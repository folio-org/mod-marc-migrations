package org.folio.marc.migrations.controllers.delegates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.controllers.delegates.MarcMigrationsService.NOT_FOUND_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.MigrationOrchestrator;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.folio.spring.exception.NotFoundException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MarcMigrationsServiceTest {

  private @Mock MarcMigrationMapper mapper;
  private @Mock OperationsService operationsService;
  private @Mock MigrationOrchestrator migrationOrchestrator;
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
    verify(migrationOrchestrator).submitMappingSaveTask(operation);
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
}
