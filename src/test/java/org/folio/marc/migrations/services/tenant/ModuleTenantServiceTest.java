package org.folio.marc.migrations.services.tenant;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.marc.migrations.services.JdbcService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.testing.type.UnitTest;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleTenantServiceTest {

  private @Mock PrepareSystemUserService systemUserService;
  private @Mock FolioExecutionContext context;
  private @Mock JdbcService jdbcService;

  private @InjectMocks ModuleTenantService moduleTenantService;

  @Test
  void testModuleInstall() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    assertDoesNotThrow(() -> moduleTenantService.createOrUpdateTenant(new TenantAttributes().moduleTo("mod-1.0.0")));

    verify(systemUserService).setupSystemUser();
    verify(jdbcService).initViews();
  }

}
