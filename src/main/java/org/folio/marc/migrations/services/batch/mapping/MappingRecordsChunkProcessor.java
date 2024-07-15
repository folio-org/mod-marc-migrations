package org.folio.marc.migrations.services.batch.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.Authority;
import org.folio.Instance;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.batch.support.MappingMetadataProvider;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.processing.mapping.defaultmapper.MarcToAuthorityMapper;
import org.folio.processing.mapping.defaultmapper.MarcToInstanceMapper;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component("mappingRecordsStepProcessor")
@StepScope
@RequiredArgsConstructor
public class MappingRecordsChunkProcessor
  implements ItemProcessor<MappingComposite<MarcRecord>, MappingComposite<MappingResult>> {

  private static final String NO_MARC_RECORD_JSON =
    "{\"marcId\": \"%s\", \"recordId\": \"%s\", \"state\": \"%s\", \"version\": %s}";

  private final ObjectMapper objectMapper;
  private final MappingMetadataProvider mappingMetadataProvider;
  private final OperationJdbcService jdbcService;
  private final ChunkJdbcService chunkJdbcService;
  private final ChunkStepJdbcService chunkStepJdbcService;

  @Override
  public MappingComposite<MappingResult> process(MappingComposite<MarcRecord> composite) {
    var mappingData = composite.mappingData();
    log.debug("process:: for operation {}, chunk {}, step {}",
        mappingData.operationId(), mappingData.chunkId(), mappingData.stepId());

    var operationEntityType = jdbcService.getOperation(mappingData.operationId().toString()).getEntityType();
    var mappingResults = getRecordMappingResults(composite.records(), operationEntityType);
    var mappedRecordsCount = (int) mappingResults.stream()
      .map(MappingResult::mappedRecord)
      .filter(Objects::nonNull)
      .count();
    var numOfErrors = mappingResults.size() - mappedRecordsCount;
    var mappingSucceeded = numOfErrors == 0;
    if (mappedRecordsCount != 0) {
      jdbcService.addProcessedOperationRecords(mappingData.operationId(), mappedRecordsCount, 0);
    }
    updateChunkAndChunkStep(mappingData.chunkId(), mappingData.stepId(), mappingSucceeded, numOfErrors);

    return new MappingComposite<>(mappingData, mappingResults);
  }

  private List<MappingResult> getRecordMappingResults(List<MarcRecord> records, EntityType type) {
    log.trace("process:: retrieving mapping metadata from cache");
    var mappingData = mappingMetadataProvider.getMappingData();
    if (mappingData == null) {
      return records.stream()
          .map(sourceRecord -> asFailedMappingResult(sourceRecord, "Failed to fetch mapping metadata"))
          .toList();
    }

    return records.stream()
        .map(sourceData -> asRecordMappingResult(sourceData, mappingData, type))
        .toList();
  }

  private MappingResult asRecordMappingResult(MarcRecord sourceData,
                                              MappingMetadataProvider.MappingData mappingData,
                                              EntityType entityType) {
    try {
      var marcSource = new JsonObject(sourceData.marc().toString());

      Object marcRecord;
      String recordString;
      if (entityType == EntityType.AUTHORITY) {
        marcRecord = new MarcToAuthorityMapper()
            .mapRecord(marcSource, mappingData.mappingParameters(), mappingData.mappingRules());
        Authority authority = (Authority) marcRecord;
        authority.setId(sourceData.recordId().toString());
        authority.setVersion(sourceData.version());
        authority.setSource(Authority.Source.MARC);
        recordString = objectMapper.writeValueAsString(authority);
      } else {
        marcRecord = new MarcToInstanceMapper()
            .mapRecord(marcSource, mappingData.mappingParameters(), mappingData.mappingRules());
        Instance instance = (Instance) marcRecord;
        instance.setId(sourceData.recordId().toString());
        instance.setVersion(sourceData.version());
        instance.setSource("MARC");
        recordString = objectMapper.writeValueAsString(instance);
      }
      return new MappingResult(recordString);
    } catch (Exception ex) {
      log.warn("Error while processing data for marcId {}, recordId {}: {}",
          sourceData.marcId(), sourceData.recordId(), ex.getMessage());
      return asFailedMappingResult(sourceData, ex.getMessage());
    }
  }

  private MappingResult asFailedMappingResult(MarcRecord sourceData, String errorMessage) {
    return new MappingResult(marcToString(sourceData), errorMessage);
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

  private void updateChunkAndChunkStep(UUID chunkId, UUID stepId, boolean mappingSucceeded, int errorsCount) {
    var stepStatus = mappingSucceeded ? StepStatus.COMPLETED : StepStatus.FAILED;
    chunkStepJdbcService.updateChunkStep(stepId, stepStatus, Timestamp.from(Instant.now()), errorsCount);

    var chunkStatus = mappingSucceeded ? OperationStatusType.DATA_MAPPING_COMPLETED
        : OperationStatusType.DATA_MAPPING_FAILED;
    chunkJdbcService.updateChunk(chunkId, chunkStatus);
  }
}
