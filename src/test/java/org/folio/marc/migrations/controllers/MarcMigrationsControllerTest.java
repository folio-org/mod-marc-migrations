package org.folio.marc.migrations.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
import org.folio.marc.migrations.domain.dto.ErrorReportCollection;
import org.folio.marc.migrations.domain.dto.ErrorReportStatus;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
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

  private static final String TENANT_ID = "tenantId";

  private @Mock MarcMigrationsService migrationsService;
  private @InjectMocks MarcMigrationsController migrationsController;

  @Test
  void createMarcMigrations_ReturnsCreatedResponse() {
    // Arrange
    NewMigrationOperation validOperation = new NewMigrationOperation();
    MigrationOperation result = new MigrationOperation();
    when(migrationsService.createNewMigration(validOperation)).thenReturn(result);

    // Act
    var response = migrationsController.createMarcMigrations(TENANT_ID, validOperation);

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
    var response = migrationsController.getMarcMigrationById(operationId, TENANT_ID);

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
    var response = migrationsController.saveMarcMigration(operationId, TENANT_ID,  saveMigrationOperation);

    // Assert
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(migrationsService).saveMigrationOperation(operationId, saveMigrationOperation);

  }

  @Test
  void createErrorReport_ReturnsNoContentResponse() {
    // Arrange
    UUID operationId = UUID.randomUUID();

    // Act
    var response = migrationsController.createErrorReport(operationId, TENANT_ID);

    // Assert
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(migrationsService).createErrorReport(operationId, TENANT_ID);
  }

  @Test
  void getErrorReportStatus_ReturnsOkResponse() {
    // Arrange
    UUID operationId = UUID.randomUUID();
    ErrorReportStatus status = new ErrorReportStatus();
    when(migrationsService.getErrorReportStatus(operationId)).thenReturn(status);

    // Act
    var response = migrationsController.getErrorReportStatus(operationId, TENANT_ID);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(status, response.getBody());
  }

  @Test
  void getMigrationErrors_ReturnsOkResponse() {
    // Arrange
    UUID operationId = UUID.randomUUID();
    ErrorReportCollection errorReportCollection = new ErrorReportCollection();
    when(migrationsService.getErrorReportEntries(operationId, 0, 100)).thenReturn(errorReportCollection);

    // Act
    var response = migrationsController.getMigrationErrors(operationId, TENANT_ID, 0, 100);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(errorReportCollection, response.getBody());
  }

  @Test
  void retryMarcMigrations_ReturnsCreatedResponse() {
    // Arrange
    UUID operationId = UUID.randomUUID();
    List<UUID> chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    MigrationOperation result = new MigrationOperation();
    when(migrationsService.retryMarcMigration(operationId, chunkIds)).thenReturn(result);

    // Act
    var response = migrationsController.retryMarcMigrations(operationId, TENANT_ID, chunkIds);

    // Assert
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(result, response.getBody());
    verify(migrationsService).retryMarcMigration(operationId, chunkIds);
  }

  @Test
  void retryMarcMigrations_ThrowsException() {
    // Arrange
    UUID operationId = UUID.randomUUID();
    List<UUID> chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(migrationsService.retryMarcMigration(operationId, chunkIds))
        .thenThrow(new IllegalArgumentException("Invalid chunk IDs"));

    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> migrationsController.retryMarcMigrations(operationId, TENANT_ID, chunkIds));
    assertEquals("Invalid chunk IDs", exception.getMessage());
    verify(migrationsService).retryMarcMigration(operationId, chunkIds);
  }
}
