package org.folio.marc.migrations.services.operations;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationErrorJdbcService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.data.OffsetRequest;
import org.folio.spring.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class OperationsService {

  private static final String ORDER_BY_FIELD = "startTimeMapping";
  private static final String NOT_FOUND_MSG = "MARC migration operation was not found [id: %s]";

  private final FolioExecutionContext context;
  private final OperationRepository repository;
  private final AuthorityJdbcService authorityJdbcService;
  private final InstanceJdbcService instanceJdbcService;
  private final OperationErrorReportService errorReportService;
  private final OperationErrorJdbcService operationErrorJdbcService;

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
    var createdOperation = repository.save(operation);
    errorReportService.createErrorReport(createdOperation);
    return createdOperation;
  }

  public Operation retryOperation(UUID operationId) {
    var operation = getOperation(operationId).orElseThrow(() ->
        new NotFoundException(NOT_FOUND_MSG.formatted(operationId)));
    log.info("retryOperation::Preparing operation: {}", operation);
    operation.setStatus(OperationStatusType.DATA_MAPPING);
    operation.setEndTimeMapping(null);
    log.info("retryOperation::Saving operation: {}", operation);
    var updatedOperation = repository.save(operation);
    errorReportService.updateErrorReportStatus(operation.getId(), ErrorReportStatus.NOT_STARTED);
    operationErrorJdbcService.deleteOperationErrorsByReportId(operationId);
    return updatedOperation;
  }

  public Optional<Operation> getOperation(UUID operationId) {
    log.info("getOperation::Get operation by ID: {}", operationId);
    return repository.findById(operationId);
  }

  public Page<Operation> getOperations(Integer offset, Integer limit, EntityType entityType) {
    var pageable = new OffsetRequest(offset, limit, Sort.by(Sort.Order.desc(ORDER_BY_FIELD)));
    if (entityType == null) {
      return repository.findAll(pageable);
    }

    Specification<Operation> specification = (root, query, builder) ->
        builder.equal(root.get("entityType"), entityType);
    return repository.findAll(specification, pageable);
  }
}
