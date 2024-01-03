package org.folio.marc.migrations.services.operations;

import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.JdbcService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

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
    var numOfRecords = jdbcService.countNumOfRecords();
    operation.setUserId(context.getUserId());
    operation.setStatus(OperationStatusType.NEW);
    operation.setTotalNumOfRecords(numOfRecords);
    operation.setProcessedNumOfRecords(0);
    return operationRepository.save(operation);
  }
}
