package org.folio.marc.migrations.services.batch;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JOB_FILES_PATH;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.folio.marc.migrations.services.batch.support.JobConstants.OPERATION_FILES_PATH;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.s3.client.FolioS3Client;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class FileUploadStepListener implements StepExecutionListener {

  private final FolioS3Client s3Client;
  private final OperationJdbcService jdbcService;

  @SneakyThrows
  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    var exitStatus = stepExecution.getExitStatus();
    var jobId = stepExecution.getJobExecution().getJobId();
    var operationId = stepExecution.getJobParameters().getString(OPERATION_ID);
    var filesPath = JOB_FILES_PATH.formatted(jobId);

    if (operationId == null) {
      log.warn("No operationId found in job parameters for jobId " + jobId);
      clearLocalFiles(filesPath);
      return new ExitStatus(ExitStatus.FAILED.getExitCode(), "No operationId in job params for jobId " + jobId);
    }
    if (ExitStatus.FAILED.getExitCode().equals(exitStatus.getExitCode())) {
      log.warn("afterStep:: job {} failed for operation {}: {}", jobId, operationId, exitStatus.getExitDescription());
      finishOperation(operationId, OperationStatusType.DATA_MAPPING_FAILED);
      clearLocalFiles(filesPath);
      return exitStatus;
    }

    try {
      log.info("afterStep:: trying to upload and delete local files for operation {}", operationId);
      uploadLocalFiles(filesPath, operationId);
      finishOperation(operationId, OperationStatusType.DATA_MAPPING_COMPLETED);
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
      var remotePath = OPERATION_FILES_PATH.formatted(operationId) + file.getName();
      s3Client.upload(localPath, remotePath);
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
