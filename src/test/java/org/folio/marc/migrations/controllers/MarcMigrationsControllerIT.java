package org.folio.marc.migrations.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.dto.EntityType.AUTHORITY;
import static org.folio.marc.migrations.domain.dto.EntityType.INSTANCE;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING_COMPLETED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING_FAILED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING_COMPLETED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING_FAILED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.NEW;
import static org.folio.marc.migrations.domain.dto.OperationType.REMAPPING;
import static org.folio.marc.migrations.domain.entities.types.StepStatus.COMPLETED;
import static org.folio.support.DatabaseHelper.OPERATION_TABLE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.USER_ID;
import static org.folio.support.TestConstants.marcMigrationEndpoint;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.ErrorReportStatus;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationCollection;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.s3.client.FolioS3Client;
import org.folio.spring.exception.NotFoundException;
import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@IntegrationTest
@DatabaseCleanup(tables = {OPERATION_TABLE})
class MarcMigrationsControllerIT extends IntegrationTestBase {
  private @MockitoSpyBean FolioS3Client s3Client;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll() throws IOException {
    FileUtils.deleteDirectory(new File("test"));
  }

  @Test
  void createNewMigrationAuthority_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(AUTHORITY.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(87))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(87));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
                         && chunk.getEndRecordId() != null
                         && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(COMPLETED)
                        && step.getNumOfErrors().equals(0));

    var fileNames = s3Client.list("operation/" + operationId + "/");
    assertThat(fileNames).hasSize(9);
  }

  @Test
  void createNewMigrationInstance_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.INSTANCE.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(11))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatusOr(DATA_MAPPING, DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(11));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(2)
      .allMatch(chunk -> chunk.getStartRecordId() != null
                         && chunk.getEndRecordId() != null
                         && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(2)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(COMPLETED)
                        && step.getNumOfErrors().equals(0));

    var fileNames = s3Client.list("operation/" + operationId + "/");
    assertThat(fileNames).hasSize(2);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_recordsNotMapped() {
    // Arrange
    var wireMock = okapi.wireMockServer();
    final var stub = wireMock.stubFor(get(urlPathEqualTo("/mapping-metadata/type/marc-authority"))
      .willReturn(notFound()));
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(AUTHORITY.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(87))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(0));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
                         && chunk.getEndRecordId() != null
                         && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_FAILED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(StepStatus.FAILED)
                        && step.getNumOfErrors() >= 7);

    var fileNames = s3Client.list("operation/" + operationId + "/");
    assertThat(fileNames).hasSize(18);
    wireMock.removeStubMapping(stub);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_mappingFailure() {
    // Arrange
    doThrow(IllegalStateException.class).when(s3Client).upload(any(), any());
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(AUTHORITY.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(87))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(87));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(9)
      .allMatch(chunk -> chunk.getStartRecordId() != null
                         && chunk.getEndRecordId() != null
                         && chunk.getStatus().equals(OperationStatusType.DATA_MAPPING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(9)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(COMPLETED)
                        && step.getNumOfErrors() == 0);

    var fileNames = s3Client.list("operation/" + operationId + "/");
    assertThat(fileNames).isEmpty();
  }

  @Test
  void createNewMigration_negative_operationTypeIsNull() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().entityType(AUTHORITY);

    // Act & Assert
    tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("must not be null")))
      .andExpect(errorTypeMatches(MethodArgumentNotValidException.class))
      .andExpect(errorCodeMatches(is("validation_error")))
      .andExpect(errorParameterKeyMatches(is("operationType")))
      .andExpect(errorParameterValueMatches(is("null")));
  }

  @Test
  void createNewMigration_negative_entityTypeIsNull() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(REMAPPING);

    // Act & Assert
    tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("must not be null")))
      .andExpect(errorTypeMatches(MethodArgumentNotValidException.class))
      .andExpect(errorCodeMatches(is("validation_error")))
      .andExpect(errorParameterKeyMatches(is("entityType")))
      .andExpect(errorParameterValueMatches(is("null")));
  }

  @Test
  void createNewMigration_negative_operationTypeIsUnexpected() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.IMPORT)
      .entityType(AUTHORITY);

    // Act & Assert
    tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("Unexpected value")))
      .andExpect(errorTypeMatches(ApiValidationException.class))
      .andExpect(errorCodeMatches(is("validation_error")))
      .andExpect(errorParameterKeyMatches(is("operationType")))
      .andExpect(errorParameterValueMatches(is("import")));
  }

  @Test
  void getMigrationById_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    tryGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(operationId.toString())))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("status",
        oneOf(NEW.getValue(), DATA_MAPPING.getValue(), DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(totalRecords(87))
      .andExpect(jsonPath("mappedNumOfRecords", lessThanOrEqualTo(87)))
      .andExpect(savedRecords(0));

    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
  }

  @Test
  @SneakyThrows
  void getMigrationCollection_positive() {
    var migrationOperation1 = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);
    var migrationOperation2 = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(EntityType.INSTANCE);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation1).andReturn();
    var operationId1 = contentAsObj(postResult, MigrationOperation.class).getId();
    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId1).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
    postResult = doPost(marcMigrationEndpoint(), migrationOperation2).andReturn();
    var operationId2 = contentAsObj(postResult, MigrationOperation.class).getId();
    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId2).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
    postResult = doPost(marcMigrationEndpoint(), migrationOperation1).andReturn();
    var operationId3 = contentAsObj(postResult, MigrationOperation.class).getId();

    var response = tryGet(marcMigrationEndpoint())
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(3)))
      .andExpect(jsonPath("migrationOperations[0].id", is(operationId3.toString())))
      .andExpect(jsonPath("migrationOperations[0].entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("migrationOperations[0].status",
        oneOf(NEW.getValue(), DATA_MAPPING.getValue(), DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[0].totalNumOfRecords", is(87)))
      .andExpect(jsonPath("migrationOperations[0].mappedNumOfRecords", lessThanOrEqualTo(87)))
      .andExpect(jsonPath("migrationOperations[1].id", is(operationId2.toString())))
      .andExpect(jsonPath("migrationOperations[1].entityType", is(INSTANCE.getValue())))
      .andExpect(jsonPath("migrationOperations[1].status", is(DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[1].totalNumOfRecords", is(11)))
      .andExpect(jsonPath("migrationOperations[1].mappedNumOfRecords", is(11)))
      .andExpect(jsonPath("migrationOperations[2].id", is(operationId1.toString())))
      .andExpect(jsonPath("migrationOperations[2].entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("migrationOperations[2].status", is(DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[2].totalNumOfRecords", is(87)))
      .andExpect(jsonPath("migrationOperations[2].mappedNumOfRecords", is(87)))
      .andReturn();

    var dtoCollection = contentAsObj(response, MigrationOperationCollection.class);
    assertThat(dtoCollection.getMigrationOperations())
      .extracting(MigrationOperation::getStartTimeMapping)
      .isSortedAccordingTo(Comparator.reverseOrder());

    tryGet(marcMigrationEndpoint() + "?offset=1")
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(operationId2.toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(operationId1.toString())));

    tryGet(marcMigrationEndpoint() + "?limit=2")
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(operationId3.toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(operationId2.toString())));

    tryGet(marcMigrationEndpoint() + "?entityType=%s".formatted(AUTHORITY))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(2)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(operationId3.toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(operationId1.toString())));
  }

  @Test
  void getMigrationCollection_negative_invalidQueryParams() throws Exception {
    // Act & Assert
    tryGet(marcMigrationEndpoint() + "?entityType=unexpected")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString(
        "Method parameter 'entityType': Failed to convert value of type 'java.lang.String' to required type")))
      .andExpect(errorTypeMatches(MethodArgumentTypeMismatchException.class))
      .andExpect(errorCodeMatches(is("validation_error")));

    tryGet(marcMigrationEndpoint() + "?offset=1001")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("must be less than or equal to 1000")))
      .andExpect(errorTypeMatches(ConstraintViolationException.class))
      .andExpect(errorCodeMatches(is("validation_error")));

    tryGet(marcMigrationEndpoint() + "?limit=1001")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("must be less than or equal to 1000")))
      .andExpect(errorTypeMatches(ConstraintViolationException.class))
      .andExpect(errorCodeMatches(is("validation_error")));
  }

  @Test
  void getMigrationById_negative_operationNotExists() throws Exception {
    // Arrange
    var randomId = UUID.randomUUID();

    // Act & Assert
    tryGet(marcMigrationEndpoint(randomId))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("MARC migration operation was not found")))
      .andExpect(errorTypeMatches(NotFoundException.class))
      .andExpect(errorCodeMatches(is("not_found_error")));
  }

  @Test
  void saveMigrationAuthority_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);

    // Act & Assert
    var result = doPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(87))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId),
      operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(87));

    var saveMigrationOperation = new SaveMigrationOperation()
      .status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(DATA_SAVING_COMPLETED))
        .andExpect(totalRecords(87))
        .andExpect(mappedRecords(87))
        .andExpect(savedRecords(87))
    );

    okapi.wireMockServer().verify(postRequestedFor(urlEqualTo("/authority-storage/authorities/bulk")));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks)
      .hasSize(9)
      .allMatch(chunk -> chunk.getStatus().equals(OperationStatusType.DATA_SAVING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(18)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(COMPLETED)
                        && step.getNumOfErrors().equals(0));
  }

  @Test
  void saveMigrationInstance_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    var result = doPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(11))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId),
      operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = new SaveMigrationOperation()
      .status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(DATA_SAVING_COMPLETED))
        .andExpect(totalRecords(11))
        .andExpect(mappedRecords(11))
        .andExpect(savedRecords(11))
    );

    okapi.wireMockServer().verify(postRequestedFor(urlEqualTo("/instance-storage/instances/bulk")));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks)
      .hasSize(2)
      .allMatch(chunk -> chunk.getStatus().equals(OperationStatusType.DATA_SAVING_COMPLETED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(4)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(COMPLETED)
                        && step.getNumOfErrors().equals(0));
  }

  @Test
  void saveMigrationAuthority_recordsNotMapped() throws Exception {
    // Arrange
    var wireMock = okapi.wireMockServer();
    final var stub = wireMock.stubFor(post(urlPathEqualTo("/authority-storage/authorities/bulk"))
      .willReturn(ResponseDefinitionBuilder.responseDefinition()
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody("{ \"errorsNumber\": \"2\", \"errorRecordsFileName\": \"errorRecordsFileName\", "
                  + "\"errorsFileName\": \"errorsFileName\" }")));
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);

    // Act & Assert
    var result = doPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(87))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId),
      operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(87));

    var saveMigrationOperation = new SaveMigrationOperation()
      .status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(DATA_SAVING_FAILED))
        .andExpect(totalRecords(87))
        .andExpect(mappedRecords(87))
        .andExpect(savedRecords(69))
    );

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks)
      .hasSize(9)
      .allMatch(chunk -> chunk.getStatus().equals(OperationStatusType.DATA_SAVING_FAILED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(18)
      .filteredOn(step -> step.getOperationStep().equals(OperationStep.DATA_SAVING))
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(StepStatus.FAILED)
                        && step.getNumOfErrors().equals(2));

    wireMock.removeStubMapping(stub);
  }

  @Test
  void saveMigrationInstance_recordsNotMapped() throws Exception {
    // Arrange
    var wireMock = okapi.wireMockServer();
    final var stub = wireMock.stubFor(post(urlPathEqualTo("/instance-storage/instances/bulk"))
      .willReturn(ResponseDefinitionBuilder.responseDefinition()
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody("{ \"errorsNumber\": \"2\", \"errorRecordsFileName\": \"errorRecordsFileName\", "
                  + "\"errorsFileName\": \"errorsFileName\" }")));
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    var result = doPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(11))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId),
      operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = new SaveMigrationOperation()
      .status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(DATA_SAVING_FAILED))
        .andExpect(totalRecords(11))
        .andExpect(mappedRecords(11))
        .andExpect(savedRecords(7))
    );

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks)
      .hasSize(2)
      .allMatch(chunk -> chunk.getStatus().equals(OperationStatusType.DATA_SAVING_FAILED));

    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(4)
      .filteredOn(step -> step.getOperationStep().equals(OperationStep.DATA_SAVING))
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(StepStatus.FAILED)
                        && step.getNumOfErrors().equals(2));

    wireMock.removeStubMapping(stub);
  }

  @Test
  void createErrorReport_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    tryPost(marcMigrationEndpoint(operationId) + "/error-report", null)
      .andExpect(status().isNoContent());
  }

  @Test
  void createErrorReport_positive_haveErrors() throws Exception {
    // Arrange
    var errorFile = writeToFile("test.txt", List.of("id1,error1", "id2,error2"));
    var wireMock = okapi.wireMockServer();
    final var stub = wireMock.stubFor(post(urlPathEqualTo("/instance-storage/instances/bulk"))
      .willReturn(ResponseDefinitionBuilder.responseDefinition()
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody("{ \"errorsNumber\": \"2\", \"errorRecordsFileName\": \"errorRecordsFileName\", "
                  + "\"errorsFileName\": \"" + errorFile + "\" }")));
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    var result = doPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(11))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId),
      operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = new SaveMigrationOperation()
      .status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(DATA_SAVING_FAILED))
        .andExpect(totalRecords(11))
        .andExpect(mappedRecords(11))
        .andExpect(savedRecords(7))
    );

    doPost(marcMigrationEndpoint(operationId) + "/error-report", null)
      .andExpect(status().isNoContent());

    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId) + "/error-report/status")
        .andExpect(status().isOk())
        .andExpect(jsonPath("status", is(ErrorReportStatus.StatusEnum.COMPLETED.getValue())))
    );

    doGet(marcMigrationEndpoint(operationId) + "/error-report/errors?limit=2&offset=2")
      .andExpect(status().isOk())
      .andExpect(jsonPath("errorReports", hasSize(2)))
      .andExpect(jsonPath("errorReports[1].recordId", anyOf(is("id1"), is("id2"))))
      .andExpect(jsonPath("errorReports[1].errorMessage", anyOf(is("error1"), is("error2"))));

    wireMock.removeStubMapping(stub);
  }

  @Test
  void getErrorReportStatus_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    var result = tryGet(marcMigrationEndpoint(operationId) + "/error-report/status")
      .andExpect(status().isOk())
      .andExpect(jsonPath("status", notNullValue()))
      .andReturn();

    var errorReportStatus = contentAsObj(result, ErrorReportStatus.class);
    assertThat(errorReportStatus.getStatus()).isNotNull();
  }

  @SneakyThrows
  private String writeToFile(String fileName, List<String> lines) {
    var path = Paths.get("test/" + fileName);
    Files.createDirectories(path.getParent());
    try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      for (var line : lines) {
        writer.write(line);
        writer.newLine();
      }
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    var filePath = "test/" + fileName;
    s3Client.upload(filePath, filePath);
    return filePath;
  }

  private ResultMatcher totalRecords(int expectedCount) {
    return jsonPath("totalNumOfRecords", is(expectedCount));
  }

  private ResultMatcher mappedRecords(int expectedCount) {
    return jsonPath("mappedNumOfRecords", is(expectedCount));
  }

  private ResultMatcher savedRecords(int expectedCount) {
    return jsonPath("savedNumOfRecords", is(expectedCount));
  }

  private ResultMatcher operationStatus(MigrationOperationStatus expectedStatus) {
    return jsonPath("status", is(expectedStatus.getValue()));
  }

  private ResultMatcher operationStatusOr(MigrationOperationStatus expectedStatus,
                                          MigrationOperationStatus otherStatus) {
    return jsonPath("status", anyOf(is(expectedStatus.getValue()), is(otherStatus.getValue())));
  }
}
