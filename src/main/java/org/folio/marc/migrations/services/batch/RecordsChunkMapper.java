package org.folio.marc.migrations.services.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.Authority;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.services.batch.support.MappingMetadataProvider;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.processing.mapping.defaultmapper.MarcToAuthorityMapper;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class RecordsChunkMapper
  implements ItemProcessor<MappingComposite<MarcRecord>, MappingComposite<MappingResult>> {

  private static final String NO_MARC_RECORD_JSON =
    "{\"marcId\": \"%s\", \"recordId\": \"%s\", \"state\": \"%s\", \"version\": %s}";

  private final ObjectMapper objectMapper;
  private final MappingMetadataProvider mappingMetadataProvider;
  private final OperationJdbcService jdbcService;

  @Override
  public MappingComposite<MappingResult> process(MappingComposite<MarcRecord> composite) {
    log.debug("process:: for operation {}, chunk {}, step {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId(), composite.mappingData().stepId());
    log.trace("process:: retrieving mapping metadata from cache");
    var mappingData = mappingMetadataProvider.getMappingData();
    var mappingResults = composite.records().stream()
      .map(sourceData -> {
        try {
          if (mappingData == null) {
            return new MappingResult(null, marcToString(sourceData), "Failed to fetch mapping metadata");
          }

          var marcSource = new JsonObject(sourceData.marc().toString());
          var authority = new MarcToAuthorityMapper().mapRecord(
            marcSource, mappingData.mappingParameters(), mappingData.mappingRules());
          authority.setId(sourceData.recordId().toString());
          authority.setVersion(sourceData.version());
          authority.setSource(Authority.Source.MARC);

          var authorityString = objectMapper.writeValueAsString(authority);
          return new MappingResult(authorityString, null, null);
        } catch (Exception ex) {
          log.warn("Error while processing data for marcId {}, recordId {}: {}",
            sourceData.marcId(), sourceData.recordId(), ex.getMessage());
          return new MappingResult(null, marcToString(sourceData), ex.getMessage());
        }
      })
      .toList();

    var mappedRecordsCount = (int) mappingResults.stream()
      .map(MappingResult::mappedRecord)
      .filter(Objects::nonNull)
      .count();
    if (mappedRecordsCount != 0) {
      jdbcService.addProcessedOperationRecords(composite.mappingData().operationId(), mappedRecordsCount, 0);
    }

    return new MappingComposite<>(composite.mappingData(), mappingResults);
  }

  private String marcToString(MarcRecord marc) {
    try {
      return objectMapper.writeValueAsString(marc);
    } catch (JsonProcessingException e) {
      log.warn(
        "Unable to convert invalid marc record to string. marcId {}, recordId {}, state {}, version {}",
        marc.marcId(), marc.recordId(), marc.state(), marc.version());
      return NO_MARC_RECORD_JSON.formatted(marc.marcId(), marc.recordId(), marc.state(), marc.version());
    }
  }
}
