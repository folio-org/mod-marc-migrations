package org.folio.marc.migrations.controllers;

import static org.folio.support.TestConstants.USER_ID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.admin.NotFoundException;
import java.util.UUID;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MethodArgumentNotValidException;

@IntegrationTest
class MarcMigrationsControllerIT extends IntegrationTestBase {

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @Test
  void createNewMigration_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
      .andExpect(jsonPath("status", is(MigrationOperationStatus.NEW.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("processedNumOfRecords", is(0)));
  }

  @Test
  void createNewMigration_negative_operationTypeIsNull() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().entityType(EntityType.AUTHORITY);

    // Act & Assert
    tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(is("must not be null")))
      .andExpect(errorTypeMatches(MethodArgumentNotValidException.class))
      .andExpect(errorParameterKeyMatches(is("operationType")))
      .andExpect(errorParameterValueMatches(is("null")));
  }

  @Test
  void createNewMigration_negative_entityTypeIsNull() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING);

    // Act & Assert
    tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(is("must not be null")))
      .andExpect(errorTypeMatches(MethodArgumentNotValidException.class))
      .andExpect(errorParameterKeyMatches(is("entityType")))
      .andExpect(errorParameterValueMatches(is("null")));
  }

  @Test
  void createNewMigration_negative_operationTypeIsUnexpected() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.IMPORT)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(containsString("Unexpected value")))
      .andExpect(errorTypeMatches(ApiValidationException.class))
      .andExpect(errorParameterKeyMatches(is("operationType")))
      .andExpect(errorParameterValueMatches(is("import")));
  }

  @Test
  void createNewMigration_negative_entityTypeIsUnexpected() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(containsString("Unexpected value")))
      .andExpect(errorTypeMatches(ApiValidationException.class))
      .andExpect(errorParameterKeyMatches(is("entityType")))
      .andExpect(errorParameterValueMatches(is("instance")));
  }

  @Test
  void getMigrationById_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
    var postResult = doPost("/marc-migrations", migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    tryGet("/marc-migrations/{operationId}", operationId)
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(operationId.toString())))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
      .andExpect(jsonPath("status", is(MigrationOperationStatus.NEW.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("processedNumOfRecords", is(0)));
  }

  @Test
  void getMigrationById_negative_operationNotExists() throws Exception {
    // Arrange
    var randomId = UUID.randomUUID();

    // Act & Assert
    tryGet("/marc-migrations/{operationId}", randomId)
      .andExpect(status().isNotFound())
      .andExpect(errorMessageMatches(containsString("MARC migration operation was not found")))
      .andExpect(errorTypeMatches(NotFoundException.class));
  }
}
