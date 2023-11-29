package org.folio.marc.migrations.services.tenant;

import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ModuleTenantService extends TenantService {

  private final FolioExecutionContext context;
  private final PrepareSystemUserService systemUserService;

  public ModuleTenantService(JdbcTemplate jdbcTemplate,
                             FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase,
                             PrepareSystemUserService systemUserService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.context = context;
    this.systemUserService = systemUserService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    log.info("afterTenantUpdate::Initiating additional setup [tenant: {}]", context.getTenantId());
    super.afterTenantUpdate(tenantAttributes);
    systemUserService.setupSystemUser();
    log.info("afterTenantUpdate::Completed additional setup [tenant: {}]", context.getTenantId());
  }
}
