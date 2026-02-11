package org.folio.marc.migrations.services.batch.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingRecordsWriterTest {

  private final Long jobId = 5L;
  private final String jobFilesDirectory = "mod-marc-migrations/" + jobId;
  private final String defaultFilePath = "mod-marc-migrations";
  private final String customFilePath = "custom";
  private final JobExecution jobExecution = new JobExecution(1L, new JobInstance(jobId, "testJob"),
    new JobParameters());
  private final StepExecution stepExecution = new StepExecution(0L, "testStep", jobExecution);
  private @Mock MigrationProperties props;
  private @InjectMocks MappingRecordsWriter writer;

  @AfterEach
  @SneakyThrows
  void deleteDirectory() {
    FileUtils.deleteDirectory(new File("mod-marc-migrations"));
    FileUtils.deleteDirectory(new File(customFilePath));
  }

  @Test
  @SneakyThrows
  void prepareFilesPath_positive() {
    when(props.getS3LocalSubPath()).thenReturn(defaultFilePath);
    writer.prepareFilesPath(stepExecution);
    assertThat(Files.exists(Path.of(jobFilesDirectory))).isTrue();
  }

  @Test
  @SneakyThrows
  void write_positive() {
    when(props.getS3LocalSubPath()).thenReturn(defaultFilePath);
    writer.prepareFilesPath(stepExecution);
    var records = records(2, 2);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);
    var expectedFiles = List.of(jobFilesDirectory + "/entity", jobFilesDirectory + "/entityError",
      jobFilesDirectory + "/error");

    writer.write(chunk);

    assertThat(new File(jobFilesDirectory).listFiles()).hasSize(3);
    for (String filePath : expectedFiles) {
      var file = new File(filePath);
      assertThat(file).exists();
      assertThat(FileUtils.readLines(file, StandardCharsets.UTF_8)).hasSize(2);
    }
  }

  @Test
  @SneakyThrows
  void write_positiveConfigurableFilePath() {
    String customDirectory = customFilePath + "/" + jobId;
    when(props.getS3LocalSubPath()).thenReturn(customFilePath);
    writer.prepareFilesPath(stepExecution);
    assertThat(Files.exists(Path.of(customDirectory))).isTrue();

    var records = records(2, 2);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);
    var expectedFiles = List.of(customDirectory + "/entity", customDirectory + "/entityError",
        customDirectory + "/error");

    writer.write(chunk);

    assertThat(new File(customDirectory).listFiles()).hasSize(3);
    for (String filePath : expectedFiles) {
      var file = new File(filePath);
      assertThat(file).exists();
      assertThat(FileUtils.readLines(file, StandardCharsets.UTF_8)).hasSize(2);
    }
  }

  @Test
  @SneakyThrows
  void write_positive_onlyMapped() {
    when(props.getS3LocalSubPath()).thenReturn(defaultFilePath);
    writer.prepareFilesPath(stepExecution);
    var records = records(2, 0);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);
    var expectedFile = jobFilesDirectory + "/entity";

    writer.write(chunk);

    assertThat(new File(jobFilesDirectory).listFiles()).hasSize(1);
    var file = new File(expectedFile);
    assertThat(file).exists();
    assertThat(FileUtils.readLines(file, StandardCharsets.UTF_8)).hasSize(2);
  }

  @Test
  @SneakyThrows
  void write_positive_onlyErrors() {
    when(props.getS3LocalSubPath()).thenReturn(defaultFilePath);
    writer.prepareFilesPath(stepExecution);
    var records = records(0, 2);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);
    var expectedFiles = List.of(jobFilesDirectory + "/entityError", jobFilesDirectory + "/error");

    writer.write(chunk);

    assertThat(new File(jobFilesDirectory).listFiles()).hasSize(2);
    for (String filePath : expectedFiles) {
      var file = new File(filePath);
      assertThat(file).exists();
      assertThat(FileUtils.readLines(file, StandardCharsets.UTF_8)).hasSize(2);
    }
  }

  @Test
  void write_negative_localFilepathNotSet() {
    var records = records(2, 2);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);

    var ex = assertThrows(IllegalStateException.class, () -> writer.write(chunk));
    assertThat(ex).hasMessage("Local filepath not set for operationId " + composite.mappingData().operationId());
  }

  @Test
  @SneakyThrows
  void write_negative_pathDoesNotExist() {
    when(props.getS3LocalSubPath()).thenReturn(defaultFilePath);
    writer.prepareFilesPath(stepExecution);
    FileUtils.deleteDirectory(new File("mod-marc-migrations"));

    var records = records(2, 2);
    var composite = composite(records);
    var chunk = new Chunk<>(composite);

    var ex = assertThrows(IllegalStateException.class, () -> writer.write(chunk));
    assertThat(ex).hasMessage("java.nio.file.NoSuchFileException: mod-marc-migrations/5/entity");
  }

  private MappingComposite<MappingResult> composite(List<MappingResult> records) {
    var mappingData = new RecordsMappingData(UUID.randomUUID(), UUID.randomUUID(), null, "path/entity",
      5, "path/entityError", "path/error");
    return new MappingComposite<>(mappingData, records);
  }

  private List<MappingResult> records(int mapped, int errors) {
    var records = new LinkedList<MappingResult>();

    Stream.iterate(0, i -> i < mapped, i -> ++i)
      .map(i -> new MappingResult("mapped", null, null))
      .forEach(records::add);
    Stream.iterate(0, i -> i < errors, i -> ++i)
      .map(i -> new MappingResult(null, "invalid", "error"))
      .forEach(records::add);

    return records;
  }
}
