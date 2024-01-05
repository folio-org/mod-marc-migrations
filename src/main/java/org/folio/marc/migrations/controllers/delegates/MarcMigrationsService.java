package org.folio.marc.migrations.controllers.delegates;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.OperationType;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.folio.spring.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class MarcMigrationsService {

  static final String NOT_FOUND_MSG = "MARC migration operation was not found [id: %s]";

  private final MarcMigrationMapper mapper;
  private final OperationsService operationsService;

  public MarcMigrationsService(MarcMigrationMapper mapper, OperationsService operationsService) {
    this.mapper = mapper;
    this.operationsService = operationsService;
  }

  public MigrationOperation createNewMigration(NewMigrationOperation newMigrationOperation) {
    log.debug("createNewMigration::Trying to create new migration operation: {}", newMigrationOperation);
    validate(newMigrationOperation);
    var operation = mapper.toEntity(newMigrationOperation);
    var newOperation = operationsService.createOperation(operation);
    return mapper.toDto(newOperation);
  }

  public MigrationOperation getMarcMigrationById(UUID operationId) {
    log.debug("getMarcMigrationById::Trying to get migration operation by ID '{}'", operationId);
    return operationsService.getOperation(operationId)
      .map(mapper::toDto)
      .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
  }

  private void validate(NewMigrationOperation newMigrationOperation) {
    log.debug("validate::Validating new migration operation: {}", newMigrationOperation);
    validateOperationType(newMigrationOperation);
    validateEntityType(newMigrationOperation);
  }

  private static void validateOperationType(NewMigrationOperation newMigrationOperation) {
    var operationType = newMigrationOperation.getOperationType();
    if (!OperationType.REMAPPING.equals(operationType)) {
      throw ApiValidationException.forOperationType(operationType.getValue());
    }
  }

  private static void validateEntityType(NewMigrationOperation newMigrationOperation) {
    var entityType = newMigrationOperation.getEntityType();
    if (!EntityType.AUTHORITY.equals(entityType)) {
      throw ApiValidationException.forEntityType(entityType.getValue());
    }
  }

}
