package org.folio.marc.migrations.controllers.delegates;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.dto.ErrorReport;
import org.folio.marc.migrations.domain.dto.ErrorReportCollection;
import org.folio.marc.migrations.domain.dto.ErrorReportStatus;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.MigrationOrchestrator;
import org.folio.marc.migrations.services.jdbc.OperationErrorJdbcService;
import org.folio.marc.migrations.services.operations.OperationErrorReportService;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.folio.spring.data.OffsetRequest;
import org.folio.spring.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class MarcMigrationsService {

  static final String NOT_FOUND_MSG = "MARC migration operation was not found [id: %s]";

  private final MarcMigrationMapper mapper;
  private final MigrationProperties props;
  private final OperationsService operationsService;
  private final MigrationOrchestrator migrationOrchestrator;
  private final OperationErrorReportService errorReportsService;
  private final OperationErrorJdbcService operationErrorJdbcService;

  public MigrationOperation createNewMigration(NewMigrationOperation newMigrationOperation) {
    log.debug("createNewMigration::Trying to create new migration operation: {}", newMigrationOperation);
    validateMigrationCreate(newMigrationOperation);
    var operation = mapper.toEntity(newMigrationOperation);
    var newOperation = operationsService.createOperation(operation);
    migrationOrchestrator.submitMappingTask(newOperation);
    return mapper.toDto(newOperation);
  }

  public MigrationOperation retryMarcMigration(UUID operationId, List<UUID> chunkIds) {
    log.debug("retryMarcMigration::Trying to retry the migration for the chunkIds: {}", chunkIds);
    validateMigrationRetry(chunkIds);
    var operation = operationsService.retryOperation(operationId);
    migrationOrchestrator.submitRetryMappingTask(operation, chunkIds);
    return mapper.toDto(operation);
  }

  public MigrationOperation getMarcMigrationById(UUID operationId) {
    log.debug("getMarcMigrationById::Trying to get migration operation by ID '{}'", operationId);
    return operationsService.getOperation(operationId)
      .map(mapper::toDto)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
  }

  public void saveMigrationOperation(UUID operationId, SaveMigrationOperation request) {
    log.debug("saveMigrationOperation::Trying to save migration operation by ID '{}'", operationId);
    var operation = operationsService.getOperation(operationId)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
    validateOperationStatusForSave(request, operation);
    migrationOrchestrator.submitMappingSaveTask(operation, request);
  }

  public void retryMigrationSaveOperation(UUID operationId, List<UUID> chunkIds) {
    log.debug("retryMigrationSaveOperation::Retry saving migration operation by ID '{}'", operationId);
    var operation = operationsService.getOperation(operationId)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
    if (operation.getStatus() != OperationStatusType.DATA_SAVING_FAILED
        && operation.getStatus() != OperationStatusType.DATA_MAPPING_COMPLETED) {
      throw ApiValidationException.notAllowedRetryForOperationStatus(operation.getStatus().name());
    }
    validateMigrationRetry(chunkIds);
    deleteOperationErrors(operationId);
    migrationOrchestrator.submitMappingSaveRetryTask(operation, chunkIds);
  }

  public void createErrorReport(UUID operationId, String tenantId) {
    log.debug("createErrorReport::Trying to create error report for migration operation by ID '{}'", operationId);
    var operation = operationsService.getOperation(operationId)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
    errorReportsService.initiateErrorReport(operation, tenantId)
      .whenComplete((v, ex) -> {
        if (ex != null) {
          log.error("Error creating error report for operation {}: {}", operationId, ex.getMessage(), ex);
          throw new IllegalStateException("Error creating error report", ex);
        }
      });
  }

  public ErrorReportStatus getErrorReportStatus(UUID operationId) {
    log.debug("getErrorReportStatus::Trying to get error report status for operation ID '{}'", operationId);
    return errorReportsService.getErrorReport(operationId)
      .map(operationErrorReport ->
        new ErrorReportStatus(ErrorReportStatus.StatusEnum.fromValue(operationErrorReport.getStatus().name()))
          .operationId(operationId))
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
  }

  public ErrorReportCollection getErrorReportEntries(UUID operationId, Integer offset, Integer limit) {
    log.debug("getErrorReportEntries::Trying to get error report entries for operation ID '{}'", operationId);
    var offsetRequest = new OffsetRequest(offset, limit);
    var reports = errorReportsService.getErrorReportEntries(operationId, offsetRequest)
      .stream()
      .map(operationError -> new ErrorReport(operationError.getReportId(),
        operationError.getChunkId().toString(),
        operationError.getOperationStep().name(),
        operationError.getChunkStatus().name(),
        operationError.getRecordId(), operationError.getErrorMessage()))
      .toList();
    return new ErrorReportCollection()
      .errorReports(reports);
  }

  private void validateMigrationCreate(NewMigrationOperation newMigrationOperation) {
    log.debug("validate::Validating new migration operation: {}", newMigrationOperation);
    var operationType = newMigrationOperation.getOperationType();
    if (!OperationType.REMAPPING.equals(operationType)) {
      throw ApiValidationException.forOperationType(operationType.getValue());
    }
  }

  private void validateOperationStatusForSave(SaveMigrationOperation saveMigrationOperation, Operation operation) {
    var status = saveMigrationOperation.getStatus();
    if (!MigrationOperationStatus.DATA_SAVING.equals(status)) {
      throw ApiValidationException.forOperationStatus(status.getValue());
    }

    if (operation.getStatus() != OperationStatusType.DATA_MAPPING_COMPLETED) {
      throw ApiValidationException.notAllowedSaveForOperationStatus(operation.getStatus().name());
    }
  }

  private void validateMigrationRetry(List<UUID> chunkIds) {
    if (chunkIds == null || chunkIds.isEmpty()) {
      throw new ApiValidationException("validateMigrationRetry:: no chunk IDs provided");
    }
    if (chunkIds.size() > props.getChunkRetryingMaxIdsCount()) {
      throw ApiValidationException.maxSizeExceeded(props.getChunkRetryingMaxIdsCount(), chunkIds.size());
    }
  }

  private void deleteOperationErrors(UUID operationId) {
    log.debug("deleteOperationErrors::Updating error report status to NOT_STARTED for operation ID '{}'", operationId);
    errorReportsService.updateErrorReportStatus(operationId,
        org.folio.marc.migrations.domain.entities.types.ErrorReportStatus.NOT_STARTED);

    log.debug("deleteOperationErrors::Deleting operation errors for operation ID '{}'", operationId);
    operationErrorJdbcService.deleteOperationErrorsByReportId(operationId);
  }
}
