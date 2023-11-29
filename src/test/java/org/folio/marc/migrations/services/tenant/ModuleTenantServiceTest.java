package org.folio.marc.migrations.services.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.test.type.UnitTest;
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

  private @InjectMocks ModuleTenantService moduleTenantService;

  @Test
  void testModuleInstall() {
    assertDoesNotThrow(() -> moduleTenantService.createOrUpdateTenant(new TenantAttributes().moduleTo("mod-1.0.0")));

    verify(systemUserService).setupSystemUser();
  }

}
