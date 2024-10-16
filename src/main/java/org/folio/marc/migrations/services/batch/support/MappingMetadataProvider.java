package org.folio.marc.migrations.services.batch.support;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.client.MappingMetadataClient;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.processing.mapping.defaultmapper.processor.parameters.MappingParameters;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class MappingMetadataProvider {

  private final MappingMetadataClient client;

  @Cacheable(cacheNames = "mapping-metadata-cache",
    key = "@folioExecutionContext.tenantId + #entityType",
    unless = "#result == null")
  public MappingData getMappingData(EntityType entityType) {
    log.trace("getMappingData:: fetching mapping metadata");
    try {
      var metadata = client.getMappingMetadata(entityType.getMappingMetadataRecordType());
      if (metadata == null || isBlank(metadata.mappingParams()) || isBlank(metadata.mappingRules())) {
        log.warn("Failed to fetch mapping metadata");
        return null;
      }

      return new MappingData(new JsonObject(metadata.mappingRules()),
        new JsonObject(metadata.mappingParams()).mapTo(MappingParameters.class));
    } catch (Exception ex) {
      log.warn("Failed to fetch mapping metadata, reason: {}", ex.getMessage());
      return null;
    }
  }

  public record MappingData(JsonObject mappingRules, MappingParameters mappingParameters) {}
}
