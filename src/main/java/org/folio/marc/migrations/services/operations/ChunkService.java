package org.folio.marc.migrations.services.operations;

import static org.folio.marc.migrations.services.batch.support.JobConstants.OPERATION_FILES_PATH;

import com.google.common.collect.Lists;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ChunkService {

  private static final String CHUNK_FILE_NAME = OPERATION_FILES_PATH + "%s_%s";

  private final MigrationProperties props;
  private final AuthorityJdbcService authorityJdbcService;
  private final ChunkJdbcService chunkJdbcService;
  private final InstanceJdbcService instanceJdbcService;

  public void prepareChunks(Operation operation) {
    log.info("prepareChunks:: starting for operation {}", operation.getId());
    var chunks = new LinkedList<OperationChunk>();

    var recordIds = (operation.getEntityType() == EntityType.AUTHORITY)
        ? authorityJdbcService.getAuthorityIdsChunk(props.getChunkFetchIdsCount()) :
          instanceJdbcService.getInstanceIdsChunk(props.getChunkFetchIdsCount());
    addChunksForRecordIds(operation, chunks, recordIds);
    var idFrom = Optional.ofNullable(chunks.peekLast()).map(OperationChunk::getEndRecordId).orElse(null);

    while (recordIds.size() == props.getChunkFetchIdsCount()) {
      recordIds = (operation.getEntityType() == EntityType.AUTHORITY)
          ? authorityJdbcService.getAuthorityIdsChunk(idFrom, props.getChunkFetchIdsCount()) :
            instanceJdbcService.getInstanceIdsChunk(idFrom, props.getChunkFetchIdsCount());
      addChunksForRecordIds(operation, chunks, recordIds);

      idFrom = Optional.ofNullable(chunks.peekLast()).map(OperationChunk::getEndRecordId).orElse(null);
      if (chunks.size() >= props.getChunkPersistCount()) {
        chunkJdbcService.createChunks(chunks);
        chunks.clear();
      }
    }

    if (!chunks.isEmpty()) {
      chunkJdbcService.createChunks(chunks);
    }
    log.info("prepareChunks:: finished for operation {}", operation.getId());
  }

  private void addChunksForRecordIds(Operation operation, List<OperationChunk> chunks, List<UUID> recordIds) {
    if (recordIds.isEmpty()) {
      return;
    }

    Lists.partition(recordIds, props.getChunkSize()).forEach(chunkRecordIds -> {
      var operationId = operation.getId();
      var chunkId = UUID.randomUUID();
      var chunk = OperationChunk.builder()
        .id(chunkId)
        .operationId(operationId)
        .startRecordId(chunkRecordIds.getFirst())
        .endRecordId(chunkRecordIds.getLast())
        .sourceChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "source"))
        .marcChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "marc"))
        .entityChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "entity"))
        .status(OperationStatusType.NEW)
        .numOfRecords(chunkRecordIds.size())
        .build();
      chunks.add(chunk);
    });
  }
}
