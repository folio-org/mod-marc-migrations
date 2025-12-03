package org.folio.marc.migrations.services.batch.saving;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component()
@StepScope
@RequiredArgsConstructor
public class SavingRetryRecordsChunkProcessor implements ItemProcessor<OperationChunk, DataSavingResult> {

  @Setter
  @Value("#{jobParameters['entityType']}")
  private EntityType entityType;

  @Setter
  @Value("#{jobParameters['publishEventsFlag']}")
  private Boolean publishEventsFlag;

  private final BulkStorageService bulkStorageService;
  private final ChunkStepJdbcService chunkStepJdbcService;
  private final OperationJdbcService operationJdbcService;
  private final FolioS3Service s3Service;

  @Override
  public DataSavingResult process(OperationChunk chunk) {
    log.trace("process:: for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
    var chunkStep = chunkStepJdbcService.getChunkStepByChunkIdAndOperationStep(chunk.getId(),
        OperationStep.DATA_SAVING);

    int numOfRecords = chunk.getNumOfRecords();
    if (chunkStep != null) {
      log.debug("process:: Updating existing chunk step for operation {} chunk {}", chunk.getOperationId(),
          chunk.getId());
      chunkStepJdbcService.updateChunkStep(chunkStep.getId(), StepStatus.IN_PROGRESS, Timestamp.from(Instant.now()));
      if (isChunkPartialyFailed(chunk, chunkStep)) {
        numOfRecords = chunkStep.getNumOfErrors();
      } else {
        reduceRecordCountOnErrors(chunk, chunkStep, numOfRecords);
      }
    } else {
      log.debug("process:: Creating new chunk step for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
      chunkStep = createChunkStep(chunk);
    }
    var recordsSavingData = new RecordsSavingData(chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
        numOfRecords);
    var saveResponse = bulkStorageService.saveEntities(chunk.getEntityChunkFileName(), entityType, publishEventsFlag);
    return new DataSavingResult(recordsSavingData, saveResponse);
  }

  private boolean isChunkPartialyFailed(OperationChunk chunk, ChunkStep chunkStep) {
    return OperationStatusType.DATA_SAVING_FAILED.equals(chunk.getStatus())
           && chunkStep.getNumOfErrors() != null
           && chunkStep.getNumOfErrors() > 0
           && StringUtils.isNotEmpty(chunkStep.getErrorChunkFileName())
           && isChunkFileUpdated(chunk, chunkStep.getErrorChunkFileName());
  }

  private void reduceRecordCountOnErrors(OperationChunk chunk, ChunkStep chunkStep, int numOfRecords) {
    // reduce the saved_num_of_records if saving is retry for all records in a chunk.
    var numOfErrors = chunkStep.getNumOfErrors() != null ? chunkStep.getNumOfErrors() : 0;
    var reducedSavedNumOfRecords = numOfRecords - numOfErrors;
    operationJdbcService.updateOperationSavedNumber(chunk.getOperationId(), reducedSavedNumOfRecords);
  }

  private boolean isChunkFileUpdated(OperationChunk chunk, String errorChunkFileName) {
    var entityChunkFileName = chunk.getEntityChunkFileName();

    var entityLines = s3Service.readFile(entityChunkFileName);
    log.debug("isChunkFileUpdated:: Read {} entity lines from chunk file: {}", entityLines.size(), entityChunkFileName);

    var errorLines = s3Service.readFile(errorChunkFileName);
    log.debug("isChunkFileUpdated:: Read {} error lines from chunk step file: {}", errorLines.size(),
        errorChunkFileName);

    if (CollectionUtils.isNotEmpty(entityLines) && CollectionUtils.isNotEmpty(errorLines)) {
      var entityLinesForRetry = getEntityLinesForRetry(errorLines, entityLines);
      if (CollectionUtils.isNotEmpty(entityLinesForRetry)) {
        log.debug("isChunkFileUpdated:: Writing {} entity lines for retry to entity chunk file: {}",
            entityLinesForRetry.size(),
            entityChunkFileName);
        s3Service.writeFile(entityChunkFileName, entityLinesForRetry);
        return true;
      }
      log.warn("isChunkFileUpdated:: No entity lines found for retry in chunk file: {}", entityChunkFileName);
    }
    return false;
  }

  private List<String> getEntityLinesForRetry(List<String> errorLines, List<String> entityLines) {
    var errorRecordIds = errorLines.stream()
      .map(errorLine -> StringUtils.substringBefore(errorLine, ','))
      .toList();

    return entityLines.stream()
      .filter(str -> errorRecordIds.stream()
        .anyMatch(str::contains))
      .toList();
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
