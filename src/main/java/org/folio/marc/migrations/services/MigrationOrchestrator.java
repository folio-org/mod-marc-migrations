package org.folio.marc.migrations.services;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
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

  private final ExecutorService remappingExecutor;
  private final ChunkService chunkService;
  private final OperationJdbcService jdbcService;
  private final JobLauncher jobLauncher;
  private final Job remappingJob;
  private final Job remappingSaveJob;

  public MigrationOrchestrator(ChunkService chunkService,
                               OperationJdbcService jdbcService,
                               JobLauncher jobLauncher,
                               @Qualifier("remappingJob") Job remappingJob,
                               @Qualifier("remappingSaveJob") Job remappingSaveJob,
                               FolioExecutor remappingExecutor) {
    this.chunkService = chunkService;
    this.jdbcService = jdbcService;
    this.jobLauncher = jobLauncher;
    this.remappingJob = remappingJob;
    this.remappingSaveJob = remappingSaveJob;
    this.remappingExecutor = remappingExecutor;
  }

  /**
   * Submits asynchronous remapping task, mapping part.
   *
   * @param operation represents migration operation to run mapping for.
   * */
  public CompletableFuture<Void> submitMappingTask(Operation operation) {
    var operationId = operation.getId().toString();
    log.info("submitMappingTask:: starting for operation {}", operation.getId());
    var future = CompletableFuture.runAsync(() -> updateOperationStatus(operationId, OperationStatusType.DATA_MAPPING,
        OperationTimeType.MAPPING_START), remappingExecutor)
      .thenRun(() -> chunkService.prepareChunks(operation))
      .thenRun(submitProcessChunksTask(operationId))
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operationId, OperationStatusType.DATA_MAPPING_FAILED, OperationTimeType.MAPPING_END);
        }
        return unused;
      });
    log.info("submitMappingTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  /**
   * Submits asynchronous remapping task, saving part.
   *
   * @param operation represents migration operation to run data saving for mapped operation chunks
   * */
  public CompletableFuture<Void> submitMappingSaveTask(Operation operation) {
    var operationId = operation.getId().toString();
    log.info("submitSavingTask:: starting for operation {}", operationId);
    updateOperationStatus(operationId, OperationStatusType.DATA_SAVING, OperationTimeType.SAVING_START);
    var future = CompletableFuture.runAsync(submitProcessChunksTask(operationId), remappingExecutor)
        .handle((unused, throwable) -> {
          if (throwable != null) {
            updateOperationStatus(operationId, OperationStatusType.DATA_SAVING_FAILED, OperationTimeType.SAVING_END);
          }
          return unused;
        });
    log.info("submitSavingTask:: submitted asynchronous execution for operation {}", operationId);
    return future;
  }

  private void updateOperationStatus(String operationId, OperationStatusType status,
                                     OperationTimeType timeType) {
    jdbcService.updateOperationStatus(operationId, status, timeType, Timestamp.from(Instant.now()));
  }

  private Runnable submitProcessChunksTask(String operationId) {
    return () -> {
      try {
        var jobParameters = new JobParameters(
            Map.of(OPERATION_ID, new JobParameter<>(operationId, String.class)));
        var currentStatus = jdbcService.getOperation(operationId).getStatus();
        if (currentStatus == OperationStatusType.DATA_MAPPING) {
          jobLauncher.run(remappingJob, jobParameters);
        } else if (currentStatus == OperationStatusType.DATA_SAVING) {
          jobLauncher.run(remappingSaveJob, jobParameters);
        }
      } catch (Exception ex) {
        log.warn("Error running job for operation {}: {} - {}", operationId, ex.getCause(), ex.getMessage());
        throw new IllegalStateException(ex);
      }
    };
  }
}
