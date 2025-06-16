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
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.marc.migrations.services.batch.support.FolioS3Service;
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

  @Setter
  @Value("#{jobParameters['publishEventsFlag']}")
  private Boolean publishEventsFlag;

  private final BulkStorageService bulkStorageService;
  private final ChunkStepJdbcService chunkStepJdbcService;
  private final FolioS3Service s3Service;

  @Override
  public DataSavingResult process(OperationChunk chunk) {
    log.trace("process:: for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
    ChunkStep chunkStep;
    if (OperationStatusType.DATA_SAVING_FAILED.equals(chunk.getStatus())) {
      chunkStep = chunkStepJdbcService.getChunkStepByChunkIdAndOperationStep(chunk.getId(), OperationStep.DATA_SAVING);
      if (chunkStep != null) {
        log.debug("process:: Updating existing chunk step for operation {} chunk {}", chunk.getOperationId(),
            chunk.getId());
        chunkStepJdbcService.updateChunkStep(chunkStep.getId(), StepStatus.IN_PROGRESS, Timestamp.from(Instant.now()));
        var recordsSavingData = new RecordsSavingData(chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
            chunkStep.getNumOfErrors());
        var lines = s3Service.readFile(chunkStep.getEntityErrorChunkFileName());
        log.info("process:: Found {} error records in chunk step file: {}", lines,
            chunkStep.getEntityErrorChunkFileName());
        var saveResponse = bulkStorageService.saveEntities(chunkStep.getEntityErrorChunkFileName(), entityType,
            publishEventsFlag);
        log.info("process:: Save response {} for chunk step file: {}", saveResponse.toString(),
            chunkStep.getEntityErrorChunkFileName());
        return new DataSavingResult(recordsSavingData, saveResponse);
      } else {
        log.debug("process:: Creating new chunk step for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
        chunkStep = createChunkStep(chunk);
      }
    } else {
      chunkStep = createChunkStep(chunk);
    }
    var recordsSavingData = new RecordsSavingData(chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
        chunk.getNumOfRecords());

    var saveResponse = bulkStorageService.saveEntities(chunk.getEntityChunkFileName(), entityType, publishEventsFlag);
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
