package org.folio.marc.migrations.services.batch.mapping;

import static org.folio.marc.migrations.services.batch.support.JobConstants.JOB_FILES_PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class MappingRecordsWriter implements ItemWriter<MappingComposite<MappingResult>> {

  private String filePath;
  private final MigrationProperties props;

  @BeforeStep
  public void prepareFilesPath(StepExecution stepExecution) throws IOException {
    var jobExecution = stepExecution.getJobExecution();
    this.filePath = JOB_FILES_PATH.formatted(props.getLocalFileStoragePath(), jobExecution.getJobId());
    Files.createDirectories(Paths.get(filePath));
  }

  @Override
  public void write(Chunk<? extends MappingComposite<MappingResult>> chunk) {
    //only one because of step config with spring batch size 1
    var composite = chunk.getItems().get(0);
    log.debug("write:: for operationId {}, chunkId {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId());

    if (filePath == null) {
      log.warn("Local filepath not set for operationId {}, chunkId {}",
        composite.mappingData().operationId(), composite.mappingData().chunkId());
      throw new IllegalStateException(
        "Local filepath not set for operationId " + composite.mappingData().operationId());
    }

    writeMappedRecords(composite);
    writeEntityErrorRecords(composite);
    writeErrors(composite);
  }

  private void writeMappedRecords(MappingComposite<MappingResult> composite) {
    var lines = composite.records().stream()
      .map(MappingResult::mappedRecord)
      .filter(Objects::nonNull)
      .toList();

    if (lines.isEmpty()) {
      log.warn("No valid entities for operation {}, chunk {}",
        composite.mappingData().operationId(), composite.mappingData().chunkId());
      return;
    }

    log.trace("writeMappedRecords:: for operationId {}, chunkId {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId());
    writeToFile(composite.mappingData(), composite.mappingData().entityChunkFile(), lines);
  }

  private void writeEntityErrorRecords(MappingComposite<MappingResult> composite) {
    var lines = composite.records().stream()
      .map(MappingResult::invalidMarcRecord)
      .filter(Objects::nonNull)
      .toList();

    if (lines.isEmpty()) {
      log.trace("No invalid entities for operation {}, chunk {}",
        composite.mappingData().operationId(), composite.mappingData().chunkId());
      return;
    }

    log.trace("writeEntityErrorRecords:: for operationId {}, chunkId {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId());
    writeToFile(composite.mappingData(), composite.mappingData().entityErrorChunkFileName(), lines);
  }

  private void writeErrors(MappingComposite<MappingResult> composite) {
    var lines = composite.records().stream()
      .map(MappingResult::errorCause)
      .filter(Objects::nonNull)
      .toList();

    if (lines.isEmpty()) {
      log.trace("No error causes for operation {}, chunk {}",
        composite.mappingData().operationId(), composite.mappingData().chunkId());
      return;
    }

    log.trace("writeErrors:: for operationId {}, chunkId {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId());
    writeToFile(composite.mappingData(), composite.mappingData().errorChunkFileName(), lines);
  }

  private void writeToFile(RecordsMappingData mappingData, String fileName, List<String> lines) {
    fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
    var path = Paths.get(filePath, fileName);
    try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      for (var line : lines) {
        writer.write(line);
        writer.newLine();
      }
    } catch (Exception ex) {
      log.warn("Unable to write file {} for operation {}, chunk {}: {}",
        fileName, mappingData.operationId(), mappingData.chunkId(), ex.getMessage());
      throw new IllegalStateException(ex);
    }
  }
}
