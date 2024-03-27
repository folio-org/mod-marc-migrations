package org.folio.marc.migrations.services.batch;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.springframework.batch.item.ItemReader;

@Log4j2
@RequiredArgsConstructor
public class ChunkEntityReader implements ItemReader<OperationChunk> {

  private final String operationId;
  private final MigrationProperties props;
  private final ChunkJdbcService jdbcService;

  private UUID idFrom;
  private int currentBatchOffset;
  private List<OperationChunk> currentBatch;

  @Override
  public OperationChunk read() {
    log.trace("read:: for operation {}.", operationId);
    if (currentBatch == null || currentBatchOffset >= currentBatch.size()) {
      log.debug("read:: no preloaded chunk entities for operation {}, attempting preload.", operationId);
      if (idFrom == null && currentBatch != null) {
        log.info("read:: no more chunk entities to load for operation {}.", operationId);
        return null;
      }

      currentBatch = jdbcService.getChunks(operationId, idFrom, props.getChunkPersistCount());
      log.debug("read:: retrieved {} chunk entities for operation {}", currentBatch.size(), operationId);
      idFrom = getNextIdFrom();
      log.debug("read:: next chunk entity id to seek from {}", idFrom);
      currentBatchOffset = 0;
    }

    if (currentBatch.isEmpty()) {
      log.debug("read:: no more chunk entities to load for operation {}.", operationId);
      return null;
    }

    log.trace("read:: returning preloaded chunk entity for operation {}.", operationId);
    return currentBatch.get(currentBatchOffset++);
  }

  private UUID getNextIdFrom() {
    if (CollectionUtils.isEmpty(currentBatch) || currentBatch.size() < props.getChunkPersistCount()) {
      return null;
    }

    return currentBatch.get(currentBatch.size() - 1).getId();
  }
}
