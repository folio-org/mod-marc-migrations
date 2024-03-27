package org.folio.marc.migrations.config;

import org.folio.marc.migrations.services.FolioExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

  /**
   * Async executor for Completable futures.
   * Values set prevent parallel execution of multiple migrations in parallel because parallel execution get contexts
   * mixed.
   * */
  @Bean("remappingExecutor")
  public FolioExecutor remappingExecutor() {
    return new FolioExecutor(0, 1);
  }
}
