package org.folio.marc.migrations.services.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.services.batch.support.MappingMetadataProvider;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
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
class RecordsChunkMapperTest {

  private @Spy ObjectMapper objectMapper;
  private @Mock MappingMetadataProvider mappingMetadataProvider;
  private @Mock OperationJdbcService jdbcService;
  private @InjectMocks RecordsChunkMapper mapper;

  @BeforeEach
  @SneakyThrows
  void setup() {
    lenient().when(mappingMetadataProvider.getMappingData())
      .thenReturn(new MappingMetadataProvider.MappingData(new JsonObject(), new MappingParameters()));
  }

  @Test
  void process_positive() {
    var mappingData = new RecordsMappingData(UUID.randomUUID(), null, null, null, 2, null, null);
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
  }

  @Test
  @SneakyThrows
  void process_positive_partial() {
    var mappingData = new RecordsMappingData(UUID.randomUUID(), null, null, null, 2, null, null);
    var records = records();
    var composite = new MappingComposite<>(mappingData, records);
    when(objectMapper.writeValueAsString(any()))
      .thenThrow(new IllegalStateException("test exc"))
      .thenCallRealMethod();

    var actual = mapper.process(composite);

    assertThat(actual).isNotNull();
    assertThat(actual.mappingData()).isEqualTo(mappingData);
    assertThat(actual.records())
      .hasSize(records.size());
    var actualRecords = new ArrayList<>(actual.records());
    assertThat(actualRecords.remove(0)).matches(result ->
      result.mappedRecord() == null
        && "test exc".equals(result.errorCause())
        && result.invalidMarcRecord() != null
        && result.invalidMarcRecord().contains("\"version\":1"));
    assertThat(actualRecords).allMatch(result ->
      result.invalidMarcRecord() == null
        && result.errorCause() == null
        && result.mappedRecord() != null
        && result.mappedRecord().contains("\"_version\":1,\"source\":\"MARC\"")
    );

    records.stream().map(mr -> mr.recordId().toString()).forEach(authorityId ->
      assertThat(actual.records().stream()
        .anyMatch(mappingResult -> Optional.ofNullable(mappingResult.mappedRecord())
          .orElse(mappingResult.invalidMarcRecord())
          .contains(authorityId)))
        .isTrue());
    verify(jdbcService).addProcessedOperationRecords(mappingData.operationId(), records.size() - 1, 0);
  }

  @Test
  void process_negative_noMappingMetadata() {
    when(mappingMetadataProvider.getMappingData())
      .thenReturn(null);

    process_negative("Failed to fetch mapping metadata", "\"version\":1");
  }

  @Test
  @SneakyThrows
  void process_negative_noMappingMetadata_jsonException() {
    when(mappingMetadataProvider.getMappingData())
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

  private void process_negative(String errorCause, String marcRecordContains) {
    var mappingData = new RecordsMappingData(UUID.randomUUID(), null, null, null, 2, null, null);
    var records = records();
    var composite = new MappingComposite<>(mappingData, records);

    var actual = mapper.process(composite);

    assertThat(actual).isNotNull();
    assertThat(actual.mappingData()).isEqualTo(mappingData);
    assertThat(actual.records())
      .hasSize(records.size())
      .allMatch(result -> result.mappedRecord() == null
        && result.errorCause() != null
        && result.errorCause().equals(errorCause)
        && result.invalidMarcRecord() != null
        && result.invalidMarcRecord().contains(marcRecordContains));

    records.stream().map(mr -> mr.recordId().toString()).forEach(authorityId ->
      assertThat(actual.records().stream()
        .anyMatch(mappingResult -> mappingResult.invalidMarcRecord().contains(authorityId)))
        .isTrue());
    verifyNoInteractions(jdbcService);
  }

  private List<MarcRecord> records() {
    return Stream.iterate(0, i -> i < 2, i -> ++i)
      .map(i -> new MarcRecord(null, UUID.randomUUID(), "{}", null, 1))
      .toList();
  }
}
