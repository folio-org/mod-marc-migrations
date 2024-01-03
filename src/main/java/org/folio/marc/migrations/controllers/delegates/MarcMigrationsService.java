package org.folio.marc.migrations.controllers.delegates;

import static org.folio.marc.migrations.utils.LoggingUtils.throwExceptionAndLog;

import lombok.extern.log4j.Log4j2;
import org.folio.entlinks.domain.dto.EntityType;
import org.folio.entlinks.domain.dto.MigrationOperation;
import org.folio.entlinks.domain.dto.NewMigrationOperation;
import org.folio.entlinks.domain.dto.OperationType;
import org.folio.marc.migrations.controllers.mappers.MarcMigrationMapper;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.marc.migrations.services.operations.OperationsService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class MarcMigrationsService {

  private final MarcMigrationMapper mapper;
  private final OperationsService operationsService;

  public MarcMigrationsService(MarcMigrationMapper mapper, OperationsService operationsService) {
    this.mapper = mapper;
    this.operationsService = operationsService;
  }

  public MigrationOperation createNewMigration(NewMigrationOperation newMigrationOperation) {
    validate(newMigrationOperation);
    var operation = mapper.toEntity(newMigrationOperation);
    Operation newOperation = operationsService.createOperation(operation);
    return mapper.toDto(newOperation);
  }

  private void validate(NewMigrationOperation newMigrationOperation) {
    validateOperationType(newMigrationOperation);
    validateEntityType(newMigrationOperation);

  }

  private static void validateOperationType(NewMigrationOperation newMigrationOperation) {
    var operationType = newMigrationOperation.getOperationType();
    if (!OperationType.REMAPPING.equals(operationType)) {
      throwExceptionAndLog(ApiValidationException.forOperationType(operationType.getValue()));
    }
  }

  private static void validateEntityType(NewMigrationOperation newMigrationOperation) {
    var entityType = newMigrationOperation.getEntityType();
    if (!EntityType.AUTHORITY.equals(entityType)) {
      throwExceptionAndLog(ApiValidationException.forEntityType(entityType.getValue()));
    }
  }

}
