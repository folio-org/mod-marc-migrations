package org.folio.support;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class DatabaseHelper {
  public static final String OPERATION_TABLE = "operation";
  public static final String CHUNKS_TABLE = "operation_chunk";
  public static final String CHUNK_STEPS_TABLE = "operation_chunk_step";
  private static final BeanPropertyRowMapper<Operation> OPERATION_MAPPER =
      new BeanPropertyRowMapper<>(Operation.class);
  private static final BeanPropertyRowMapper<OperationChunk> CHUNKS_MAPPER =
      new BeanPropertyRowMapper<>(OperationChunk.class);
  private static final BeanPropertyRowMapper<ChunkStep> CHUNK_STEPS_MAPPER =
      new BeanPropertyRowMapper<>(ChunkStep.class);

  private final FolioModuleMetadata metadata;
  private final JdbcTemplate jdbcTemplate;

  public String getDbPath(String tenantId, String basePath) {
    return metadata.getDBSchemaName(tenantId) + "." + basePath;
  }

  public Operation getOperation(String tenant, UUID operationId) {
    var sql = "SELECT * from " + getDbPath(tenant, OPERATION_TABLE) + " where id = '" + operationId + "'";
    return jdbcTemplate.queryForObject(sql, OPERATION_MAPPER);
  }

  public List<OperationChunk> getOperationChunks(String tenant, UUID operationId) {
    var sql = "SELECT * from " + getDbPath(tenant, CHUNKS_TABLE) + " where operation_id = '" + operationId + "'";
    return jdbcTemplate.query(sql, CHUNKS_MAPPER);
  }

  public List<ChunkStep> getChunksSteps(String tenant, UUID operationId) {
    var sql = "SELECT * from " + getDbPath(tenant, CHUNK_STEPS_TABLE) + " where operation_id = '" + operationId + "'";
    return jdbcTemplate.query(sql, CHUNK_STEPS_MAPPER);
  }
}
