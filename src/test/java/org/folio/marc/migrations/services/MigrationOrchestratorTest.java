package org.folio.marc.migrations.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.ENTITY_TYPE;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.PUBLISH_EVENTS_FLAG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.batch.support.JobConstants;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.jdbc.SpringBatchExecutionParamsJdbcService;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {

  private @Mock ChunkService chunkService;
  private @Mock OperationJdbcService jdbcService;
  private @Mock JobOperator jobOperator;
  private @Mock Job job;
  private @Mock FolioExecutor remappingExecutor;
  private @Mock SpringBatchExecutionParamsJdbcService executionParamsJdbcService;
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
    var operation = prepareOperation(OperationStatusType.DATA_MAPPING, entityType);
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
    verify(jobOperator).start(job, new JobParameters(
      Set.of(
        new JobParameter<>(OPERATION_ID, operationId, String.class),
        new JobParameter<>(ENTITY_TYPE, operation.getEntityType(), EntityType.class)
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
    verifyNoInteractions(jobOperator);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_MAPPING, EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doThrow(new IllegalStateException()).when(jobOperator).start(any(Job.class), any(JobParameters.class));

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jobOperator).start(job, new JobParameters(
      Set.of(
        new JobParameter<>(OPERATION_ID, operationId, String.class),
        new JobParameter<>(ENTITY_TYPE, operation.getEntityType(), EntityType.class)
      )));
    verify(jdbcService).updateOperationStatus(eq(operationId),
      eq(OperationStatusType.DATA_MAPPING_FAILED), eq(OperationTimeType.MAPPING_END), notNull());
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingSaveTask_positive() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_SAVING, EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    var validSaveOperation = new SaveMigrationOperation()
      .status(MigrationOperationStatus.DATA_SAVING);
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Act
    service.submitMappingSaveTask(operation, validSaveOperation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
      eq(OperationTimeType.SAVING_START), notNull());
    verify(jobOperator).start(job, new JobParameters(
      Set.of(
        new JobParameter<>(OPERATION_ID, operationId, String.class),
        new JobParameter<>(ENTITY_TYPE, operation.getEntityType(), EntityType.class),
        new JobParameter<>(PUBLISH_EVENTS_FLAG, validSaveOperation.getPublishEvents(), Boolean.class)
      )));
    verify(jdbcService).getOperation(operationId);
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingSaveTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_SAVING, EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    var validSaveOperation = new SaveMigrationOperation()
      .status(MigrationOperationStatus.DATA_SAVING);
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(jobOperator).start(any(Job.class), any(JobParameters.class));

    // Act
    service.submitMappingSaveTask(operation, validSaveOperation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
      eq(OperationTimeType.SAVING_START), notNull());
    verify(jobOperator).start(job, new JobParameters(
      Set.of(
        new JobParameter<>(OPERATION_ID, operationId, String.class),
        new JobParameter<>(ENTITY_TYPE, operation.getEntityType(), EntityType.class),
        new JobParameter<>(PUBLISH_EVENTS_FLAG, validSaveOperation.getPublishEvents(), Boolean.class)
      )));
    verify(jdbcService).getOperation(operationId);
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING_FAILED),
      eq(OperationTimeType.SAVING_END), notNull());
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitRetryMappingTask_positive() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_MAPPING, EntityType.AUTHORITY);
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Captor for JobParameters
    ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

    // Act
    service.submitRetryMappingTask(operation, chunkIds).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(chunkService).updateChunkStatus(chunkIds, OperationStatusType.DATA_MAPPING);
    verify(jobOperator).start(eq(job), jobParametersCaptor.capture());
    verifyNoMoreInteractions(jdbcService);

    // Verify captured timestamp
    var capturedParameters = jobParametersCaptor.getValue();
    var capturedTimestamp = capturedParameters.getParameter(JobConstants.JobParameterNames.TIMESTAMP).value();
    Assertions.assertNotNull(capturedTimestamp);
    Assertions.assertInstanceOf(String.class, capturedTimestamp);
  }

  @Test
  @SneakyThrows
  void submitRetryMappingTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_MAPPING, EntityType.AUTHORITY);
    var operationId = operation.getId().toString();
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(jobOperator).start(any(Job.class), any(JobParameters.class));

    // Captor for JobParameters
    ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

    // Act
    service.submitRetryMappingTask(operation, chunkIds).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(chunkService).updateChunkStatus(chunkIds, OperationStatusType.DATA_MAPPING);
    verify(jobOperator).start(eq(job), jobParametersCaptor.capture());
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING_FAILED),
      eq(OperationTimeType.MAPPING_END), notNull());
    verifyNoMoreInteractions(jdbcService);

    // Verify captured JobParameters
    var capturedParameters = jobParametersCaptor.getValue();
    var capturedTimestamp = capturedParameters.getParameter(JobConstants.JobParameterNames.TIMESTAMP).value();
    assertThat(capturedTimestamp).isInstanceOf(String.class);
  }

  @Test
  @SneakyThrows
  void submitMappingSaveRetryTask_positive() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_SAVING, EntityType.AUTHORITY);
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var operationId = operation.getId().toString();

    when(executionParamsJdbcService.getBatchExecutionParam(PUBLISH_EVENTS_FLAG, operationId)).thenReturn("true");
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Captor for JobParameters
    ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

    // Act
    service.submitMappingSaveRetryTask(operation, chunkIds).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
      eq(OperationTimeType.SAVING_START), notNull());
    verify(jobOperator).start(eq(job), jobParametersCaptor.capture());
    verifyNoMoreInteractions(jdbcService);

    // Verify captured JobParameters
    var capturedParameters = jobParametersCaptor.getValue();
    var capturedTimestamp = capturedParameters.getParameter(JobConstants.JobParameterNames.TIMESTAMP).value();
    assertThat(capturedTimestamp).isInstanceOf(String.class);
  }

  @SneakyThrows
  @Test
  void submitMappingSaveRetryTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = prepareOperation(OperationStatusType.DATA_SAVING, EntityType.AUTHORITY);
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var operationId = operation.getId().toString();

    when(executionParamsJdbcService.getBatchExecutionParam(PUBLISH_EVENTS_FLAG, operationId)).thenReturn("true");
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(jobOperator).start(any(Job.class), any(JobParameters.class));

    // Captor for JobParameters
    ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

    // Act
    service.submitMappingSaveRetryTask(operation, chunkIds).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING),
      eq(OperationTimeType.SAVING_START), notNull());
    verify(jobOperator).start(eq(job), jobParametersCaptor.capture());
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_SAVING_FAILED),
      eq(OperationTimeType.SAVING_END), notNull());
    verifyNoMoreInteractions(jdbcService);

    // Verify captured JobParameters
    var capturedParameters = jobParametersCaptor.getValue();
    var capturedTimestamp = capturedParameters.getParameter(JobConstants.JobParameterNames.TIMESTAMP).value();
    assertThat(capturedTimestamp).isInstanceOf(String.class);
  }

  private Operation prepareOperation(OperationStatusType dataSaving, EntityType authority) {
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setStatus(dataSaving);
    operation.setEntityType(authority);
    return operation;
  }
}
