package org.folio.marc.migrations.services.operations;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class OperationsService {

  private final FolioExecutionContext context;
  private final OperationRepository repository;
  private final AuthorityJdbcService authorityJdbcService;
  private final InstanceJdbcService instanceJdbcService;

  public Operation createOperation(Operation operation) {
    log.info("createOperation::Preparing new operation: {}", operation);

    var numOfRecords = (operation.getEntityType() == EntityType.AUTHORITY)
        ? authorityJdbcService.countNumOfRecords() :
          instanceJdbcService.countNumOfRecords();

    operation.setUserId(context.getUserId());
    operation.setStatus(OperationStatusType.NEW);
    operation.setTotalNumOfRecords(numOfRecords);
    operation.setMappedNumOfRecords(0);
    operation.setSavedNumOfRecords(0);
    log.info("createOperation::Saving new operation: {}", operation);
    return repository.save(operation);
  }

  public Optional<Operation> getOperation(UUID operationId) {
    log.info("getOperation::Get operation by ID: {}", operationId);
    return repository.findById(operationId);
  }
}
