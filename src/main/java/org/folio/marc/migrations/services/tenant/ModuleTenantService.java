package org.folio.marc.migrations.services.tenant;

import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Primary
public class ModuleTenantService extends TenantService {

  private final PrepareSystemUserService systemUserService;
  private final AuthorityJdbcService authorityJdbcService;
  private final InstanceJdbcService instanceJdbcService;

  public ModuleTenantService(JdbcTemplate jdbcTemplate,
                             FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase,
                             PrepareSystemUserService systemUserService, AuthorityJdbcService authorityJdbcService, InstanceJdbcService instanceJdbcService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.systemUserService = systemUserService;
    this.authorityJdbcService = authorityJdbcService;
    this.instanceJdbcService = instanceJdbcService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    log.info("afterTenantUpdate::Initiating additional setup [tenant: {}]", context.getTenantId());
    super.afterTenantUpdate(tenantAttributes);
    systemUserService.setupSystemUser();
    authorityJdbcService.initViews(context.getTenantId());
    instanceJdbcService.initViews(context.getTenantId());
    log.info("afterTenantUpdate::Completed additional setup [tenant: {}]", context.getTenantId());
  }
}
