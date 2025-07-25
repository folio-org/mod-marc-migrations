package org.folio.marc.migrations.services;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.CHUNK_IDS;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.ENTITY_TYPE;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.PUBLISH_EVENTS_FLAG;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.TIMESTAMP;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.jdbc.SpringBatchExecutionParamsJdbcService;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class MigrationOrchestrator {

  private static final String ERROR_RUNNING_JOB_MESSAGE = "Error running job for operation {}: {} - {}";
  private final ExecutorService remappingExecutor;
  private final ChunkService chunkService;
  private final OperationJdbcService jdbcService;
  private final SpringBatchExecutionParamsJdbcService executionParamsJdbcService;
  private final JobLauncher jobLauncher;
  private final Job remappingJob;
  private final Job remappingSaveJob;
  private final Job remappingRetryJob;
  private final Job remappingRetrySaveJob;

  public MigrationOrchestrator(ChunkService chunkService,
                               OperationJdbcService jdbcService,
                               SpringBatchExecutionParamsJdbcService executionParamsJdbcService,
                               JobLauncher jobLauncher,
                               @Qualifier("remappingJob") Job remappingJob,
                               @Qualifier("remappingSaveJob") Job remappingSaveJob,
                               @Qualifier("remappingRetryJob") Job remappingRetryJob,
                                @Qualifier("remappingRetrySaveJob") Job remappingRetrySaveJob,
                               FolioExecutor remappingExecutor) {
    this.chunkService = chunkService;
    this.jdbcService = jdbcService;
    this.executionParamsJdbcService = executionParamsJdbcService;
    this.jobLauncher = jobLauncher;
    this.remappingJob = remappingJob;
    this.remappingSaveJob = remappingSaveJob;
    this.remappingRetryJob = remappingRetryJob;
    this.remappingRetrySaveJob = remappingRetrySaveJob;
    this.remappingExecutor = remappingExecutor;
  }

  /**
   * Submits asynchronous remapping task, mapping part.
   *
   * @param operation represents migration operation to run mapping for.
   */
  public CompletableFuture<Void> submitMappingTask(Operation operation) {
    var operationId = operation.getId().toString();
    log.info("submitMappingTask:: starting for operation {}", operation.getId());
    var future = runAsync(() -> updateOperationStatus(operationId, OperationStatusType.DATA_MAPPING,
      OperationTimeType.MAPPING_START), remappingExecutor)
      .thenRun(() -> chunkService.prepareChunks(operation))
      .thenRun(submitProcessChunksTask(operationId, operation.getEntityType(), null))
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operationId, OperationStatusType.DATA_MAPPING_FAILED, OperationTimeType.MAPPING_END);
        }
        return unused;
      });
    log.info("submitMappingTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  public CompletableFuture<Void> submitRetryMappingTask(Operation operation, List<UUID> chunkIds) {
    var operationId = operation.getId()
      .toString();
    log.info("submitRetryMappingTask:: starting retry for operation {}", operation.getId());
    var future = runAsync(() ->
        chunkService.updateChunkStatus(chunkIds, OperationStatusType.DATA_MAPPING), remappingExecutor)
      .thenRun(submitProcessRetryChunksTask(operationId, operation.getEntityType(), chunkIds))
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operationId, OperationStatusType.DATA_MAPPING_FAILED, OperationTimeType.MAPPING_END);
        }
        return unused;
      });
    log.info("submitRetryMappingTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  /**
   * Submits asynchronous remapping task, saving part.
   *
   * @param operation represents migration operation to run data saving for mapped operation chunks
   * @param request   save migration request
   */
  public CompletableFuture<Void> submitMappingSaveTask(Operation operation, SaveMigrationOperation request) {
    var operationId = operation.getId().toString();
    var entityType = operation.getEntityType();
    log.info("submitSavingTask:: starting for operation {}", operationId);
    updateOperationStatus(operationId, OperationStatusType.DATA_SAVING, OperationTimeType.SAVING_START);
    var future = runAsync(submitProcessChunksTask(operationId, entityType, request.getPublishEvents()),
      remappingExecutor)
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operationId, OperationStatusType.DATA_SAVING_FAILED, OperationTimeType.SAVING_END);
        }
        return unused;
      });
    log.info("submitSavingTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  public CompletableFuture<Void> submitMappingSaveRetryTask(Operation operation, List<UUID> chunkIds) {
    var operationId = operation.getId().toString();
    var entityType = operation.getEntityType();
    log.info("submitMappingSaveRetryTask:: starting for operation {}", operationId);
    updateOperationStatus(operationId, OperationStatusType.DATA_SAVING, OperationTimeType.SAVING_START);
    var publishEventsFlag = executionParamsJdbcService.getBatchExecutionParam(PUBLISH_EVENTS_FLAG, operationId);
    var future = runAsync(submitProcessRetrySaveChunksTask(operationId, entityType, chunkIds, publishEventsFlag),
      remappingExecutor)
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operationId, OperationStatusType.DATA_SAVING_FAILED, OperationTimeType.SAVING_END);
        }
        return unused;
      });
    log.info("submitMappingSaveRetryTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  private void updateOperationStatus(String operationId, OperationStatusType status,
                                     OperationTimeType timeType) {
    jdbcService.updateOperationStatus(operationId, status, timeType, Timestamp.from(Instant.now()));
  }

  private Runnable submitProcessChunksTask(String operationId, EntityType entityType, Boolean publishEvents) {
    return () -> {
      try {
        var parameterMap = new HashMap<String, JobParameter<?>>();
        parameterMap.put(OPERATION_ID, new JobParameter<>(operationId, String.class));
        parameterMap.put(ENTITY_TYPE, new JobParameter<>(entityType, EntityType.class));
        if (publishEvents != null) {
          parameterMap.put(PUBLISH_EVENTS_FLAG, new JobParameter<>(publishEvents, Boolean.class));
        }
        var jobParameters = new JobParameters(parameterMap);
        var currentStatus = jdbcService.getOperation(operationId).getStatus();
        if (currentStatus == OperationStatusType.DATA_MAPPING) {
          jobLauncher.run(remappingJob, jobParameters);
        } else if (currentStatus == OperationStatusType.DATA_SAVING) {
          jobLauncher.run(remappingSaveJob, jobParameters);
        }
      } catch (Exception ex) {
        log.warn(ERROR_RUNNING_JOB_MESSAGE, operationId, ex.getCause(), ex.getMessage());
        throw new IllegalStateException(ex);
      }
    };
  }

  private Runnable submitProcessRetryChunksTask(String operationId, EntityType entityType, List<UUID> chunkIds) {
    return () -> {
      try {
        var parameterMap = new HashMap<String, JobParameter<?>>();
        parameterMap.put(OPERATION_ID, new JobParameter<>(operationId, String.class));
        parameterMap.put(ENTITY_TYPE, new JobParameter<>(entityType, EntityType.class));
        parameterMap.put(CHUNK_IDS, new JobParameter<>(chunkIds, List.class));
        parameterMap.put(TIMESTAMP, new JobParameter<>(Timestamp.from(Instant.now()).toString(), String.class));
        var jobParameters = new JobParameters(parameterMap);
        jobLauncher.run(remappingRetryJob, jobParameters);
      } catch (Exception ex) {
        log.warn(ERROR_RUNNING_JOB_MESSAGE, operationId, ex.getCause(), ex.getMessage());
        throw new IllegalStateException(ex);
      }
    };
  }

  private Runnable submitProcessRetrySaveChunksTask(String operationId, EntityType entityType, List<UUID> chunkIds,
                                                String publishEvents) {
    return () -> {
      try {
        var parameterMap = new HashMap<String, JobParameter<?>>();
        parameterMap.put(OPERATION_ID, new JobParameter<>(operationId, String.class));
        parameterMap.put(ENTITY_TYPE, new JobParameter<>(entityType, EntityType.class));
        parameterMap.put(CHUNK_IDS, new JobParameter<>(chunkIds, List.class));
        parameterMap.put(TIMESTAMP, new JobParameter<>(Timestamp.from(Instant.now()).toString(), String.class));
        if (StringUtils.isNotEmpty(publishEvents)) {
          parameterMap.put(PUBLISH_EVENTS_FLAG, new JobParameter<>(Boolean.parseBoolean(publishEvents), Boolean.class));
        } else {
          parameterMap.put(PUBLISH_EVENTS_FLAG, new JobParameter<>(true, Boolean.class));
        }
        var jobParameters = new JobParameters(parameterMap);
        jobLauncher.run(remappingRetrySaveJob, jobParameters);
      } catch (Exception ex) {
        log.warn(ERROR_RUNNING_JOB_MESSAGE, operationId, ex.getCause(), ex.getMessage());
        throw new IllegalStateException(ex);
      }
    };
  }
}
