package org.folio.marc.migrations.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.folio.entlinks.domain.dto.MigrationOperation;
import org.folio.entlinks.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
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
  void createMarcMigrations_ValidInput_ReturnsOkResponse() {
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

}
