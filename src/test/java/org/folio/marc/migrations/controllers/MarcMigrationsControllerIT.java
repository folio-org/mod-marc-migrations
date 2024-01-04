package org.folio.marc.migrations.controllers;

import static org.folio.support.TestConstants.USER_ID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.folio.entlinks.domain.dto.EntityType;
import org.folio.entlinks.domain.dto.MigrationOperationStatus;
import org.folio.entlinks.domain.dto.NewMigrationOperation;
import org.folio.entlinks.domain.dto.OperationType;
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
      .andExpect(jsonPath("processedNumOfRecords", is(0)))
      .andExpect(jsonPath("id", notNullValue(UUID.class)));
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
}
