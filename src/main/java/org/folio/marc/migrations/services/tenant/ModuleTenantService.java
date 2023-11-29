package org.folio.marc.migrations.services.tenant;

import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModuleTenantService extends TenantService {

  private final PrepareSystemUserService systemUserService;

  public ModuleTenantService(JdbcTemplate jdbcTemplate,
                             FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase,
                             PrepareSystemUserService systemUserService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.systemUserService = systemUserService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    super.afterTenantUpdate(tenantAttributes);
    systemUserService.setupSystemUser();
  }
}
