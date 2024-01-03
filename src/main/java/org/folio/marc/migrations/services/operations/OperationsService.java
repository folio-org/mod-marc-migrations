package org.folio.marc.migrations.services.operations;

import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.JdbcService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class OperationsService {

  private final FolioExecutionContext context;
  private final OperationRepository operationRepository;
  private final JdbcService jdbcService;

  public OperationsService(FolioExecutionContext context,
                           OperationRepository operationRepository,
                           JdbcService jdbcService) {
    this.context = context;
    this.operationRepository = operationRepository;
    this.jdbcService = jdbcService;
  }

  public Operation createOperation(Operation operation) {
    log.info("createOperation::Creating new operation: {}", operation);
    var numOfRecords = jdbcService.countNumOfRecords();
    operation.setUserId(context.getUserId());
    operation.setStatus(OperationStatusType.NEW);
    operation.setTotalNumOfRecords(numOfRecords);
    operation.setProcessedNumOfRecords(0);
    log.info("createOperation::Saving new operation: {}", operation);
    return operationRepository.save(operation);
  }
}
