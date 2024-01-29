package org.folio.support;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class DatabaseHelper {
  public static final String CHUNKS_TABLE = "operation_chunk";
  private static final BeanPropertyRowMapper<OperationChunk> CHUNKS_MAPPER =
      new BeanPropertyRowMapper<>(OperationChunk.class);

  private final FolioModuleMetadata metadata;
  private final JdbcTemplate jdbcTemplate;

  public String getDbPath(String tenantId, String basePath) {
    return metadata.getDBSchemaName(tenantId) + "." + basePath;
  }

  public List<OperationChunk> getOperationChunks(String tenant, UUID operationId) {
    var sql = "SELECT * from " + getDbPath(tenant, CHUNKS_TABLE) + " where operation_id = '" + operationId + "'";
    return jdbcTemplate.query(sql, CHUNKS_MAPPER);
  }
}
