package org.folio.marc.migrations.controllers.delegates;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationStatus;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.MigrationOrchestrator;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.folio.spring.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class MarcMigrationsService {

  static final String NOT_FOUND_MSG = "MARC migration operation was not found [id: %s]";

  private final MarcMigrationMapper mapper;
  private final OperationsService operationsService;
  private final MigrationOrchestrator migrationOrchestrator;

  public MigrationOperation createNewMigration(NewMigrationOperation newMigrationOperation) {
    log.debug("createNewMigration::Trying to create new migration operation: {}", newMigrationOperation);
    validateMigrationCreate(newMigrationOperation);
    var operation = mapper.toEntity(newMigrationOperation);
    var newOperation = operationsService.createOperation(operation);
    migrationOrchestrator.submitMappingTask(newOperation);
    return mapper.toDto(newOperation);
  }

  public MigrationOperation getMarcMigrationById(UUID operationId) {
    log.debug("getMarcMigrationById::Trying to get migration operation by ID '{}'", operationId);
    return operationsService.getOperation(operationId)
      .map(mapper::toDto)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
  }

  public void saveMigrationOperation(UUID operationId, SaveMigrationOperation saveMigrationOperation) {
    log.debug("saveMigrationOperation::Trying to save migration operation by ID '{}'", operationId);
    var operation = operationsService.getOperation(operationId)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
    validateOperationStatusForSave(saveMigrationOperation, operation);
    migrationOrchestrator.submitMappingSaveTask(operation);
  }

  private void validateMigrationCreate(NewMigrationOperation newMigrationOperation) {
    log.debug("validate::Validating new migration operation: {}", newMigrationOperation);
    validateOperationType(newMigrationOperation);
    validateEntityType(newMigrationOperation);
  }

  private void validateOperationType(NewMigrationOperation newMigrationOperation) {
    var operationType = newMigrationOperation.getOperationType();
    if (!OperationType.REMAPPING.equals(operationType)) {
      throw ApiValidationException.forOperationType(operationType.getValue());
    }
  }

  private void validateEntityType(NewMigrationOperation newMigrationOperation) {
    var entityType = newMigrationOperation.getEntityType();
    if (!EntityType.AUTHORITY.equals(entityType)) {
      throw ApiValidationException.forEntityType(entityType.getValue());
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
}
