package org.folio.marc.migrations.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationCollection;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MarcMigrationsControllerTest {

  private @Mock MarcMigrationsService migrationsService;
  private @InjectMocks MarcMigrationsController migrationsController;

  @Test
  void createMarcMigrations_ReturnsCreatedResponse() {
    // Arrange
    NewMigrationOperation validOperation = new NewMigrationOperation();
    MigrationOperation result = new MigrationOperation();
    when(migrationsService.createNewMigration(validOperation)).thenReturn(result);

    // Act
    var response = migrationsController.createMarcMigrations("tenantId", validOperation);

    // Assert
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(result, response.getBody());

  }

  @Test
  void getMarcMigrationById_ReturnsOkResponse() {
    // Arrange
    MigrationOperation result = new MigrationOperation();
    UUID operationId = UUID.randomUUID();
    when(migrationsService.getMarcMigrationById(operationId)).thenReturn(result);

    // Act
    var response = migrationsController.getMarcMigrationById(operationId, "tenantId");

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(result, response.getBody());
  }

  @Test
  void getMarcMigrations_ReturnsOkResponse() {
    // Arrange
    MigrationOperationCollection result = new MigrationOperationCollection()
        .migrationOperations(List.of(new MigrationOperation()))
        .totalRecords(1);
    when(migrationsService.getMarcMigrations(0, 100, EntityType.AUTHORITY)).thenReturn(result);

    // Act
    var response = migrationsController.getMarcMigrations(0, 100, EntityType.AUTHORITY);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(result, response.getBody());
  }

  @Test
  void saveMarcMigrations_ReturnsNoContentResponse() {
    // Arrange
    var operationId = UUID.randomUUID();
    var saveMigrationOperation = new SaveMigrationOperation();

    // Act
    var response = migrationsController.saveMarcMigration(operationId, saveMigrationOperation);

    // Assert
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(migrationsService).saveMigrationOperation(operationId, saveMigrationOperation);

  }
}
