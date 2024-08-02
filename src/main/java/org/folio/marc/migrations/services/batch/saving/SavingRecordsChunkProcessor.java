package org.folio.marc.migrations.services.batch.saving;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.marc.migrations.services.domain.DataSavingResult;
import org.folio.marc.migrations.services.domain.RecordsSavingData;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component()
@StepScope
@RequiredArgsConstructor
public class SavingRecordsChunkProcessor implements ItemProcessor<OperationChunk, DataSavingResult> {
  @Setter
  @Value("#{jobParameters['entityType']}")
  private EntityType entityType;

  private final BulkStorageService bulkStorageService;
  private final ChunkStepJdbcService chunkStepJdbcService;

  @Override
  public DataSavingResult process(OperationChunk chunk) {
    log.trace("process:: for operation {} chunk {}", chunk.getOperationId(), chunk.getId());

    var chunkStep = createChunkStep(chunk);
    var recordsSavingData = new RecordsSavingData(chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
        chunk.getNumOfRecords());

    var saveResponse = bulkStorageService.saveEntities(chunk.getEntityChunkFileName(), entityType);
    return new DataSavingResult(recordsSavingData, saveResponse);
  }

  private ChunkStep createChunkStep(OperationChunk chunk) {
    var stepId = UUID.randomUUID();
    var chunkStep = ChunkStep.builder()
        .id(stepId)
        .operationId(chunk.getOperationId())
        .operationChunkId(chunk.getId())
        .operationStep(OperationStep.DATA_SAVING)
        .status(StepStatus.IN_PROGRESS)
        .stepStartTime(Timestamp.from(Instant.now()))
        .build();

    chunkStepJdbcService.createChunkStep(chunkStep);
    return chunkStep;
  }
}
