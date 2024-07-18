package org.folio.marc.migrations.services;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.ENTITY_TYPE;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {

  private @Mock ChunkService chunkService;
  private @Mock OperationJdbcService jdbcService;
  private @Mock JobLauncher jobLauncher;
  private @Mock Job job;
  private @Mock FolioExecutor remappingExecutor;
  private @InjectMocks MigrationOrchestrator service;

  @Test
  void submitAuthorityMappingTask_positive() {
    submitMappingTask_positive(EntityType.AUTHORITY);
  }


  @Test
  void submitInstanceMappingTask_positive() {
    submitMappingTask_positive(EntityType.INSTANCE);
  }

  @SneakyThrows
  void submitMappingTask_positive(EntityType entityType) {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setStatus(OperationStatusType.DATA_MAPPING);
    operation.setEntityType(entityType);
    var operationId = operation.getId().toString();
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING),
        eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jobLauncher).run(job, new JobParameters(
        Map.of(
            OPERATION_ID, new JobParameter<>(operationId, String.class),
            ENTITY_TYPE, new JobParameter<>(operation.getEntityType(), EntityType.class)
        )));
    verify(jdbcService).getOperation(operationId);
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_negative_shouldFailAndUpdateOperationStatus_whenPreparationFails() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    var operationId = operation.getId().toString();
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(chunkService).prepareChunks(operation);

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jdbcService).updateOperationStatus(eq(operationId),
      eq(OperationStatusType.DATA_MAPPING_FAILED), eq(OperationTimeType.MAPPING_END), notNull());
    verifyNoInteractions(jobLauncher);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setStatus(OperationStatusType.DATA_MAPPING);
    operation.setEntityType(EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doThrow(new IllegalStateException()).when(jobLauncher).run(any(), any());

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jobLauncher).run(job, new JobParameters(
      Map.of(
          OPERATION_ID, new JobParameter<>(operationId, String.class),
          ENTITY_TYPE, new JobParameter<>(operation.getEntityType(), EntityType.class)
      )));
    verify(jdbcService).updateOperationStatus(eq(operationId),
      eq(OperationStatusType.DATA_MAPPING_FAILED), eq(OperationTimeType.MAPPING_END), notNull());
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingSaveTask_positive() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setStatus(OperationStatusType.DATA_SAVING);
    operation.setEntityType(EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Act
    service.submitMappingSaveTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
        eq(OperationTimeType.SAVING_START), notNull());
    verify(jobLauncher).run(job, new JobParameters(
        Map.of(
            OPERATION_ID, new JobParameter<>(operationId, String.class),
            ENTITY_TYPE, new JobParameter<>(operation.getEntityType(), EntityType.class)
        )));
    verify(jdbcService).getOperation(operationId);
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingSaveTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setStatus(OperationStatusType.DATA_SAVING);
    operation.setEntityType(EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(jobLauncher).run(any(), any());

    // Act
    service.submitMappingSaveTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
        eq(OperationTimeType.SAVING_START), notNull());
    verify(jobLauncher).run(job, new JobParameters(
        Map.of(
            OPERATION_ID, new JobParameter<>(operationId, String.class),
            ENTITY_TYPE, new JobParameter<>(operation.getEntityType(), EntityType.class)
        )));
    verify(jdbcService).getOperation(operationId);
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING_FAILED),
        eq(OperationTimeType.SAVING_END), notNull());
    verifyNoMoreInteractions(jdbcService);
  }
}
