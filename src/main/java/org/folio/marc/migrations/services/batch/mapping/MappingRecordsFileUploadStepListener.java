package org.folio.marc.migrations.services.batch.mapping;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JOB_FILES_PATH;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.folio.marc.migrations.services.batch.support.JobConstants.OPERATION_FILES_PATH;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.batch.support.FolioS3Service;
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
public class MappingRecordsFileUploadStepListener implements StepExecutionListener {

  private final FolioS3Service s3Service;
  private final OperationJdbcService jdbcService;
  private final MigrationProperties props;

  @SneakyThrows
  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    var exitStatus = stepExecution.getExitStatus();
    var jobId = stepExecution.getJobExecution().getJobId();
    var operationId = stepExecution.getJobParameters().getString(OPERATION_ID);
    var filesPath = JOB_FILES_PATH.formatted(props.getS3LocalSubPath(), jobId);

    if (operationId == null) {
      log.warn("No operationId found in job parameters for jobId {}", jobId);
      clearLocalFiles(filesPath);
      return new ExitStatus(ExitStatus.FAILED.getExitCode(), "No operationId in job params for jobId " + jobId);
    }
    if (ExitStatus.FAILED.getExitCode().equals(exitStatus.getExitCode())) {
      log.warn("afterStep:: job {} failed for operation {}: {}", jobId, operationId, exitStatus.getExitDescription());
      try {
        log.warn("afterStep:: upload local files even if the job failed.");
        uploadLocalFiles(filesPath, operationId);
      } catch (Exception e) {
        log.error("afterStep:: Failed to upload local files for operation {}: {}", operationId, e.getMessage());
      }
      finishOperation(operationId, OperationStatusType.DATA_MAPPING_FAILED);
      clearLocalFiles(filesPath);
      return exitStatus;
    }

    try {
      log.info("afterStep:: trying to upload and delete local files for operation {}", operationId);
      uploadLocalFiles(filesPath, operationId);

      var operation = jdbcService.getOperation(operationId);
      if (!Objects.equals(operation.getTotalNumOfRecords(), operation.getMappedNumOfRecords())) {
        log.warn("afterStep:: operation.totalNumOfRecords: {}, operation.mappedNumOfRecords: {}",
            operation.getTotalNumOfRecords(), operation.getMappedNumOfRecords());
        finishOperation(operationId, OperationStatusType.DATA_MAPPING_FAILED);
      } else {
        finishOperation(operationId, OperationStatusType.DATA_MAPPING_COMPLETED);
      }
    } catch (Exception ex) {
      log.warn("afterStep:: file upload/delete failed for operation {}, reason {}",
        operationId, ex.getMessage());

      finishOperation(operationId, OperationStatusType.DATA_MAPPING_FAILED);
      return new ExitStatus(ExitStatus.FAILED.getExitCode(), ex.getMessage());
    } finally {
      clearLocalFiles(filesPath);
    }

    return exitStatus;
  }

  private void uploadLocalFiles(String filesPath, String operationId) {
    var directory = new File(filesPath);
    for (var file : directory.listFiles()) {
      if (!file.isFile()) {
        continue;
      }

      var localPath = file.getAbsolutePath();
      var remotePath = OPERATION_FILES_PATH.formatted(props.getS3SubPath(), operationId) + file.getName();
      s3Service.uploadFile(localPath, remotePath);
    }
  }

  private void clearLocalFiles(String directory) throws IOException {
    FileUtils.deleteDirectory(new File(directory));
  }

  private void finishOperation(String operationId, OperationStatusType status) {
    jdbcService.updateOperationStatus(operationId, status, OperationTimeType.MAPPING_END,
      Timestamp.from(Instant.now()));
  }
}
