package org.folio.marc.migrations.controllers.delegates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.controllers.delegates.MarcMigrationsService.NOT_FOUND_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationCollection;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationError;
import org.folio.marc.migrations.domain.entities.OperationErrorReport;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.MigrationOrchestrator;
import org.folio.marc.migrations.services.operations.OperationErrorReportService;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.folio.spring.data.OffsetRequest;
import org.folio.spring.exception.NotFoundException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MarcMigrationsServiceTest {

  private @Mock MarcMigrationMapper mapper;
  private @Mock OperationsService operationsService;
  private @Mock MigrationOrchestrator migrationOrchestrator;
  private @Mock OperationErrorReportService errorReportsService;
  private @Mock MigrationProperties props;
  private @InjectMocks MarcMigrationsService migrationsService;

  @Test
  void createNewMigration_ValidInput_ReturnsMigrationOperation() {
    // Arrange
    var validOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
    var operation = new Operation();
    when(mapper.toEntity(validOperation)).thenReturn(operation);
    when(operationsService.createOperation(operation)).thenReturn(operation);
    when(mapper.toDto(operation)).thenReturn(new MigrationOperation());

    // Act
    MigrationOperation result = migrationsService.createNewMigration(validOperation);

    // Assert
    assertNotNull(result);
    verify(operationsService).createOperation(operation);
    verify(migrationOrchestrator).submitMappingTask(operation);
  }

  @Test
  void createNewMigration_InvalidOperationType_ThrowsApiValidationException() {
    // Arrange
    var invalidOperation = new NewMigrationOperation()
      .operationType(OperationType.IMPORT)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    assertThrows(ApiValidationException.class, () -> migrationsService.createNewMigration(invalidOperation));
  }

  @Test
  void getMarcMigrationById_Valid_ReturnsMigrationOperation() {
    // Arrange
    var operationId = UUID.randomUUID();
    var fetchedOperation = new Operation();
    var operationDto = new MigrationOperation();
    when(operationsService.getOperation(operationId)).thenReturn(Optional.of(fetchedOperation));
    when(mapper.toDto(fetchedOperation)).thenReturn(operationDto);

    // Act
    var result = migrationsService.getMarcMigrationById(operationId);

    // Assert
    assertEquals(operationDto, result);
    verify(operationsService).getOperation(operationId);
  }

  @Test
  void getMarcMigrationById_NotExists_ThrowsNotFoundException() {
    // Arrange
    var randomId = UUID.randomUUID();
    when(operationsService.getOperation(randomId)).thenReturn(Optional.empty());

    // Act & Assert
    var exception = assertThrows(NotFoundException.class, () -> migrationsService.getMarcMigrationById(randomId));
    assertThat(exception).hasMessage(NOT_FOUND_MSG, randomId);
  }

  @Test
  void getMarcMigrations_Valid_ReturnsMigrationOperations() {
    // Arrange
    var offset = 0;
    var limit = 100;
    var dtoCollection = new MigrationOperationCollection(1, List.of(new MigrationOperation()));
    var page = new PageImpl<>(List.of(new Operation()), OffsetRequest.of(offset, limit), 1);
    when(operationsService.getOperations(offset, limit, null)).thenReturn(page);
    when(mapper.toDtoCollection(page)).thenReturn(dtoCollection);

    // Act
    var result = migrationsService.getMarcMigrations(offset, limit, null);

    // Assert
    assertEquals(dtoCollection, result);
    verify(operationsService).getOperations(offset, limit, null);
  }

  @Test
  void saveMigration_ValidInput_submitsSaveMigrationOperation() {
    // Arrange
    var operationId = UUID.randomUUID();
    var operation = new Operation();
    operation.setId(operationId);
    operation.setStatus(OperationStatusType.DATA_MAPPING_COMPLETED);
    when(operationsService.getOperation(operationId)).thenReturn(Optional.of(operation));
    var validSaveOperation = new SaveMigrationOperation()
      .status(MigrationOperationStatus.DATA_SAVING);

    // Act
    migrationsService.saveMigrationOperation(operationId, validSaveOperation);

    // Assert
    verify(operationsService).getOperation(operationId);
    verify(migrationOrchestrator).submitMappingSaveTask(operation, validSaveOperation);
  }

  @Test
  void saveMigration_NotExists_ThrowsNotFoundException() {
    // Arrange
    var validSaveOperation = new SaveMigrationOperation()
      .status(MigrationOperationStatus.DATA_SAVING);
    var operationId = UUID.randomUUID();
    when(operationsService.getOperation(operationId)).thenReturn(Optional.empty());

    // Act
    var exception = assertThrows(NotFoundException.class,
      () -> migrationsService.saveMigrationOperation(operationId, validSaveOperation));

    // Assert
    assertThat(exception).hasMessage(NOT_FOUND_MSG, operationId);
    verify(operationsService).getOperation(operationId);
    verifyNoInteractions(migrationOrchestrator);
  }

  @Test
  void saveMigration_InvalidStatus_ThrowsApiValidationException() {
    // Arrange
    var operationId = UUID.randomUUID();
    var operation = new Operation();
    operation.setId(operationId);
    operation.setStatus(OperationStatusType.DATA_MAPPING_FAILED);
    when(operationsService.getOperation(operationId)).thenReturn(Optional.of(operation));
    var validSaveOperation = new SaveMigrationOperation()
      .status(MigrationOperationStatus.DATA_SAVING);

    // Act & Assert
    assertThrows(ApiValidationException.class,
      () -> migrationsService.saveMigrationOperation(operationId, validSaveOperation));
    verify(operationsService).getOperation(operationId);
    verifyNoInteractions(migrationOrchestrator);
  }

  @Test
  void createErrorReport_Success() {
    // Arrange
    var operationId = UUID.randomUUID();
    var tenantId = "testTenant";
    var operation = new Operation();
    when(operationsService.getOperation(operationId)).thenReturn(Optional.of(operation));
    when(errorReportsService.initiateErrorReport(operation, tenantId))
      .thenReturn(CompletableFuture.completedFuture(null));

    // Act
    migrationsService.createErrorReport(operationId, tenantId);

    // Assert
    verify(operationsService).getOperation(operationId);
    verify(errorReportsService).initiateErrorReport(operation, tenantId);
  }

  @Test
  void createErrorReport_NotFound_ThrowsNotFoundException() {
    // Arrange
    var operationId = UUID.randomUUID();
    var tenantId = "testTenant";
    when(operationsService.getOperation(operationId)).thenReturn(Optional.empty());

    // Act & Assert
    var exception = assertThrows(NotFoundException.class,
      () -> migrationsService.createErrorReport(operationId, tenantId));
    assertThat(exception).hasMessage(NOT_FOUND_MSG, operationId);
    verifyNoInteractions(errorReportsService);
  }

  @Test
  void getErrorReportStatus_Success() {
    // Arrange
    var operationId = UUID.randomUUID();
    var errorReport = new OperationErrorReport();
    errorReport.setStatus(ErrorReportStatus.IN_PROGRESS);
    when(errorReportsService.getErrorReport(operationId)).thenReturn(Optional.of(errorReport));

    // Act
    var result = migrationsService.getErrorReportStatus(operationId);

    // Assert
    assertNotNull(result);
    assertEquals(operationId, result.getOperationId());
    assertEquals("in_progress", result.getStatus().getValue());
  }

  @Test
  void getErrorReportStatus_NotFound_ThrowsNotFoundException() {
    // Arrange
    var operationId = UUID.randomUUID();
    when(errorReportsService.getErrorReport(operationId)).thenReturn(Optional.empty());

    // Act & Assert
    var exception = assertThrows(NotFoundException.class,
      () -> migrationsService.getErrorReportStatus(operationId));
    assertThat(exception).hasMessage(NOT_FOUND_MSG, operationId);
  }

  @Test
  void getErrorReportEntries_Success() {
    // Arrange
    final var operationId = UUID.randomUUID();
    final var offset = 0;
    final var limit = 10;
    var operationError = new OperationError();
    operationError.setReportId(UUID.randomUUID());
    operationError.setChunkId(UUID.randomUUID());
    operationError.setOperationStep(OperationStep.DATA_MAPPING);
    operationError.setChunkStatus(StepStatus.FAILED);
    operationError.setRecordId("record1");
    operationError.setErrorMessage("Test error");

    when(errorReportsService.getErrorReportEntries(eq(operationId), any(OffsetRequest.class)))
      .thenReturn(List.of(operationError));

    // Act
    var result = migrationsService.getErrorReportEntries(operationId, offset, limit);

    // Assert
    assertNotNull(result);
    assertThat(result.getErrorReports()).hasSize(1);
    var errorReport = result.getErrorReports().getFirst();
    assertEquals(operationError.getReportId(), errorReport.getOperationId());
    assertEquals(operationError.getChunkId().toString(), errorReport.getChunkId());
    assertEquals(operationError.getChunkStatus().name(), errorReport.getChunkStatus());
    assertEquals(operationError.getRecordId(), errorReport.getRecordId());
    assertEquals(operationError.getErrorMessage(), errorReport.getErrorMessage());
  }

  @Test
  void retryMarcMigration_ValidInput_ReturnsMigrationOperation() {
    // Arrange
    var operationId = UUID.randomUUID();
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var operation = new Operation();
    var operationDto = new MigrationOperation();
    when(operationsService.retryOperation(operationId)).thenReturn(operation);
    when(mapper.toDto(operation)).thenReturn(operationDto);
    when(props.getChunkRetryingMaxIdsCount()).thenReturn(1000);

    // Act
    var result = migrationsService.retryMarcMigration(operationId, chunkIds);

    // Assert
    assertNotNull(result);
    assertEquals(operationDto, result);
    verify(operationsService).retryOperation(operationId);
    verify(migrationOrchestrator).submitRetryMappingTask(operation, chunkIds);
  }

  @Test
  void retryMarcMigration_InvalidChunkIds_ThrowsApiValidationException() {
    // Act
    Executable executable = () -> migrationsService.retryMarcMigration(UUID.randomUUID(), List.of());

    // Assert
    ApiValidationException exception = assertThrows(ApiValidationException.class, executable);
    assertNotNull(exception);
    verifyNoInteractions(operationsService);
    verifyNoInteractions(migrationOrchestrator);
  }

  @Test
  void retryMarcMigration_OperationNotFound_ThrowsNotFoundException() {
    // Arrange
    var operationId = UUID.randomUUID();
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var maxChunkIdsCount = 1000;
    when(props.getChunkRetryingMaxIdsCount()).thenReturn(maxChunkIdsCount);
    when(operationsService.retryOperation(operationId))
      .thenThrow(new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));

    // Act & Assert
    var exception = assertThrows(NotFoundException.class, () -> migrationsService.retryMarcMigration(
        operationId, chunkIds));
    assertThat(exception).hasMessage(NOT_FOUND_MSG, operationId);
    verify(operationsService).retryOperation(operationId);
    verifyNoInteractions(migrationOrchestrator);
  }

  @Test
  void retryMarcMigration_ChunkIdsExceedMaxSize_ThrowsApiValidationException() {
    // Arrange
    var operationId = UUID.randomUUID();
    var maxChunkIdsCount = 2;
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(props.getChunkRetryingMaxIdsCount()).thenReturn(maxChunkIdsCount);

    // Act & Assert
    var exception = assertThrows(ApiValidationException.class, () -> migrationsService.retryMarcMigration(
        operationId, chunkIds));
    assertNotNull(exception);
    assertThat(exception).hasMessage("The maximum allowed number of chunk IDs is '2', but received '3'.");
    verifyNoInteractions(operationsService);
    verifyNoInteractions(migrationOrchestrator);
  }
}
