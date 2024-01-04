package org.folio.marc.migrations.controllers.delegates;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.entlinks.domain.dto.EntityType;
import org.folio.entlinks.domain.dto.MigrationOperation;
import org.folio.entlinks.domain.dto.NewMigrationOperation;
import org.folio.entlinks.domain.dto.OperationType;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.operations.OperationsService;
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
  void createNewMigration_InvalidEntityType_ThrowsApiValidationException() {
    // Arrange
    var invalidOperation = new NewMigrationOperation()
        .operationType(OperationType.REMAPPING)
        .entityType(EntityType.INSTANCE);

    // Act & Assert
    assertThrows(ApiValidationException.class, () -> migrationsService.createNewMigration(invalidOperation));
  }
}
