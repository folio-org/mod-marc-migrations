package org.folio.marc.migrations.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
import static org.folio.marc.migrations.domain.entities.types.StepStatus.FAILED;
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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import jakarta.validation.ConstraintViolationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.s3.client.FolioS3Client;
import org.folio.spring.exception.NotFoundException;
import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@EnableJdbcJobRepository
@IntegrationTest
@DatabaseCleanup(tables = {OPERATION_TABLE})
class MarcMigrationsControllerIT extends IntegrationTestBase {

  public static final String AUTHORITIES_BULK_ENDPOINT = "/authority-storage/authorities/bulk";
  public static final String INSTANCES_BULK_ENDPOINT = "/instance-storage/instances/bulk";
  private static final String OPERATION_PATH = "mod-marc-migrations/operation/%s/";
  private static final String UNKNOWN_RECORD_ID = "<unknown>";
  private @MockitoSpyBean FolioS3Client s3Client;
  private @MockitoSpyBean ChunkStepJdbcService chunkStepJdbcService;
  private @MockitoSpyBean BulkStorageService bulkStorageService;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterEach
  void afterEach() throws IOException {
    FileUtils.deleteDirectory(new File("test"));
    okapi.wireMockServer().resetAll();
  }

  @Test
  void createNewMigrationAuthority_positive() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(AUTHORITY);

    // Act & Assert
    var expectedTotalRecords = 81;
    var result = postAndAssertNewOperation(migrationOperation, expectedTotalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(expectedTotalRecords));

    assertChunksInDatabase(operationId, 9, OperationStatusType.DATA_MAPPING_COMPLETED);
    assertChunkStepsInDatabase(operationId, 9, COMPLETED);

    var fileNames = s3Client.list(OPERATION_PATH.formatted(operationId));
    assertThat(fileNames).hasSize(9);
  }

  @Test
  void createNewMigrationInstance_positive() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(EntityType.INSTANCE);

    // Act & Assert
    var expectedTotalRecords = 11;
    var result = postAndAssertNewOperation(migrationOperation, expectedTotalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatusOr(DATA_MAPPING, DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(expectedTotalRecords));

    assertChunksInDatabase(operationId, 2, OperationStatusType.DATA_MAPPING_COMPLETED);
    assertChunkStepsInDatabase(operationId, 2, COMPLETED);

    var fileNames = s3Client.list(OPERATION_PATH.formatted(operationId));
    assertThat(fileNames).hasSize(2);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_recordsNotMapped() {
    // Arrange
    final var stub = okapi.wireMockServer().stubFor(get(urlPathEqualTo("/mapping-metadata/type/marc-authority"))
      .willReturn(notFound()));
    var migrationOperation = getNewMigrationOperation(AUTHORITY);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 81);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(0));

    assertChunksInDatabase(operationId, 9, OperationStatusType.DATA_MAPPING_FAILED);
    assertChunkStepsInDatabase(operationId, 9, StepStatus.FAILED);

    var fileNames = s3Client.list(OPERATION_PATH.formatted(operationId));
    assertThat(fileNames).hasSize(18);
    okapi.wireMockServer().removeStubMapping(stub);
  }

  @Test
  @SneakyThrows
  void createNewMigration_positive_mappingFailure() {
    // Arrange
    doThrow(IllegalStateException.class).when(s3Client).upload(any(), any());
    var migrationOperation = getNewMigrationOperation(AUTHORITY);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 81);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING));
    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    doGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(mappedRecords(81));

    assertChunksInDatabase(operationId, 9, OperationStatusType.DATA_MAPPING_COMPLETED);
    assertChunkStepsInDatabase(operationId, 9, StepStatus.COMPLETED);

    var fileNames = s3Client.list("operation/" + operationId + "/");
    assertThat(fileNames).isEmpty();
  }

  @Test
  void createNewMigration_negative_operationTypeIsNull() throws Exception {
    // Arrange
    var migrationOperation = new NewMigrationOperation().entityType(AUTHORITY);

    // Act & Assert
    tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isUnprocessableContent())
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
      .andExpect(status().isUnprocessableContent())
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
      .andExpect(status().isUnprocessableContent())
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
    var migrationOperation = getNewMigrationOperation(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = getOperationId(postResult);

    // Act & Assert
    tryGet(marcMigrationEndpoint(operationId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(operationId.toString())))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("status",
        oneOf(NEW.getValue(), DATA_MAPPING.getValue(), DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(totalRecords(81))
      .andExpect(jsonPath("mappedNumOfRecords", lessThanOrEqualTo(81)))
      .andExpect(savedRecords(0));

    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
  }

  @Test
  @SneakyThrows
  void getMigrationCollection_positive() {
    var result = prepare3MigrationCollection();

    var response = tryGet(marcMigrationEndpoint())
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(3)))
      .andExpect(jsonPath("migrationOperations[0].id", is(result.operationId3.toString())))
      .andExpect(jsonPath("migrationOperations[0].entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("migrationOperations[0].status",
        oneOf(NEW.getValue(), DATA_MAPPING.getValue(), DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[0].totalNumOfRecords", is(81)))
      .andExpect(jsonPath("migrationOperations[0].mappedNumOfRecords", lessThanOrEqualTo(81)))
      .andExpect(jsonPath("migrationOperations[1].id", is(result.operationId2.toString())))
      .andExpect(jsonPath("migrationOperations[1].entityType", is(INSTANCE.getValue())))
      .andExpect(jsonPath("migrationOperations[1].status", is(DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[1].totalNumOfRecords", is(11)))
      .andExpect(jsonPath("migrationOperations[1].mappedNumOfRecords", is(11)))
      .andExpect(jsonPath("migrationOperations[2].id", is(result.operationId1.toString())))
      .andExpect(jsonPath("migrationOperations[2].entityType", is(AUTHORITY.getValue())))
      .andExpect(jsonPath("migrationOperations[2].status", is(DATA_MAPPING_COMPLETED.getValue())))
      .andExpect(jsonPath("migrationOperations[2].totalNumOfRecords", is(81)))
      .andExpect(jsonPath("migrationOperations[2].mappedNumOfRecords", is(81)))
      .andReturn();

    assertExpectedSorting(response);
  }

  @Test
  @SneakyThrows
  void getMigrationCollection_positive_withPaginationAndFiltering() {
    var result = prepare3MigrationCollection();

    tryGet(marcMigrationEndpoint() + "?offset=1")
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(result.operationId2().toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(result.operationId1().toString())));

    tryGet(marcMigrationEndpoint() + "?limit=2")
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(result.operationId3().toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(result.operationId2().toString())));

    tryGet(marcMigrationEndpoint() + "?entityType=%s".formatted(AUTHORITY))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(2)))
      .andExpect(jsonPath("migrationOperations", hasSize(2)))
      .andExpect(jsonPath("migrationOperations[0].id", is(result.operationId3().toString())))
      .andExpect(jsonPath("migrationOperations[1].id", is(result.operationId1().toString())));
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
    var migrationOperation = getNewMigrationOperation(AUTHORITY);
    var expectedTotalRecords = 81;

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, expectedTotalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(expectedTotalRecords));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, expectedTotalRecords,
      expectedTotalRecords, expectedTotalRecords);

    okapi.wireMockServer().verify(postRequestedFor(urlEqualTo(AUTHORITIES_BULK_ENDPOINT)));

    assertChunksInDatabase(operationId, 9, OperationStatusType.DATA_SAVING_COMPLETED);
    assertChunkStepsInDatabase(operationId, 18, COMPLETED);
  }

  @Test
  void saveMigrationInstance_positive() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(EntityType.INSTANCE);
    var expectedTotalRecords = 11;

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, expectedTotalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(expectedTotalRecords));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation)
      .andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, expectedTotalRecords,
      expectedTotalRecords, expectedTotalRecords);

    okapi.wireMockServer().verify(postRequestedFor(urlEqualTo(INSTANCES_BULK_ENDPOINT)));

    assertChunksInDatabase(operationId, 2, OperationStatusType.DATA_SAVING_COMPLETED);
    assertChunkStepsInDatabase(operationId, 4, COMPLETED);
  }

  @Test
  void saveMigrationAuthority_recordsNotMapped() throws Exception {
    // Arrange
    final var stub = mockFailOnSaveEndpoint(AUTHORITIES_BULK_ENDPOINT, 2, "errorsFileName");
    var migrationOperation = getNewMigrationOperation(AUTHORITY);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 81);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(81));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, 81, 81, 63);

    assertChunksInDatabase(operationId, 9, OperationStatusType.DATA_SAVING_FAILED);
    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(18)
      .filteredOn(step -> step.getOperationStep().equals(OperationStep.DATA_SAVING))
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(StepStatus.FAILED)
                        && step.getNumOfErrors().equals(2));

    okapi.wireMockServer().removeStubMapping(stub);
  }

  @Test
  void saveMigrationInstance_recordsNotMapped() throws Exception {
    // Arrange
    final var stub = mockFailOnSaveEndpoint(INSTANCES_BULK_ENDPOINT, 2, "errorsFileName");
    var migrationOperation = getNewMigrationOperation(EntityType.INSTANCE);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 11);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, 11, 11, 7);

    assertChunksInDatabase(operationId, 2, OperationStatusType.DATA_SAVING_FAILED);
    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(4)
      .filteredOn(step -> step.getOperationStep().equals(OperationStep.DATA_SAVING))
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(StepStatus.FAILED)
                        && step.getNumOfErrors().equals(2));

    okapi.wireMockServer().removeStubMapping(stub);
  }

  @Test
  void createErrorReport_positive() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = getOperationId(postResult);

    // Act & Assert
    tryPost(marcMigrationEndpoint(operationId) + "/error-report", null)
      .andExpect(status().isNoContent());
  }

  @Test
  void createErrorReport_positive_haveErrors_without_id() throws Exception {
    // Arrange
    var errorFile = writeToFile(List.of("error1", "error2"));
    final var stub = mockFailOnSaveEndpoint(INSTANCES_BULK_ENDPOINT, 2, errorFile);
    var migrationOperation = getNewMigrationOperation(EntityType.INSTANCE);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 11);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, 11, 11, 7);

    doPost(marcMigrationEndpoint(operationId) + "/error-report", null).andExpect(status().isNoContent());

    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId) + "/error-report/status")
      .andExpect(status().isOk())
      .andExpect(jsonPath("status", is(ErrorReportStatus.StatusEnum.COMPLETED.getValue()))));

    doGet(marcMigrationEndpoint(operationId) + "/error-report/errors?limit=2&offset=2").andExpect(status().isOk())
      .andExpect(jsonPath("errorReports", hasSize(2)))
      .andExpect(jsonPath("errorReports[1].recordId", anyOf(is(UNKNOWN_RECORD_ID), is(UNKNOWN_RECORD_ID))))
      .andExpect(jsonPath("errorReports[1].errorMessage", anyOf(is("error1"), is("error2"))));

    okapi.wireMockServer().removeStubMapping(stub);
  }

  @Test
  void createErrorReport_positive_haveErrors() throws Exception {
    // Arrange
    var recordId1 = UUID.randomUUID().toString();
    var recordId2 = UUID.randomUUID().toString();
    var errorFile = writeToFile(List.of(recordId1 + ",error1", recordId2 + ",error2"));
    final var stub = mockFailOnSaveEndpoint(INSTANCES_BULK_ENDPOINT, 2, errorFile);
    var migrationOperation = getNewMigrationOperation(EntityType.INSTANCE);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, 11);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGetUntilMatches(marcMigrationEndpoint(operationId), mappedRecords(11));

    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, 11, 11, 7);

    doPost(marcMigrationEndpoint(operationId) + "/error-report", null).andExpect(status().isNoContent());

    awaitUntilAsserted(() -> doGet(marcMigrationEndpoint(operationId) + "/error-report/status")
      .andExpect(status().isOk())
      .andExpect(jsonPath("status", is(ErrorReportStatus.StatusEnum.COMPLETED.getValue()))));

    doGet(marcMigrationEndpoint(operationId) + "/error-report/errors?limit=2&offset=2").andExpect(status().isOk())
      .andExpect(jsonPath("errorReports", hasSize(2)))
      .andExpect(jsonPath("errorReports[1].recordId", anyOf(is(recordId1), is(recordId2))))
      .andExpect(jsonPath("errorReports[1].errorMessage", anyOf(is("error1"), is("error2"))));

    okapi.wireMockServer().removeStubMapping(stub);
  }

  @Test
  void getErrorReportStatus_positive() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = getOperationId(postResult);

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
    doThrow(IllegalStateException.class).when(chunkStepJdbcService).createChunkStep(any());
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_FAILED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk()).andExpect(mappedRecords(0));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);
    var chunksRetrying = chunks.stream()
      .map(OperationChunk::getId)
      .toList();

    // retry migration operation
    var retryResult = postAndAssertRetryOperation(entityType, totalRecords, operationId, chunksRetrying, 0, 0);
    var retryOperationId = getOperationId(retryResult);

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    assertOperationRecordCounts(retryOperationId, totalRecords, 0, totalRecords);

    // save migration operation
    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, totalRecords, totalRecords, totalRecords);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesAndChunkSizes")
  void retryMarcMigrations_positive_retryMappingForCompletedChunk(EntityType entityType, int totalRecords,
                                                                  int expectedChunkSize) {
    // Arrange
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    // create migration operation
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk())
      .andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    // retry migration operation for DATA_MAPPING_COMPLETED chunk
    var retryResult = postAndAssertRetryOperation(entityType, totalRecords, operationId,
      List.of(chunks.getFirst().getId()), totalRecords, 0);

    var retryOperation = contentAsObj(retryResult, MigrationOperation.class);
    var retryOperationId = retryOperation.getId();

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    assertOperationRecordCounts(retryOperationId, totalRecords, 0, totalRecords);

    // save migration operation
    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, totalRecords, totalRecords, totalRecords);
  }

  @Test
  void retryMarcMigrations_negative_emptyChunkIds() throws Exception {
    // Arrange
    var migrationOperation = getNewMigrationOperation(AUTHORITY);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation).andReturn();
    var operationId = getOperationId(postResult);

    // Act & Assert
    tryPost(retryMarcMigrationEndpoint(operationId), List.of()).andExpect(status().isUnprocessableContent())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(errorMessageMatches(containsString("no chunk IDs provided")));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesData")
  void retrySaveMarcMigrations_positive(EntityType entityType, int totalRecords, int expectedChunkSize,
                                        int savedRecords, String bulkUrl) {
    // Arrange
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk()).andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    var errorChunk = chunks.getFirst();
    var errorFile = getErrorFile(operationId, errorChunk);
    var stub = mockFailOnSaveEndpoint(bulkUrl, 1, errorFile, containing(errorChunk.getId().toString()));

    // save migration operation
    var saveMigrationOperation = getSaveMigrationOperation();
    tryPut(marcMigrationEndpoint(operationId), saveMigrationOperation).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, totalRecords, totalRecords, savedRecords);

    okapi.wireMockServer().removeStubMapping(stub);

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), List.of(errorChunk.getId())).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, totalRecords, totalRecords, totalRecords);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesData")
  void retrySaveMarcMigrations_positive_retrySavingForAllRecordsInChunk(EntityType entityType, int totalRecords,
                                                                        int expectedChunkSize, int savedRecords,
                                                                        String bulkUrl) {
    // Arrange
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk()).andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    var errorChunk = chunks.getFirst();
    var errorFile = getErrorFile(operationId, errorChunk);
    var stub = mockFailOnSaveEndpoint(bulkUrl, 1, errorFile, containing(errorChunk.getId().toString()));

    // save migration operation
    tryPut(marcMigrationEndpoint(operationId), getSaveMigrationOperation()).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, totalRecords, totalRecords, savedRecords);

    okapi.wireMockServer().removeStubMapping(stub);

    // retry remapping for DATA_SAVING_FAILED chunk
    var retryResult = postAndAssertRetryOperation(entityType, totalRecords, operationId, List.of(errorChunk.getId()),
      totalRecords, savedRecords);
    var retryOperationId = getOperationId(retryResult);

    doGetUntilMatches(marcMigrationEndpoint(retryOperationId), operationStatus(DATA_MAPPING_COMPLETED));
    assertOperationRecordCounts(retryOperationId, totalRecords, savedRecords, totalRecords);

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), List.of(errorChunk.getId())).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, totalRecords, totalRecords, totalRecords);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesDataForNegativeCase")
  void retrySaveMarcMigrations_negative_bulkClientReturnNullResponse(EntityType entityType, int totalRecords,
                                                                     int expectedChunkSize, int savedRecords) {
    // Arrange
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operationId = getOperationId(result);

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    Mockito.reset(chunkStepJdbcService);
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk()).andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);

    when(bulkStorageService.saveEntities(any(), any(), any())).thenReturn(null);

    // save migration operation
    tryPut(marcMigrationEndpoint(operationId), getSaveMigrationOperation()).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, totalRecords, totalRecords, savedRecords);

    var chunksRetrying = chunks.stream()
      .map(OperationChunk::getId)
      .toList();

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_FAILED, totalRecords, totalRecords, savedRecords);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("provideEntityTypesAndChunkSizes")
  void retrySaveMarcMigrations_positive_whenChunkStepNotExist(EntityType entityType, int totalRecords,
                                                              int expectedChunkSize) {
    // Arrange
    var migrationOperation = getNewMigrationOperation(entityType);

    // Act & Assert
    // create migration operation
    var result = postAndAssertNewOperation(migrationOperation, totalRecords);
    var operation = contentAsObj(result, MigrationOperation.class);
    var operationId = operation.getId();

    doGetUntilMatches(marcMigrationEndpoint(operationId), operationStatus(DATA_MAPPING_COMPLETED));
    doGet(marcMigrationEndpoint(operationId)).andExpect(status().isOk()).andExpect(mappedRecords(totalRecords));

    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedChunkSize);
    var chunksRetrying = chunks.stream()
      .map(OperationChunk::getId)
      .toList();

    // retry saving migration operation
    tryPost(retrySaveMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isNoContent());
    awaitUntilMigrationCompleted(operationId, DATA_SAVING_COMPLETED, totalRecords, totalRecords, totalRecords);
  }

  private SaveMigrationOperation getSaveMigrationOperation() {
    return new SaveMigrationOperation().status(DATA_SAVING);
  }

  private void assertOperationRecordCounts(UUID retryOperationId, int totalRecords, int savedRecords, int mappedRecords)
    throws Exception {
    doGet(marcMigrationEndpoint(retryOperationId)).andExpect(status().isOk())
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(mappedRecords))
      .andExpect(savedRecords(savedRecords));
  }

  private String getErrorFile(UUID operationId, OperationChunk errorChunk) {
    var fileNames = s3Client.list(OPERATION_PATH.formatted(operationId) + errorChunk.getId() + "_entity");
    var entityList = readFile(fileNames.getFirst());
    return writeToFile(List.of(entityList.getFirst()));
  }

  private UUID getOperationId(MvcResult result) {
    return contentAsObj(result, MigrationOperation.class).getId();
  }

  private MvcResult postAndAssertRetryOperation(EntityType entityType, int totalRecords, UUID operationId,
                                                List<UUID> chunksRetrying, int mappedRecords, int savedRecords)
    throws Exception {
    return tryPost(retryMarcMigrationEndpoint(operationId), chunksRetrying).andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(entityType.getValue())))
      .andExpect(operationStatus(DATA_MAPPING))
      .andExpect(totalRecords(totalRecords))
      .andExpect(mappedRecords(mappedRecords))
      .andExpect(savedRecords(savedRecords))
      .andReturn();
  }

  private NewMigrationOperation getNewMigrationOperation(EntityType entityType) {
    return new NewMigrationOperation()
      .operationType(REMAPPING)
      .entityType(entityType);
  }

  private StubMapping mockFailOnSaveEndpoint(String testUrl, int errorsNumber,
                                             String errorsFileName, StringValuePattern bodyPattern) {
    var mappingBuilder = post(urlPathEqualTo(testUrl));
    if (bodyPattern != null) {
      mappingBuilder.withRequestBody(bodyPattern);
    }
    return okapi.wireMockServer().stubFor(mappingBuilder
      .willReturn(ResponseDefinitionBuilder.responseDefinition()
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody("""
          {
            "errorsNumber": "%s",
            "errorRecordsFileName": "errorRecordsFileName",
            "errorsFileName": "%s"
          }
          """.formatted(errorsNumber, errorsFileName))));
  }

  private StubMapping mockFailOnSaveEndpoint(String testUrl, int errorsNumber, String errorsFileName) {
    return mockFailOnSaveEndpoint(testUrl, errorsNumber, errorsFileName, null);
  }

  private void awaitUntilMigrationCompleted(UUID operationId, MigrationOperationStatus operationStatus,
                                            int expectedTotalRecords, int expectedMappedRecords,
                                            int expectedSavedRecords) {
    awaitUntilAsserted(() ->
      doGet(marcMigrationEndpoint(operationId))
        .andExpect(operationStatus(operationStatus))
        .andExpect(totalRecords(expectedTotalRecords))
        .andExpect(mappedRecords(expectedMappedRecords))
        .andExpect(savedRecords(expectedSavedRecords))
    );
  }

  private void assertExpectedSorting(MvcResult response) {
    var dtoCollection = contentAsObj(response, MigrationOperationCollection.class);
    assertThat(dtoCollection.getMigrationOperations())
      .extracting(MigrationOperation::getStartTimeMapping)
      .isSortedAccordingTo(Comparator.reverseOrder());
  }

  private TestMigrationCollection prepare3MigrationCollection() {
    var migrationOperation1 = getNewMigrationOperation(AUTHORITY);
    NewMigrationOperation migrationOperation2 = getNewMigrationOperation(EntityType.INSTANCE);
    var postResult = doPost(marcMigrationEndpoint(), migrationOperation1).andReturn();
    var operationId1 = getOperationId(postResult);
    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId1).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
    postResult = doPost(marcMigrationEndpoint(), migrationOperation2).andReturn();
    var operationId2 = getOperationId(postResult);
    awaitUntilAsserted(() ->
      assertThat(databaseHelper.getOperation(TENANT_ID, operationId2).getStatus())
        .isEqualTo(OperationStatusType.DATA_MAPPING_COMPLETED)
    );
    postResult = doPost(marcMigrationEndpoint(), migrationOperation1).andReturn();
    var operationId3 = getOperationId(postResult);
    return new TestMigrationCollection(operationId1, operationId2, operationId3);
  }

  private MvcResult postAndAssertNewOperation(NewMigrationOperation migrationOperation, int expectedTotalRecords)
    throws Exception {
    return tryPost(marcMigrationEndpoint(), migrationOperation)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("id", notNullValue(UUID.class)))
      .andExpect(jsonPath("userId", is(USER_ID)))
      .andExpect(jsonPath("operationType", is(REMAPPING.getValue())))
      .andExpect(jsonPath("entityType", is(migrationOperation.getEntityType().getValue())))
      .andExpect(operationStatus(NEW))
      .andExpect(totalRecords(expectedTotalRecords))
      .andExpect(mappedRecords(0))
      .andExpect(savedRecords(0))
      .andReturn();
  }

  private void assertChunkStepsInDatabase(UUID operationId, int expectedSize, StepStatus stepStatus) {
    var steps = databaseHelper.getChunksSteps(TENANT_ID, operationId);
    assertThat(steps).hasSize(expectedSize)
      .allMatch(step -> step.getStepStartTime() != null
                        && step.getStepEndTime() != null
                        && step.getStepEndTime().after(step.getStepStartTime())
                        && step.getStatus().equals(stepStatus)
                        && (stepStatus.equals(FAILED) ? step.getNumOfErrors() >= 1 : step.getNumOfErrors().equals(0)));
  }

  private void assertChunksInDatabase(UUID operationId, int expectedSize, OperationStatusType statusType) {
    var chunks = databaseHelper.getOperationChunks(TENANT_ID, operationId);
    assertThat(chunks).hasSize(expectedSize)
      .allMatch(chunk -> chunk.getStartRecordId() != null
                         && chunk.getEndRecordId() != null
                         && chunk.getStatus().equals(statusType));
  }

  @SneakyThrows
  private String writeToFile(List<String> lines) {
    var filePath = "test/test.txt";
    var path = Paths.get(filePath);
    Files.createDirectories(path.getParent());
    try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      for (var line : lines) {
        writer.write(line);
        writer.newLine();
      }
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
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
    return Stream.of(Arguments.of(AUTHORITY, 81, 9), Arguments.of(INSTANCE, 11, 2));
  }

  private static Stream<Arguments> provideEntityTypesData() {
    return Stream.of(
      Arguments.of(AUTHORITY, 81, 9, 80, AUTHORITIES_BULK_ENDPOINT),
      Arguments.of(INSTANCE, 11, 2, 10, INSTANCES_BULK_ENDPOINT));
  }

  private static Stream<Arguments> provideEntityTypesDataForNegativeCase() {
    return Stream.of(
      Arguments.of(AUTHORITY, 81, 9, 0),
      Arguments.of(INSTANCE, 11, 2, 0));
  }

  @SneakyThrows
  private List<String> readFile(String remotePath) {
    try (var inputStream = s3Client.read(remotePath);
         var reader = new BufferedReader(new InputStreamReader(inputStream))) {
      return reader.lines().toList();
    }
  }

  private record TestMigrationCollection(UUID operationId1, UUID operationId2, UUID operationId3) { }
}
