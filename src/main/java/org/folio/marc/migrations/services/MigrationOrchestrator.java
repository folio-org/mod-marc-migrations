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
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class MigrationOrchestrator {

  private final ExecutorService remappingExecutor;
  private final ChunkService chunkService;
  private final OperationJdbcService jdbcService;
  private final JobLauncher jobLauncher;
  private final Job job;

  public MigrationOrchestrator(ChunkService chunkService, OperationJdbcService jdbcService, JobLauncher jobLauncher,
                               Job job, FolioExecutor remappingExecutor) {
    this.chunkService = chunkService;
    this.jdbcService = jdbcService;
    this.jobLauncher = jobLauncher;
    this.job = job;
    this.remappingExecutor = remappingExecutor;
  }

  /**
   * Submits asynchronous remapping task, mapping part.
   *
   * @param operation represents migration operation to run mapping for.
   * */
  public CompletableFuture<Void> submitMappingTask(Operation operation) {
    log.info("submitMappingTask:: starting for operation {}", operation.getId());
    var future = CompletableFuture.runAsync(() -> updateOperationStatus(operation, OperationStatusType.DATA_MAPPING,
        OperationTimeType.MAPPING_START), remappingExecutor)
      .thenRun(() -> chunkService.prepareChunks(operation))
      .thenRun(submitProcessChunksTask(operation))
      .handle((unused, throwable) -> {
        if (throwable != null) {
          updateOperationStatus(operation, OperationStatusType.DATA_MAPPING_FAILED, OperationTimeType.MAPPING_END);
        }
        return unused;
      });
    log.info("submitMappingTask:: submitted asynchronous execution for operation {}", operation.getId());
    return future;
  }

  private void updateOperationStatus(Operation operation, OperationStatusType status,
                                     OperationTimeType timeType) {
    jdbcService.updateOperationStatus(operation.getId().toString(), status, timeType, Timestamp.from(Instant.now()));
  }

  private Runnable submitProcessChunksTask(Operation operation) {
    return () -> {
      try {
        jobLauncher.run(job, new JobParameters(
          Map.of(OPERATION_ID, new JobParameter<>(operation.getId().toString(), String.class))));
      } catch (Exception ex) {
        log.warn("Error running job for operation {}: {} - {}",
          operation.getId(), ex.getCause(), ex.getMessage());
        throw new IllegalStateException(ex);
      }
    };
  }
}
