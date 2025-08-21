package org.folio.marc.migrations.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING_COMPLETED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_MAPPING_FAILED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING_COMPLETED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.DATA_SAVING_FAILED;
import static org.folio.marc.migrations.domain.dto.MigrationOperationStatus.NEW;
import static org.folio.marc.migrations.domain.entities.types.StepStatus.COMPLETED;
import static org.folio.support.DatabaseHelper.OPERATION_TABLE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.USER_ID;
import static org.folio.support.TestConstants.marcMigrationEndpoint;
import static org.folio.support.TestConstants.retryMarcMigrationEndpoint;
import static org.folio.support.TestConstants.retrySaveMarcMigrationEndpoint;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.admin.NotFoundException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.ErrorReportStatus;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.s3.client.FolioS3Client;
import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.MethodArgumentNotValidException;

@IntegrationTest
@DatabaseCleanup(tables = {OPERATION_TABLE})
class MarcMigrationsControllerIT extends IntegrationTestBase {
  private @SpyBean FolioS3Client s3Client;
  private @SpyBean ChunkStepJdbcService chunkStepJdbcService;
  private @SpyBean BulkStorageService bulkStorageService;

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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.INSTANCE);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

    // Act & Assert
    var result = tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
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
    var migrationOperation = new NewMigrationOperation().entityType(EntityType.AUTHORITY);

    // Act & Assert
    tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isUnprocessableEntity())
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
    tryPost(marcMigrationEndpoint(), migrationOperation)
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
    tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(containsString("Unexpected value")))
      .andExpect(errorTypeMatches(ApiValidationException.class))
      .andExpect(errorParameterKeyMatches(is("operationType")))
      .andExpect(errorParameterValueMatches(is("import")));
  }

  @Test
  void getMigrationById_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    tryGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(operationId.toString())))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(EntityType.AUTHORITY.getValue())))
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
  void getMigrationById_negative_operationNotExists() throws Exception {
    // Arrange
    var randomId = UUID.randomUUID();

    // Act & Assert
    tryGet(marcMigrationEndpoint(randomId))
      .andExpect(status().isNotFound())
      .andExpect(errorMessageMatches(containsString("MARC migration operation was not found")))
      .andExpect(errorTypeMatches(NotFoundException.class));
  }

  @Test
  void saveMigrationAuthority_positive() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation()
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

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
      .operationType(OperationType.REMAPPING)
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);

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
      .operationType(OperationType.REMAPPING)
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
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
      .operationType(OperationType.REMAPPING)
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
      .operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
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
  @ParameterizedTest
  @MethodSource("provideEntityTypesAndChunkSizes")
  void retryMarcMigrations_positive(EntityType entityType, int totalRecords, int expectedChunkSize) {
    // Arrange
    doThrow(IllegalStateException.class).when(chunkStepJdbcService)
      .createChunkStep(any());
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
      .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(entityType.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
      .andExpect(mappedRecords(0));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);
    var chunksRetrying = chunks.stream()
      .map(OperationChunk::getId)
      .toList();

    // retry migration operation
    var retryResult = tryPost(retryMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(entityType.getValue())))
      .andExpect(operationStatus(DATA_MAPPING))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var retryOperation = contentAsObj(retryResult, MigrationOperation.class);
    var retryOperationId = retryOperation.getId();

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(retryOperationId)).andExpect(status().isOk())
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(0));

    // save migration operation
    var saveMigrationOperation = new SaveMigrationOperation().status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_COMPLETED))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(totalRecords)));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesAndChunkSizes")
  void retryMarcMigrations_positive_retryMappingForCompletedChunk(EntityType entityType, int totalRecords,
                                                                  int expectedChunkSize) {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
        .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
        .andExpect(jsonPath("id", notNullValue(UUID.class)))
        .andExpect(jsonPath("userId", is(USER_ID)))
        .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
        .andExpect(jsonPath("entityType", is(entityType.getValue())))
        .andExpect(operationStatus(NEW))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(0))
        .andExpect(savedRecords(0))
        .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
        .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    // retry migration operation for DATA_MAPPING_COMPLETED chunk
    var retryResult = tryPost(retryMarcMigrationEndpoint(operationId), List.of(chunks.get(0).getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("id", notNullValue(UUID.class)))
        .andExpect(jsonPath("userId", is(USER_ID)))
        .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
        .andExpect(jsonPath("entityType", is(entityType.getValue())))
        .andExpect(operationStatus(DATA_MAPPING))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(0))
        .andReturn();
    var retryOperation = contentAsObj(retryResult, MigrationOperation.class);
    var retryOperationId = retryOperation.getId();

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(retryOperationId)).andExpect(status().isOk())
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(0));

    // save migration operation
    var saveMigrationOperation = new SaveMigrationOperation().status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_COMPLETED))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(totalRecords)));
  }

  @Test
  void retryMarcMigrations_negative_emptyChunkIds() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
      .entityType(EntityType.AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = contentAsObj(postResult, MigrationOperation.class).getId();

    // Act & Assert
    tryPost(retryMarcMigrationEndpoint(operationId), List.of()).andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatches(containsString("no chunk IDs provided")));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesData")
  void retrySaveMarcMigrations_positive(EntityType entityType, int totalRecords, int expectedChunkSize,
                                        int savedRecords, String bulkUrl) {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
      .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(entityType.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
      .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    var errorChunk = chunks.get(0);
    var fileNames = s3Client.list("operation/" + operationId + "/" + errorChunk.getId() + "_entity");
    var entityList = readFile(fileNames.get(0));
    var errorFile = writeToFile("test.txt", List.of(entityList.get(0)));

    var wireMock = okapi.wireMockServer();
    var stub = wireMock.stubFor(post(urlPathMatching(bulkUrl)).withRequestBody(containing(errorChunk.getId()
      .toString()))
      .willReturn(ResponseDefinitionBuilder.responseDefinition()
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody("{ \"errorsNumber\": \"1\", \"errorRecordsFileName\": \"errorRecordsFileName\", "
            + "\"errorsFileName\": \"" + errorFile + "\" }")));

    // save migration operation
    var saveMigrationOperation = new SaveMigrationOperation().status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_FAILED))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(savedRecords)));

    wireMock.removeStubMapping(stub);

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), List.of(errorChunk.getId())).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_COMPLETED))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(totalRecords)));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesData")
  void retrySaveMarcMigrations_positive_retrySavingForAllRecordsInChunk(EntityType entityType, int totalRecords,
                                        int expectedChunkSize, int savedRecords, String bulkUrl) {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
        .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
        .andExpect(jsonPath("id", notNullValue(UUID.class)))
        .andExpect(jsonPath("userId", is(USER_ID)))
        .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
        .andExpect(jsonPath("entityType", is(entityType.getValue())))
        .andExpect(operationStatus(NEW))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(0))
        .andExpect(savedRecords(0))
        .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
        .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    var errorChunk = chunks.get(0);
    var fileNames = s3Client.list("operation/" + operationId + "/" + errorChunk.getId() + "_entity");
    var entityList = readFile(fileNames.get(0));
    var errorFile = writeToFile("test.txt", List.of(entityList.get(0)));

    var wireMock = okapi.wireMockServer();
    var stub = wireMock.stubFor(post(urlPathMatching(bulkUrl)).withRequestBody(containing(errorChunk.getId()
            .toString()))
        .willReturn(ResponseDefinitionBuilder.responseDefinition()
            .withHeader("Content-Type", "application/json;charset=UTF-8")
            .withBody("{ \"errorsNumber\": \"1\", \"errorRecordsFileName\": \"errorRecordsFileName\", "
                + "\"errorsFileName\": \"" + errorFile + "\" }")));

    // save migration operation
    var saveMigrationOperation = new SaveMigrationOperation().status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_FAILED))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(savedRecords)));

    wireMock.removeStubMapping(stub);

    // retry remapping for DATA_SAVING_FAILED chunk
    var retryResult = tryPost(retryMarcMigrationEndpoint(operationId), List.of(errorChunk.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("id", notNullValue(UUID.class)))
        .andExpect(jsonPath("userId", is(USER_ID)))
        .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
        .andExpect(jsonPath("entityType", is(entityType.getValue())))
        .andExpect(operationStatus(DATA_MAPPING))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(savedRecords))
        .andReturn();
    var retryOperation = contentAsObj(retryResult, MigrationOperation.class);
    var retryOperationId = retryOperation.getId();

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(retryOperationId)).andExpect(status().isOk())
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(savedRecords));

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), List.of(errorChunk.getId())).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_COMPLETED))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(totalRecords)));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesDataForNegativeCase")
  void retrySaveMarcMigrations_negative_bulkClientReturnNullResponse(EntityType entityType, int totalRecords,
                                                                     int expectedChunkSize, int savedRecords) {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
      .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(entityType.getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(savedRecords))
      .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
      .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    when(bulkStorageService.saveEntities(any(), any(), any())).thenReturn(null);

    // save migration operation
    var saveMigrationOperation = new SaveMigrationOperation().status(DATA_SAVING);
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_FAILED))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(savedRecords)));

    var chunksRetrying = chunks.stream()
      .map(OperationChunk::getId)
      .toList();

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_FAILED))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(totalRecords))
      .andExpect(savedRecords(savedRecords)));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesAndChunkSizes")
  void retrySaveMarcMigrations_positive_whenChunkStepNotExist(EntityType entityType, int totalRecords,
                                                              int expectedChunkSize) {
    // Arrange
    var migrationOperation = new NewMigrationOperation().operationType(OperationType.REMAPPING)
        .entityType(entityType);

    // Act & Assert
    // create migration operation
    var result = tryPost(marcMigrationEndpoint(), migrationOperation).andExpect(status().isCreated())
        .andExpect(jsonPath("id", notNullValue(UUID.class)))
        .andExpect(jsonPath("userId", is(USER_ID)))
        .andExpect(jsonPath("operationType", is(OperationType.REMAPPING.getValue())))
        .andExpect(jsonPath("entityType", is(entityType.getValue())))
        .andExpect(operationStatus(NEW))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(0))
        .andExpect(savedRecords(0))
        .andReturn();
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));

    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
        .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);
    var chunksRetrying = chunks.stream()
        .map(OperationChunk::getId)
        .toList();

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isNoContent());
    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId)).andExpect(operationStatus(DATA_SAVING_COMPLETED))
        .andExpect(totalRecords(totalRecords))
        .andExpect(mappedRecords(totalRecords))
        .andExpect(savedRecords(totalRecords)));
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

  private static Stream<Arguments> provideEntityTypesAndChunkSizes() {
    return Stream.of(Arguments.of(EntityType.AUTHORITY, 87, 9), Arguments.of(EntityType.INSTANCE, 11, 2));
  }

  private static Stream<Arguments> provideEntityTypesData() {
    return Stream.of(
        Arguments.of(EntityType.AUTHORITY, 87, 9, 86, "/authority-storage/authorities/bulk"),
        Arguments.of(EntityType.INSTANCE, 11, 2, 10, "/instance-storage/instances/bulk"));
  }

  private static Stream<Arguments> provideEntityTypesDataForNegativeCase() {
    return Stream.of(
        Arguments.of(EntityType.AUTHORITY, 87, 9, 0),
        Arguments.of(EntityType.INSTANCE, 11, 2, 0));
  }

  @SneakyThrows
  private List<String> readFile(String remotePath) {
    try (var inputStream = s3Client.read(remotePath);
         var reader = new BufferedReader(new InputStreamReader(inputStream))) {
      return reader.lines()
        .toList();
    }
  }
}
