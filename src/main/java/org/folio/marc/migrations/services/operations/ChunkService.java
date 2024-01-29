package org.folio.marc.migrations.services.operations;

import com.google.common.collect.Lists;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationChunkRepository;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ChunkService {

  private static final String CHUNK_FILE_NAME = "operation/%s/%s_%s";
  private static final String FIST_IDS_CHUNK_QUERY = """
    SELECT id FROM %s_%s
    ORDER BY id LIMIT %s;
    """;
  private static final String IDS_CHUNK_QUERY = """
    SELECT id FROM %s_%s
    WHERE id > '%s'
    ORDER BY id LIMIT %s;
    """;
  private static final Map<EntityType, String> TABLE_BY_ENTITY =
      Map.of(EntityType.AUTHORITY, "mod_entities_links.authority");

  private final MigrationProperties props;
  private final FolioExecutionContext context;
  private final JdbcTemplate jdbcTemplate;
  private final OperationChunkRepository repository;

  public void prepareChunks(Operation operation) {
    var tableName = Optional.ofNullable(TABLE_BY_ENTITY.get(operation.getEntityType())).orElseThrow(() ->
        new UnsupportedOperationException("prepareChunks:: Unsupported entity type: " + operation.getEntityType()));
    var tenantId = context.getTenantId();
    var chunks = new LinkedList<OperationChunk>();

    var recordIdsFirstSql = FIST_IDS_CHUNK_QUERY.formatted(tenantId, tableName, props.getChunkFetchIdsCount());
    var recordIds = jdbcTemplate.queryForList(recordIdsFirstSql, UUID.class);
    addChunksForRecordIds(operation, chunks, recordIds);
    var idFrom = Optional.ofNullable(chunks.peekLast()).map(OperationChunk::getEndRecordId).orElse(null);

    while (recordIds.size() == props.getChunkFetchIdsCount()) {
      var recordIdsSql = IDS_CHUNK_QUERY.formatted(tenantId, tableName, idFrom, props.getChunkFetchIdsCount());
      recordIds = jdbcTemplate.queryForList(recordIdsSql, UUID.class);
      addChunksForRecordIds(operation, chunks, recordIds);

      idFrom = Optional.ofNullable(chunks.peekLast()).map(OperationChunk::getEndRecordId).orElse(null);
      if (chunks.size() >= props.getChunkPersistCount()) {
        repository.saveAll(chunks);
        chunks.clear();
      }
    }

    if (!chunks.isEmpty()) {
      repository.saveAll(chunks);
    }
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
          .operation(operation)
          .startRecordId(chunkRecordIds.get(0))
          .endRecordId(chunkRecordIds.get(chunkRecordIds.size() - 1))
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
