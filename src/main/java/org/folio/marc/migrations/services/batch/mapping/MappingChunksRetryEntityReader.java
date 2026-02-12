package org.folio.marc.migrations.services.batch.mapping;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.springframework.batch.infrastructure.item.ItemReader;

@Log4j2
@RequiredArgsConstructor
public class MappingChunksRetryEntityReader implements ItemReader<OperationChunk> {

  private final ChunkJdbcService jdbcService;
  private final List<UUID> chunkIds;

  private List<OperationChunk> operationChunks;
  private int currentChunkOffset = 0;

  @Override
  public OperationChunk read() {
    log.trace("read:: for chunkIds size {}.", chunkIds.size());
    if (operationChunks == null) {
      log.debug("read:: fetching all OperationChunks for the provided chunk IDs.");
      operationChunks = jdbcService.getChunks(chunkIds);
      log.debug("read:: fetched {} OperationChunks.", operationChunks.size());
    }

    if (currentChunkOffset >= operationChunks.size()) {
      log.debug("read:: no more OperationChunks to process.");
      return null;
    }

    var operationChunk = operationChunks.get(currentChunkOffset++);
    log.trace("read:: returning OperationChunk with id: {}", operationChunk.getId());
    return operationChunk;
  }
}
