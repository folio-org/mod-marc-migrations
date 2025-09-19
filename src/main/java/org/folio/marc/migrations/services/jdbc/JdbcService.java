package org.folio.marc.migrations.services.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

@Log4j2
@RequiredArgsConstructor
public abstract class JdbcService {

  protected final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  protected String getSchemaName() {
    var schemaName = context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
    log.trace("getSchemaName:: tenantId {}, schema name {}", context.getTenantId(), schemaName);
    return schemaName;
  }

  protected String getSchemaName(String tenantId) {
    var schemaName = context.getFolioModuleMetadata().getDBSchemaName(tenantId);
    log.trace("getSchemaName:: tenantId {}, schema name {}", tenantId, schemaName);
    return schemaName;
  }

  protected void createView(String tenantId, String query) {
    log.info("createView:: Attempting to create view [tenant: {}, query: {}]", tenantId, query);
    jdbcTemplate.execute(query);
    log.info("createView:: Successfully created view [tenant: {}, query: {}]", tenantId, query);
  }
}
