package org.folio.marc.migrations.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.USER_ID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.admin.NotFoundException;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.s3.client.FolioS3Client;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

@IntegrationTest
class MarcMigrationsControllerIT extends IntegrationTestBase {
  private @SpyBean FolioS3Client s3Client;

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
    var result = tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
      .andExpect(jsonPath("status", is(MigrationOperationStatus.NEW.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("mappedNumOfRecords", is(0)))
      .andExpect(jsonPath("savedNumOfRecords", is(0)))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);

    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING.getValue())),
      operation.getId());
    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING_COMPLETED.getValue())),
      operation.getId());
    doGet("/marc-migrations/{operationId}", operation.getId())
      .andExpect(status().isOk())
      .andExpect(jsonPath("mappedNumOfRecords", is(87)));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operation.getId());
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
        && chunk.getEndRecordId() != null
        && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operation.getId());
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
        && step.getStepEndTime() != null
        && step.getStepEndTime().after(step.getStepStartTime())
        && step.getStatus().equals(StepStatus.COMPLETED)
        && step.getNumOfErrors().equals(0));

    var fileNames = s3Client.list("operation/" + operation.getId() + "/");
    assertThat(fileNames).hasSize(9);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_recordsNotMapped() {
    // Arrange
    var wireMock = okapi.wireMockServer();
    final var stub = wireMock.stubFor(get(urlPathEqualTo("/mapping-metadata/type/marc-authority"))
      .willReturn(notFound()));
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    var result = tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
      .andExpect(jsonPath("status", is(MigrationOperationStatus.NEW.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("mappedNumOfRecords", is(0)))
      .andExpect(jsonPath("savedNumOfRecords", is(0)))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);

    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING.getValue())),
      operation.getId());
    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING_COMPLETED.getValue())),
      operation.getId());
    doGet("/marc-migrations/{operationId}", operation.getId())
      .andExpect(status().isOk())
      .andExpect(jsonPath("mappedNumOfRecords", is(0)));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operation.getId());
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
        && chunk.getEndRecordId() != null
        && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_FAILED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operation.getId());
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
        && step.getStepEndTime() != null
        && step.getStepEndTime().after(step.getStepStartTime())
        && step.getStatus().equals(StepStatus.FAILED)
        && step.getNumOfErrors() >= 7);

    var fileNames = s3Client.list("operation/" + operation.getId() + "/");
    assertThat(fileNames).hasSize(18);
    wireMock.removeStubMapping(stub);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_mappingFailure() {
    // Arrange
    doThrow(IllegalStateException.class).when(s3Client).upload(any(), any());
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    var result = tryPost("/marc-migrations", migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
      .andExpect(jsonPath("status", is(MigrationOperationStatus.NEW.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("mappedNumOfRecords", is(0)))
      .andExpect(jsonPath("savedNumOfRecords", is(0)))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);

    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING.getValue())),
      operation.getId());
    doGetUntilMatches("/marc-migrations/{operationId}",
      jsonPath("status", is(MigrationOperationStatus.DATA_MAPPING_FAILED.getValue())),
      operation.getId());
    doGet("/marc-migrations/{operationId}", operation.getId())
      .andExpect(status().isOk())
      .andExpect(jsonPath("mappedNumOfRecords", is(87)));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operation.getId());
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
        && chunk.getEndRecordId() != null
        && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operation.getId());
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
        && step.getStepEndTime() != null
        && step.getStepEndTime().after(step.getStepStartTime())
        && step.getStatus().equals(StepStatus.COMPLETED)
        && step.getNumOfErrors() == 0);

    var fileNames = s3Client.list("operation/" + operation.getId() + "/");
    assertThat(fileNames).isEmpty();
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
      .andExpect(jsonPath("status", oneOf(MigrationOperationStatus.NEW.getValue(),
        MigrationOperationStatus.DATA_MAPPING.getValue(),
        MigrationOperationStatus.DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("totalNumOfRecords", is(87)))
      .andExpect(jsonPath("mappedNumOfRecords", lessThanOrEqualTo(87)))
      .andExpect(jsonPath("savedNumOfRecords", is(0)));
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
