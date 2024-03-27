package org.folio.marc.migrations.services.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;

@Log4j2
@RequiredArgsConstructor
public abstract class JdbcService {

  private final FolioExecutionContext context;

  protected String getSchemaName() {
    var schemaName = context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
    log.trace("getSchemaName:: tenantId {}, schema name {}", context.getTenantId(), schemaName);
    return schemaName;
  }
}
