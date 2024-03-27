package org.folio.marc.migrations.services.jdbc;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.lenient;

import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class JdbcServiceTestBase {

  protected @Mock JdbcTemplate jdbcTemplate;
  protected @Mock FolioModuleMetadata metadata;
  protected @Mock FolioExecutionContext context;

  @BeforeEach
  void setUp() {
    lenient().when(context.getFolioModuleMetadata()).thenReturn(metadata);
    lenient().when(context.getTenantId()).thenReturn(TENANT_ID);
    lenient().when(metadata.getDBSchemaName(TENANT_ID)).thenReturn(TENANT_ID);
  }
}
