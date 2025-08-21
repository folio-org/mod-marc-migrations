package org.folio.marc.migrations.services;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.stereotype.Component;

@Component
public class TenantContextRunner {

  private final FolioModuleMetadata moduleMetadata;

  public TenantContextRunner(FolioModuleMetadata moduleMetadata) {
    this.moduleMetadata = moduleMetadata;
  }

  public void runInContext(String tenantId, Runnable runnable) {
    try (var fec = new FolioExecutionContextSetter(createContext(tenantId))) {
      runnable.run();
    }
  }

  private FolioExecutionContext createContext(String tenantId) {
    Map<String, Collection<String>> headers = Map.of(XOkapiHeaders.TENANT, Collections.singletonList(tenantId));
    return new DefaultFolioExecutionContext(moduleMetadata, headers);
  }
}
