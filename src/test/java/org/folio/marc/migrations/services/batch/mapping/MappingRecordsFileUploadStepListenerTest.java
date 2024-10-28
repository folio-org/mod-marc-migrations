package org.folio.marc.migrations.services.batch.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.services.batch.support.JobConstants.JobParameterNames.OPERATION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.batch.support.FolioS3Service;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingRecordsFileUploadStepListenerTest {

  private final Long jobId = 5L;
  private final String jobFilesDirectory = "job/" + jobId;
  private final String defaultFilePath = "job";

  private @Mock FolioS3Service s3Service;
  private @Mock OperationJdbcService jdbcService;
  private @Mock MigrationProperties props;
  private @InjectMocks MappingRecordsFileUploadStepListener listener;

  @BeforeEach
  @SneakyThrows
  void setUpFilesStorage() {
    when(props.getLocalFileStoragePath()).thenReturn(defaultFilePath);
    var directory = Path.of(jobFilesDirectory);
    Files.createDirectories(directory);
  }

  @AfterEach
  @SneakyThrows
  void deleteDirectory() {
    FileUtils.deleteDirectory(new File("job"));
  }

  @Test
  @SneakyThrows
  void afterStep_positive() {
    var operationId = UUID.randomUUID().toString();
    var operation = new Operation();
    operation.setTotalNumOfRecords(10);
    operation.setMappedNumOfRecords(10);
    when(jdbcService.getOperation(operationId)).thenReturn(operation);
    var jobExecution = new JobExecution(new JobInstance(jobId, "testJob"), 1L,
      new JobParameters(Map.of(OPERATION_ID, new JobParameter<>(operationId, String.class))));
    var stepExecution = new StepExecution("testStep", jobExecution);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    var path1 = Path.of(jobFilesDirectory, "test1");
    var path2 = Path.of(jobFilesDirectory, "test2");
    Files.createFile(path1);
    Files.createFile(path2);

    var actual = listener.afterStep(stepExecution);

    assertThat(actual).isEqualTo(stepExecution.getExitStatus());
    verify(s3Service).uploadFile(path1.toFile().getAbsolutePath(), "operation/" + operationId + "/test1");
    verify(s3Service).uploadFile(path2.toFile().getAbsolutePath(), "operation/" + operationId + "/test2");
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING_COMPLETED),
      eq(OperationTimeType.MAPPING_END), notNull());
    verify(jdbcService).getOperation(operationId);
    assertThat(Files.exists(Path.of(jobFilesDirectory))).isFalse();
  }

  @Test
  void afterStep_negative_noOperationId() {
    var jobExecution = new JobExecution(new JobInstance(jobId, "testJob"), 1L, new JobParameters());
    var stepExecution = new StepExecution("testStep", jobExecution);

    var actual = listener.afterStep(stepExecution);
    assertThat(actual).isNotNull();
    assertThat(actual.getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    assertThat(actual.getExitDescription()).isEqualTo("No operationId in job params for jobId " + jobId);
    assertThat(Files.exists(Path.of(jobFilesDirectory))).isFalse();
  }

  @Test
  @SneakyThrows
  void afterStep_negative_jobFailed() {
    var operationId = UUID.randomUUID().toString();
    var jobExecution = new JobExecution(new JobInstance(jobId, "testJob"), 1L,
      new JobParameters(Map.of(OPERATION_ID, new JobParameter<>(operationId, String.class))));
    var stepExecution = new StepExecution("testStep", jobExecution);
    stepExecution.setExitStatus(ExitStatus.FAILED);

    var actual = listener.afterStep(stepExecution);

    assertThat(actual).isEqualTo(stepExecution.getExitStatus());
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING_FAILED),
      eq(OperationTimeType.MAPPING_END), notNull());
    assertThat(Files.exists(Path.of(jobFilesDirectory))).isFalse();
  }

  @Test
  @SneakyThrows
  void afterStep_negative_fileUploadFailed() {
    var operationId = UUID.randomUUID().toString();
    var jobExecution = new JobExecution(new JobInstance(jobId, "testJob"), 1L,
      new JobParameters(Map.of(OPERATION_ID, new JobParameter<>(operationId, String.class))));
    var stepExecution = new StepExecution("testStep", jobExecution);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    var path1 = Path.of(jobFilesDirectory, "test1");
    Files.createFile(path1);
    var failMessage = "fail";
    doThrow(new IllegalStateException(failMessage)).when(s3Service).uploadFile(any(), any());

    var actual = listener.afterStep(stepExecution);

    assertThat(actual.getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    assertThat(actual.getExitDescription()).isEqualTo(failMessage);
    verify(jdbcService).updateOperationStatus(eq(operationId), eq(OperationStatusType.DATA_MAPPING_FAILED),
      eq(OperationTimeType.MAPPING_END), notNull());
    assertThat(Files.exists(Path.of(jobFilesDirectory))).isFalse();
  }
}
