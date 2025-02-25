package org.folio.marc.migrations.services.batch.saving;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.domain.DataSavingResult;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class SavingRecordsWriter implements ItemWriter<DataSavingResult> {

  private final OperationJdbcService jdbcService;
  private final ChunkJdbcService chunkJdbcService;
  private final ChunkStepJdbcService chunkStepJdbcService;

  @Override
  public void write(Chunk<? extends DataSavingResult> chunk) {
    var saveResult = chunk.getItems().getFirst();
    var operationId = saveResult.recordsSavingData().operationId();
    var chunkId = saveResult.recordsSavingData().chunkId();
    var stepId = saveResult.recordsSavingData().stepId();
    var numberOfRecords = saveResult.recordsSavingData().numberOfRecords();
    log.debug("write:: for operationId {}, chunkId {}", operationId, chunkId);

    if (saveResult.saveResponse() == null) {
      updateChunkAndChunkStep(chunkId, stepId, false, numberOfRecords, null, null);
      return;
    }

    int errorsCount = Optional.ofNullable(saveResult.saveResponse().getErrorsNumber()).orElse(0);
    boolean saveSucceeded = errorsCount == 0;

    log.debug("write:: for operationId {}, totalNumberOfRecords: {}, errorsCount: {}",
        operationId, numberOfRecords, errorsCount);
    jdbcService.addProcessedOperationRecords(operationId, 0, numberOfRecords - errorsCount);
    updateChunkAndChunkStep(chunkId, stepId, saveSucceeded, errorsCount,
        saveResult.saveResponse().getErrorRecordsFileName(), saveResult.saveResponse().getErrorsFileName());
  }

  private void updateChunkAndChunkStep(UUID chunkId, UUID stepId, boolean saveSucceeded, int errorsCount,
                                       String entityErrorFileName, String errorFileName) {
    var stepStatus = saveSucceeded ? StepStatus.COMPLETED : StepStatus.FAILED;
    if (entityErrorFileName != null || errorFileName != null) {
      chunkStepJdbcService.updateChunkStep(stepId, stepStatus, Timestamp.from(Instant.now()), errorsCount,
          entityErrorFileName, errorFileName);
    } else {
      chunkStepJdbcService.updateChunkStep(stepId, stepStatus, Timestamp.from(Instant.now()), errorsCount);
    }

    var chunkStatus =
        saveSucceeded ? OperationStatusType.DATA_SAVING_COMPLETED : OperationStatusType.DATA_SAVING_FAILED;
    chunkJdbcService.updateChunk(chunkId, chunkStatus);
  }
}
