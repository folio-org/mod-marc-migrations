package org.folio.marc.migrations.services.jdbc;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.spring.FolioExecutionContext;
import org.hibernate.type.SqlTypes;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ChunkJdbcService extends JdbcService {

  private static final String GET_CHUNKS = """
    SELECT *
    FROM %s.operation_chunk
    WHERE operation_id = '%s'
      %s
    ORDER BY id
    LIMIT %s;
    """;

  private static final String GET_CHUNKS_BY_IDS = """
      SELECT *
      FROM %s.operation_chunk
      WHERE id IN (%s)
      """;

  private static final String UPDATE_CHUNK = """
    UPDATE %s.operation_chunk
    SET status = '%s'
    WHERE id = '%s';
    """;

  private static final String UPDATE_CHUNKS = """
      UPDATE %s.operation_chunk
      SET status = ?::operationstatus
      WHERE id = ?
      """;

  private static final String CREATE_CHUNK = """
    INSERT INTO %s.operation_chunk
    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

  private final BeanPropertyRowMapper<OperationChunk> mapper;

  public ChunkJdbcService(FolioExecutionContext context, BeanPropertyRowMapper<OperationChunk> mapper,
                          JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, context);
    this.mapper = mapper;
  }

  public List<OperationChunk> getChunks(String operationId, UUID idFrom, int count) {
    log.debug("getChunks:: for operationId {}, id to seek from {}, count {}", operationId, idFrom, count);
    var idFilter = idFrom == null ? "" : "AND id > '%s'".formatted(idFrom);
    var sql = GET_CHUNKS.formatted(getSchemaName(), operationId, idFilter, count);
    return jdbcTemplate.query(sql, mapper);
  }

  public List<OperationChunk> getChunks(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      log.debug("getChunks:: no ids provided");
      return List.of();
    }
    log.debug("getChunks:: fetching chunks for ids {}", ids);
    var placeholders = String.join(",", ids.stream()
        .map(id -> "?")
        .toList());
    var sql = GET_CHUNKS_BY_IDS.formatted(getSchemaName(), placeholders);
    return jdbcTemplate.query(sql, mapper, ids.toArray());
  }

  public void createChunks(List<OperationChunk> chunks) {
    if (chunks.isEmpty()) {
      log.debug("createChunks:: no chunks to create");
      return;
    }
    log.debug("createChunks::operationId {}, count {}", chunks.get(0).getOperationId(), chunks.size());

    var sql = CREATE_CHUNK.formatted(getSchemaName());
    try {
      jdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (PreparedStatement ps, OperationChunk chunk) -> {
        ps.setObject(1, chunk.getId());
        ps.setObject(2, chunk.getOperationId());
        ps.setObject(3, chunk.getStartRecordId());
        ps.setObject(4, chunk.getEndRecordId());
        ps.setString(5, chunk.getSourceChunkFileName());
        ps.setString(6, chunk.getMarcChunkFileName());
        ps.setString(7, chunk.getEntityChunkFileName());
        ps.setObject(8, chunk.getStatus(), SqlTypes.OTHER);
        ps.setInt(9, chunk.getNumOfRecords());
      });
    } catch (Exception ex) {
      log.warn("createChunks:: unable to batch insert chunks for operation {}: {}",
        chunks.get(0).getOperationId(), ex.getMessage());
      throw new IllegalStateException(ex);
    }
  }

  public void updateChunk(UUID id, OperationStatusType status) {
    log.debug("updateChunk::For id {}: status {}", id, status);

    var sql = UPDATE_CHUNK.formatted(getSchemaName(), status, id);
    jdbcTemplate.update(sql);
  }

  public void updateChunkStatus(List<UUID> ids, OperationStatusType status) {
    if (ids == null || ids.isEmpty()) {
      log.debug("updateChunkStatus:: no ids provided");
      return;
    }
    log.debug("updateChunkStatus:: updating status to {} for ids {}", status, ids);

    var sql = UPDATE_CHUNKS.formatted(getSchemaName());
    try {
      jdbcTemplate.batchUpdate(sql, ids, ids.size(), (PreparedStatement ps, UUID id) -> {
        ps.setString(1, status.name());
        ps.setObject(2, id);
      });
    } catch (Exception ex) {
      log.warn("updateChunkStatus:: failed to update status to {} for ids {}: {}", status, ids, ex.getMessage());
      throw new IllegalStateException(ex);
    }
  }
}
