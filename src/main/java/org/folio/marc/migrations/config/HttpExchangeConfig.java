package org.folio.marc.migrations.config;

import org.folio.marc.migrations.client.BulkClient;
import org.folio.marc.migrations.client.MappingMetadataClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpExchangeConfig {

  @Bean
  public BulkClient bulkClient(HttpServiceProxyFactory factory) {
    return factory.createClient(BulkClient.class);
  }

  @Bean
  public MappingMetadataClient mappingMetadataClient(HttpServiceProxyFactory factory) {
    return factory.createClient(MappingMetadataClient.class);
  }
}
