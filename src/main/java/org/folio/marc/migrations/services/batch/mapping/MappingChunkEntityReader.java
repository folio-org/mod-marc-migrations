package org.folio.marc.migrations.services.batch.mapping;

import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.springframework.batch.infrastructure.item.ItemReader;

@Log4j2
public class MappingChunkEntityReader implements ItemReader<OperationChunk> {

  private final String operationId;
  private final UUID idTo;
  private final MigrationProperties props;
  private final ChunkJdbcService jdbcService;

  private UUID idFrom;
  private int currentBatchOffset;
  private List<OperationChunk> currentBatch;

  public MappingChunkEntityReader(String operationId, MigrationProperties props, ChunkJdbcService jdbcService) {
    this(operationId, null, null, props, jdbcService);
  }

  /**
   * Reads only the chunks in a partition's id window {@code (idFrom, idTo]}, keyset-paged by
   * {@code props.chunkPersistCount}.
   *
   * @param idFrom exclusive lower bound seeding the cursor; {@code null} for the first partition (no lower bound)
   * @param idTo   inclusive upper bound; {@code null} means read to the end
   */
  public MappingChunkEntityReader(String operationId, UUID idFrom, UUID idTo, MigrationProperties props,
                                  ChunkJdbcService jdbcService) {
    this.operationId = operationId;
    this.idFrom = idFrom;
    this.idTo = idTo;
    this.props = props;
    this.jdbcService = jdbcService;
  }

  @Override
  public OperationChunk read() {
    log.trace("read:: for operation {}.", operationId);
    if (currentBatch == null || currentBatchOffset >= currentBatch.size()) {
      log.debug("read:: no preloaded chunk entities for operation {}, attempting preload.", operationId);
      if (idFrom == null && currentBatch != null) {
        log.info("read:: no more chunk entities to load for operation {}.", operationId);
        return null;
      }

      currentBatch = jdbcService.getChunks(operationId, idFrom, idTo, props.getChunkPersistCount());
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

    return currentBatch.getLast().getId();
  }
}
