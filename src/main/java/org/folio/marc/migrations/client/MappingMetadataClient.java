package org.folio.marc.migrations.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("mapping-metadata")
public interface MappingMetadataClient {

  @GetMapping(value = "/type/marc-authority", produces = APPLICATION_JSON_VALUE)
  MappingMetadata getMappingMetadata();

  record MappingMetadata(String mappingRules, String mappingParams) { }
}
