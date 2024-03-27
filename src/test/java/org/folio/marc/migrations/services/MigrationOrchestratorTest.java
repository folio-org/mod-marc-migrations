package org.folio.marc.migrations.services;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {
  private @Spy MigrationProperties migrationProperties;
  private @Mock ChunkService chunkService;
  private @Mock OperationJdbcService jdbcService;
  private @Mock FolioExecutionContext context;
  private @Mock FolioExecutor remappingExecutor;
  private @Mock JobLauncher jobLauncher;
  private @Mock Job job;
  private @InjectMocks MigrationOrchestrator service;

  @Test
  @SneakyThrows
  void submitMappingTask_positive() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operation.getId().toString()), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jobLauncher).run(job, new JobParameters(
      Map.of(OPERATION_ID, new JobParameter<>(operation.getId().toString(), String.class))));
    verifyNoMoreInteractions(jdbcService);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_negative_shouldFailAndUpdateOperationStatus_whenPreparationFails() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(chunkService).prepareChunks(operation);

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operation.getId().toString()), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jdbcService).updateOperationStatus(eq(operation.getId().toString()),
      eq(OperationStatusType.DATA_MAPPING_FAILED), eq(OperationTimeType.MAPPING_END), notNull());
    verifyNoInteractions(jobLauncher);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_negative_shouldFailAndUpdateOperationStatus_whenBatchJobFails() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(remappingExecutor).execute(any());
    doThrow(new IllegalStateException()).when(jobLauncher).run(any(), any());

    // Act
    service.submitMappingTask(operation).get(200, TimeUnit.MILLISECONDS);

    // Assert
    verify(jdbcService).updateOperationStatus(eq(operation.getId().toString()), eq(OperationStatusType.DATA_MAPPING),
      eq(OperationTimeType.MAPPING_START), notNull());
    verify(chunkService).prepareChunks(operation);
    verify(jobLauncher).run(job, new JobParameters(
      Map.of(OPERATION_ID, new JobParameter<>(operation.getId().toString(), String.class))));
    verify(jdbcService).updateOperationStatus(eq(operation.getId().toString()),
      eq(OperationStatusType.DATA_MAPPING_FAILED), eq(OperationTimeType.MAPPING_END), notNull());
  }
}
