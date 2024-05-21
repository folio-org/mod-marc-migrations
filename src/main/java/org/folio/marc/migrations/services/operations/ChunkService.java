package org.folio.marc.migrations.services.operations;

import static org.folio.marc.migrations.services.batch.support.JobConstants.OPERATION_FILES_PATH;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ChunkService {

  private static final String CHUNK_FILE_NAME = OPERATION_FILES_PATH + "%s_%s";

  private final MigrationProperties props;
  private final AuthorityJdbcService authorityJdbcService;
  private final ChunkJdbcService chunkJdbcService;

  public void prepareChunks(Operation operation) {
    log.info("prepareChunks:: starting for operation {}", operation.getId());
    var chunks = new LinkedList<OperationChunk>();

    var recordIds = authorityJdbcService.getAuthorityIdsChunk(props.getChunkFetchIdsCount() + 1);
    addChunksForRecordIds(operation, chunks, recordIds.get(0), recordIds.get(recordIds.size() - 1),
        props.getChunkFetchIdsCount());
    var idFrom = recordIds.get(recordIds.size() - 1);

    while (idFrom != null) {
      recordIds = authorityJdbcService.getAuthorityIdsChunk(idFrom, props.getChunkFetchIdsCount(), 1);
      if (CollectionUtils.isNotEmpty(recordIds)) {
        addChunksForRecordIds(operation, chunks, idFrom, recordIds.get(0), props.getChunkFetchIdsCount());
        idFrom = recordIds.get(0);
      } else {
        recordIds = authorityJdbcService.getAuthorityIdsChunk(idFrom, 0, props.getChunkFetchIdsCount());
        var numOfRecords = CollectionUtils.isNotEmpty(recordIds) ? recordIds.size() : 1;
        addChunksForRecordIds(operation, chunks, idFrom, null, numOfRecords);
        idFrom = null;
      }

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

  private void addChunksForRecordIds(Operation operation,
                                     List<OperationChunk> chunks,
                                     UUID startRecordId,
                                     UUID endRecordId,
                                     int numOfRecords) {
    var operationId = operation.getId();
    var chunkId = UUID.randomUUID();
    var chunk = OperationChunk.builder()
      .id(chunkId)
      .operationId(operationId)
      .startRecordId(startRecordId)
      .endRecordId(endRecordId)
      .sourceChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "source"))
      .marcChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "marc"))
      .entityChunkFileName(CHUNK_FILE_NAME.formatted(operationId, chunkId, "entity"))
      .status(OperationStatusType.NEW)
      .numOfRecords(numOfRecords)
      .build();
    chunks.add(chunk);
  }
}
