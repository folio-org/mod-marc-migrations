package org.folio.marc.migrations.services.batch;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@StepScope
@RequiredArgsConstructor
public class ChunkDbUpdater implements ItemProcessor<MappingComposite<MappingResult>, MappingComposite<MappingResult>> {

  private final ChunkJdbcService chunkJdbcService;
  private final ChunkStepJdbcService chunkStepJdbcService;

  @Override
  public MappingComposite<MappingResult> process(MappingComposite<MappingResult> composite) {
    log.trace("process:: for operation {}, chunk {}",
      composite.mappingData().operationId(), composite.mappingData().chunkId());
    var numOfErrors = (int) composite.records().stream()
      .filter(result -> result.mappedRecord() == null)
      .count();
    var chunkFailed = composite.mappingData().numberOfRecords() == numOfErrors;
    var stepStatus = chunkFailed ? StepStatus.FAILED : StepStatus.COMPLETED;
    chunkStepJdbcService.updateChunkStep(composite.mappingData().stepId(), stepStatus, Timestamp.from(Instant.now()),
      numOfErrors);

    var chunkStatus = chunkFailed ? OperationStatusType.DATA_MAPPING_FAILED
      : OperationStatusType.DATA_MAPPING_COMPLETED;
    chunkJdbcService.updateChunk(composite.mappingData().chunkId(), chunkStatus);
    return composite;
  }
}
