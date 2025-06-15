package org.folio.marc.migrations.services.batch.saving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.entities.types.EntityType.AUTHORITY;
import static org.folio.marc.migrations.domain.entities.types.EntityType.INSTANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.folio.marc.migrations.client.BulkClient.BulkResponse;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.marc.migrations.services.domain.RecordsSavingData;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SavingRecordsChunkProcessorTest {
  private static final UUID AUTHORITY_OPERATION_ID = UUID.randomUUID();
  private static final UUID INSTANCE_OPERATION_ID = UUID.randomUUID();

  private @Mock BulkStorageService bulkStorageService;
  private @Mock ChunkStepJdbcService chunkStepJdbcService;
  private @InjectMocks SavingRecordsChunkProcessor processor;

  @BeforeEach
  void setUpMocks() {
    BulkResponse bulkResponse = new BulkResponse();
    bulkResponse.setErrorsNumber(0);

    when(bulkStorageService.saveEntities(any(), any(), eq(Boolean.TRUE)))
        .thenReturn(bulkResponse);
  }

  @Test
  void saveAuthority_positive() {
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, AUTHORITY_OPERATION_ID, OperationStatusType.DATA_MAPPING);
    processor.setEntityType(AUTHORITY);
    processor.setPublishEventsFlag(Boolean.TRUE);

    save_positive(chunk, AUTHORITY);
  }

  @Test
  void saveInstance_positive() {
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, INSTANCE_OPERATION_ID, OperationStatusType.DATA_MAPPING);
    processor.setEntityType(INSTANCE);
    processor.setPublishEventsFlag(Boolean.TRUE);

    save_positive(chunk, INSTANCE);
  }

  @Test
  void process_retry_shouldUpdateExistingChunkStep() {
    // Arrange
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, AUTHORITY_OPERATION_ID, OperationStatusType.DATA_SAVING_FAILED);
    var existingChunkStep = createChunkStep(chunk);
    when(chunkStepJdbcService.getChunkStepByChunkIdAndOperationStep(chunk.getId(), OperationStep.DATA_SAVING))
      .thenReturn(existingChunkStep);
    processor.setEntityType(AUTHORITY);
    processor.setPublishEventsFlag(Boolean.TRUE);

    // Act
    var result = processor.process(chunk);

    // Assert
    assertThat(result).isNotNull();
    verify(chunkStepJdbcService).updateChunkStep(eq(existingChunkStep.getId()), eq(StepStatus.IN_PROGRESS),
        any(Timestamp.class));
    verify(bulkStorageService).saveEntities(chunk.getEntityChunkFileName(), AUTHORITY, Boolean.TRUE);
  }

  @Test
  void process_retry_shouldCreateNewChunkStep_whenNoExistingChunkStep() {
    // Arrange
    int numOfRecords = 5;
    var chunk = chunk(numOfRecords, AUTHORITY_OPERATION_ID, OperationStatusType.DATA_SAVING_FAILED);
    when(chunkStepJdbcService.getChunkStepByChunkIdAndOperationStep(chunk.getId(), OperationStep.DATA_SAVING))
        .thenReturn(null);
    processor.setEntityType(AUTHORITY);
    processor.setPublishEventsFlag(Boolean.TRUE);

    // Act
    var result = processor.process(chunk);

    // Assert
    assertThat(result).isNotNull();
    verify(chunkStepJdbcService).createChunkStep(any());
    verify(bulkStorageService).saveEntities(chunk.getEntityChunkFileName(), AUTHORITY, Boolean.TRUE);
  }

  void save_positive(OperationChunk chunk, EntityType entityType) {
    var actual = processor.process(chunk);
    assertThat(actual).isNotNull();
    assertThat(actual.saveResponse()).isNotNull();
    assertThat(actual.saveResponse().getErrorsNumber()).isZero();

    var stepCaptor = ArgumentCaptor.forClass(ChunkStep.class);
    verify(chunkStepJdbcService).createChunkStep(stepCaptor.capture());
    verify(bulkStorageService).saveEntities(chunk.getEntityChunkFileName(), entityType, Boolean.TRUE);
    var step = stepCaptor.getValue();
    var operationId = entityType == AUTHORITY ? AUTHORITY_OPERATION_ID : INSTANCE_OPERATION_ID;

    assertRecordSavingData(actual.recordsSavingData(), chunk, operationId, step);
    assertChunkStep(chunk, step);
  }

  private void assertRecordSavingData(RecordsSavingData recordsSavingData, OperationChunk chunk,
                                      UUID operationId, ChunkStep step) {
    var softAssert = new SoftAssertions();

    softAssert.assertThat(recordsSavingData.chunkId()).isEqualTo(chunk.getId());
    softAssert.assertThat(recordsSavingData.operationId()).isEqualTo(operationId);
    softAssert.assertThat(recordsSavingData.numberOfRecords()).isEqualTo(chunk.getNumOfRecords());
    softAssert.assertThat(recordsSavingData.stepId()).isEqualTo(step.getId());

    softAssert.assertAll();
  }

  private void assertChunkStep(OperationChunk chunk, ChunkStep step) {
    var softAssert = new SoftAssertions();

    softAssert.assertThat(step.getId()).isNotNull();
    softAssert.assertThat(step.getOperationId()).isEqualTo(chunk.getOperationId());
    softAssert.assertThat(step.getOperationChunkId()).isEqualTo(chunk.getId());
    softAssert.assertThat(step.getOperationStep()).isEqualTo(OperationStep.DATA_SAVING);
    softAssert.assertThat(step.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
    softAssert.assertThat(step.getStepStartTime()).isNotNull().isBefore(Instant.now());

    softAssert.assertAll();
  }

  private OperationChunk chunk(int numOfRecords, UUID operationId, OperationStatusType status) {
    return OperationChunk.builder()
        .id(UUID.randomUUID())
        .operationId(operationId)
        .startRecordId(UUID.randomUUID())
        .endRecordId(UUID.randomUUID())
        .numOfRecords(numOfRecords)
        .entityChunkFileName("entity" + numOfRecords)
        .marcChunkFileName("marc" + numOfRecords)
        .sourceChunkFileName("source" + numOfRecords)
        .status(status)
        .build();
  }

  private ChunkStep createChunkStep(OperationChunk chunk) {
    return ChunkStep.builder()
      .id(UUID.randomUUID())
      .operationId(chunk.getOperationId())
      .operationChunkId(chunk.getId())
      .operationStep(OperationStep.DATA_SAVING)
      .status(StepStatus.FAILED)
      .numOfErrors(0)
      .entityErrorChunkFileName(chunk.getEntityChunkFileName())
      .stepStartTime(Timestamp.from(Instant.now()))
      .build();
  }
}
