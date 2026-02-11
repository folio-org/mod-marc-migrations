package org.folio.marc.migrations.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("mapping-metadata")
public interface MappingMetadataClient {

  @GetExchange(value = "/type/{recordType}", accept = APPLICATION_JSON_VALUE)
  MappingMetadata getMappingMetadata(@PathVariable String recordType);

  record MappingMetadata(String mappingRules, String mappingParams) { }
}
