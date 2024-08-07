package org.folio.marc.migrations.services.batch.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.entities.types.EntityType.AUTHORITY;
import static org.folio.marc.migrations.domain.entities.types.EntityType.INSTANCE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingRecordsChunkPreProcessorTest {

  private static final UUID AUTHORITY_OPERATION_ID = UUID.randomUUID();
  private static final UUID INSTANCE_OPERATION_ID = UUID.randomUUID();

  private @Mock AuthorityJdbcService authorityJdbcService;
  private @Mock ChunkStepJdbcService chunkStepJdbcService;
  private @Mock InstanceJdbcService instanceJdbcService;
  private @InjectMocks MappingRecordsChunkPreProcessor processor;

  @Test
  void processAuthority_positive() {
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, AUTHORITY_OPERATION_ID);
    var marcRecords = marcRecords(numOfRecords);

    when(authorityJdbcService.getAuthoritiesChunk(chunk.getStartRecordId(), chunk.getEndRecordId()))
      .thenReturn(marcRecords);
    processor.setEntityType(AUTHORITY);
    process_positive(chunk, marcRecords);
  }

  @Test
  void processInstance_positive() {
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, INSTANCE_OPERATION_ID);
    var marcRecords = marcRecords(numOfRecords);

    when(instanceJdbcService.getInstancesChunk(chunk.getStartRecordId(), chunk.getEndRecordId()))
        .thenReturn(marcRecords);
    processor.setEntityType(INSTANCE);

    process_positive(chunk, marcRecords);
  }

  void process_positive(OperationChunk chunk, List<MarcRecord> marcRecords) {
    var stepCaptor = ArgumentCaptor.forClass(ChunkStep.class);

    var actual = processor.process(chunk);

    verify(chunkStepJdbcService).createChunkStep(stepCaptor.capture());
    var step = stepCaptor.getValue();
    assertChunkStep(chunk, step);
    assertMappingData(chunk, step, actual.mappingData());
    assertThat(actual.records()).hasSize(marcRecords.size()).containsAll(marcRecords);
  }

  private void assertChunkStep(OperationChunk chunk, ChunkStep step) {
    var softAssert = new SoftAssertions();

    softAssert.assertThat(step.getId()).isNotNull();
    softAssert.assertThat(step.getOperationId()).isEqualTo(chunk.getOperationId());
    softAssert.assertThat(step.getOperationChunkId()).isEqualTo(chunk.getId());
    softAssert.assertThat(step.getOperationStep()).isEqualTo(OperationStep.DATA_MAPPING);
    softAssert.assertThat(step.getEntityErrorChunkFileName())
      .contains(chunk.getOperationId().toString(), chunk.getId().toString(), step.getId().toString(), "entity-error");
    softAssert.assertThat(step.getErrorChunkFileName())
      .contains(chunk.getOperationId().toString(), chunk.getId().toString(), step.getId().toString(), "error");
    softAssert.assertThat(step.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
    softAssert.assertThat(step.getStepStartTime()).isNotNull().isBefore(Instant.now());

    softAssert.assertAll();
  }

  private void assertMappingData(OperationChunk chunk, ChunkStep step, RecordsMappingData mappingData) {
    var softAssert = new SoftAssertions();

    softAssert.assertThat(mappingData.operationId()).isEqualTo(chunk.getOperationId());
    softAssert.assertThat(mappingData.chunkId()).isEqualTo(chunk.getId());
    softAssert.assertThat(mappingData.stepId()).isEqualTo(step.getId());
    softAssert.assertThat(mappingData.entityChunkFile()).isEqualTo(chunk.getEntityChunkFileName());
    softAssert.assertThat(mappingData.numberOfRecords()).isEqualTo(chunk.getNumOfRecords());
    softAssert.assertThat(mappingData.entityErrorChunkFileName()).isEqualTo(step.getEntityErrorChunkFileName());
    softAssert.assertThat(mappingData.errorChunkFileName()).isEqualTo(step.getErrorChunkFileName());

    softAssert.assertAll();
  }

  private OperationChunk chunk(int numOfRecords, UUID operationId) {
    return OperationChunk.builder()
      .id(UUID.randomUUID())
      .operationId(operationId)
      .startRecordId(UUID.randomUUID())
      .endRecordId(UUID.randomUUID())
      .numOfRecords(numOfRecords)
      .entityChunkFileName("entity" + numOfRecords)
      .marcChunkFileName("marc" + numOfRecords)
      .sourceChunkFileName("source" + numOfRecords)
      .status(OperationStatusType.DATA_MAPPING)
      .build();
  }

  private List<MarcRecord> marcRecords(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i -> new MarcRecord(null, null, null, null, null))
      .toList();
  }
}
