package org.folio.marc.migrations.services.batch.saving;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class SavingRecordsStepListener implements StepExecutionListener {

  private final OperationJdbcService jdbcService;

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    var exitStatus = stepExecution.getExitStatus();
    var jobId = stepExecution.getJobExecution().getJobId();
    var operationId = stepExecution.getJobParameters().getString(OPERATION_ID);

    if (operationId == null) {
      log.warn("No operationId found in job parameters for jobId {}", jobId);
      return new ExitStatus(ExitStatus.FAILED.getExitCode(), "No operationId in job params for jobId " + jobId);
    }
    if (ExitStatus.FAILED.getExitCode().equals(exitStatus.getExitCode())) {
      log.warn("afterStep:: job {} failed for operation {}: {}", jobId, operationId, exitStatus.getExitDescription());
      finishOperation(operationId, OperationStatusType.DATA_SAVING_FAILED);
      return exitStatus;
    }

    var operation = jdbcService.getOperation(operationId);
    if (!Objects.equals(operation.getTotalNumOfRecords(), operation.getSavedNumOfRecords())) {
      log.warn("afterStep::, operation.totalNumOfRecords: {}, operation.savedNumOfRecords: {}",
          operation.getTotalNumOfRecords(), operation.getSavedNumOfRecords());
      finishOperation(operationId, OperationStatusType.DATA_SAVING_FAILED);
    } else {
      finishOperation(operationId, OperationStatusType.DATA_SAVING_COMPLETED);
    }

    return exitStatus;
  }

  private void finishOperation(String operationId, OperationStatusType status) {
    jdbcService.updateOperationStatus(operationId, status, OperationTimeType.SAVING_END, Timestamp.from(Instant.now()));
  }
}
