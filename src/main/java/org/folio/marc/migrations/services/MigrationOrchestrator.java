package org.folio.marc.migrations.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class MigrationOrchestrator {

  private final ExecutorService chunksPreparationExecutor;
  private final ExecutorService chunksProcessingExecutor;
  private final ChunkService chunkService;
  private final JdbcService jdbcService;

  public MigrationOrchestrator(MigrationProperties migrationProperties, ChunkService chunkService,
                               JdbcService jdbcService) {
    this.chunkService = chunkService;
    this.jdbcService = jdbcService;
    chunksPreparationExecutor = Executors.newSingleThreadExecutor();
    chunksProcessingExecutor = new ThreadPoolExecutor(0, migrationProperties.getChunkProcessingMaxParallelism(),
        60L, TimeUnit.SECONDS, new SynchronousQueue<>());
  }

  public CompletableFuture<Void> submitMappingTask(Operation operation) {
    log.info("submitMappingTask:: starting for operation {}", operation.getId());
    return submitPrepareChunksTask(operation)
        .thenCompose(unused -> submitProcessChunksTask(operation))
        .thenAccept(unused -> log.info("submitMappingTask:: finished for operation {}", operation.getId()));
  }

  private CompletableFuture<Void> submitPrepareChunksTask(Operation operation) {
    log.info("submitPrepareChunksTask:: starting for operation {}", operation.getId());
    return CompletableFuture.runAsync(() -> chunkService.prepareChunks(operation), chunksPreparationExecutor)
        .handle((unused, throwable) -> {
          if (throwable != null) {
            log.warn("submitPrepareChunksTask:: failed for operation {}. Error: {}",
                operation.getId(), throwable.getMessage());
            return OperationStatusType.DATA_MAPPING_FAILED;
          } else {
            log.info("submitPrepareChunksTask:: finished for operation {}", operation.getId());
            return OperationStatusType.DATA_MAPPING;
          }
        })
        .thenAccept(status -> jdbcService.updateOperationStatus(operation.getId(), status));
  }

  private CompletableFuture<Void> submitProcessChunksTask(Operation operation) {
    //maybe submit a spring batch job
    //use 'chunksProcessingExecutor' (or delete if not needed)
    return CompletableFuture.completedFuture(null);
  }
}
