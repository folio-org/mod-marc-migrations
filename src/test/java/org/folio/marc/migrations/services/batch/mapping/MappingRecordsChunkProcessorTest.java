package org.folio.marc.migrations.services.batch.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.entities.types.EntityType.AUTHORITY;
import static org.folio.marc.migrations.domain.entities.types.EntityType.INSTANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.batch.support.MappingMetadataProvider;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.processing.mapping.defaultmapper.processor.parameters.MappingParameters;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingRecordsChunkProcessorTest {

  private @Spy ObjectMapper objectMapper;
  private @Mock MappingMetadataProvider mappingMetadataProvider;
  private @Mock OperationJdbcService jdbcService;
  private @Mock ChunkJdbcService chunkJdbcService;
  private @Mock ChunkStepJdbcService chunkStepJdbcService;
  private @InjectMocks MappingRecordsChunkProcessor mapper;

  private RecordsMappingData mappingData;

  @BeforeEach
  @SneakyThrows
  void setup() {
    lenient().when(mappingMetadataProvider.getMappingData(isA(EntityType.class)))
      .thenReturn(new MappingMetadataProvider.MappingData(new JsonObject(), new MappingParameters()));

    var chunkId = UUID.randomUUID();
    var chunkStepId = UUID.randomUUID();
    mappingData = new RecordsMappingData(UUID.randomUUID(), chunkId, chunkStepId, null, 2, null, null);
  }

  @Test
  void processAuthority_positive() {
    mapper.setEntityType(AUTHORITY);
    process_positive();
  }

  @Test
  void processInstance_positive() {
    mapper.setEntityType(INSTANCE);
    process_positive();
  }

  void process_positive() {
    var records = records();
    var composite = new MappingComposite<>(mappingData, records);

    var actual = mapper.process(composite);

    assertThat(actual).isNotNull();
    assertThat(actual.mappingData()).isEqualTo(mappingData);
    assertThat(actual.records())
      .hasSize(records.size())
      .allMatch(result -> result.invalidMarcRecord() == null
                          && result.errorCause() == null
                          && result.mappedRecord() != null
                          && result.mappedRecord().contains("\"_version\":1,\"source\":\"MARC\""));

    records.stream().map(mr -> mr.recordId().toString()).forEach(authorityId ->
      assertThat(actual.records().stream()
        .anyMatch(mappingResult -> mappingResult.mappedRecord().contains(authorityId)))
        .isTrue());
    verify(jdbcService).addProcessedOperationRecords(mappingData.operationId(), records.size(), 0);
    verify(chunkStepJdbcService)
      .updateChunkStep(eq(mappingData.stepId()), eq(StepStatus.COMPLETED), any(Timestamp.class), eq(0));
    verify(chunkJdbcService).updateChunk(mappingData.chunkId(), OperationStatusType.DATA_MAPPING_COMPLETED);
  }

  @Test
  @SneakyThrows
  void process_positive_partial() {
    var records = records();
    var composite = new MappingComposite<>(mappingData, records);
    when(objectMapper.writeValueAsString(any()))
      .thenThrow(new IllegalStateException("test exc"))
      .thenCallRealMethod();
    mapper.setEntityType(AUTHORITY);
    var actual = mapper.process(composite);

    assertMappingResult(actual, records);

    records.stream().map(mr -> mr.recordId().toString()).forEach(authorityId ->
      assertThat(actual.records().stream()
        .anyMatch(mappingResult -> Optional.ofNullable(mappingResult.mappedRecord())
          .orElse(mappingResult.invalidMarcRecord())
          .contains(authorityId)))
        .isTrue());
    verify(jdbcService).addProcessedOperationRecords(mappingData.operationId(), records.size() - 1, 0);
    verify(chunkStepJdbcService)
      .updateChunkStep(eq(mappingData.stepId()), eq(StepStatus.FAILED), any(Timestamp.class), eq(1));
    verify(chunkJdbcService).updateChunk(mappingData.chunkId(), OperationStatusType.DATA_MAPPING_FAILED);
  }

  @Test
  void process_negative_noMappingMetadata() {
    when(mappingMetadataProvider.getMappingData(AUTHORITY))
      .thenReturn(null);

    process_negative("Failed to fetch mapping metadata", "\"version\":1");
  }

  @Test
  @SneakyThrows
  void process_negative_noMappingMetadata_jsonException() {
    when(mappingMetadataProvider.getMappingData(AUTHORITY))
      .thenReturn(null);
    when(objectMapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);

    process_negative("Failed to fetch mapping metadata", "\"version\": 1");
  }

  @Test
  @SneakyThrows
  void process_negative_mappingException() {
    var excMessage = "test exc";
    when(objectMapper.writeValueAsString(any()))
      .thenThrow(new IllegalStateException(excMessage))
      .thenCallRealMethod()
      .thenThrow(new IllegalStateException(excMessage))
      .thenCallRealMethod();

    process_negative(excMessage, "\"version\":1");
  }

  @Test
  @SneakyThrows
  void process_negative_mappingException_thenJsonException() {
    var excMessage = "test exc";
    when(objectMapper.writeValueAsString(any()))
      .thenThrow(new IllegalStateException(excMessage))
      .thenThrow(JsonProcessingException.class)
      .thenThrow(new IllegalStateException(excMessage))
      .thenThrow(JsonProcessingException.class);

    process_negative(excMessage, "\"version\": 1");
  }

  private void assertMappingResult(MappingComposite<MappingResult> actual, List<MarcRecord> records) {
    assertThat(actual).isNotNull();
    assertThat(actual.mappingData()).isEqualTo(mappingData);
    assertThat(actual.records())
      .hasSize(records.size());
    var actualRecords = new ArrayList<>(actual.records());
    assertThat(actualRecords.removeFirst()).matches(result ->
      result.mappedRecord() == null
      && result.errorCause().contains("test exc")
      && result.invalidMarcRecord() != null
      && result.invalidMarcRecord().contains("\"version\":1"));
    assertThat(actualRecords).allMatch(result ->
      result.invalidMarcRecord() == null
      && result.errorCause() == null
      && result.mappedRecord() != null
      && result.mappedRecord().contains("\"_version\":1,\"source\":\"MARC\"")
    );
  }

  private void process_negative(String errorCause, String marcRecordContains) {
    var records = records();
    var composite = new MappingComposite<>(mappingData, records);
    mapper.setEntityType(AUTHORITY);
    var actual = mapper.process(composite);

    assertThat(actual).isNotNull();
    assertThat(actual.mappingData()).isEqualTo(mappingData);
    assertThat(actual.records())
      .hasSize(records.size())
      .allMatch(result -> result.mappedRecord() == null
                          && result.errorCause() != null
                          && result.errorCause().contains(errorCause)
                          && result.invalidMarcRecord() != null
                          && result.invalidMarcRecord().contains(marcRecordContains));

    records.stream().map(mr -> mr.recordId().toString()).forEach(authorityId ->
      assertThat(actual.records().stream()
        .anyMatch(mappingResult -> mappingResult.invalidMarcRecord().contains(authorityId)))
        .isTrue());
    verify(chunkStepJdbcService)
      .updateChunkStep(eq(mappingData.stepId()), eq(StepStatus.FAILED), any(Timestamp.class), eq(records.size()));
    verify(chunkJdbcService).updateChunk(mappingData.chunkId(), OperationStatusType.DATA_MAPPING_FAILED);
  }

  private List<MarcRecord> records() {
    return Stream.iterate(0, i -> i < 2, i -> ++i)
      .map(i -> new MarcRecord(null, UUID.randomUUID(), "{}", null, 1))
      .toList();
  }
}
