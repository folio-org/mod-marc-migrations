package org.folio.marc.migrations.services.batch.mapping;

import static org.folio.marc.migrations.services.batch.support.JobConstants.OPERATION_FILES_PATH;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component("mappingRecordsStepPreProcessor")
@StepScope
@RequiredArgsConstructor
public class MappingRecordsChunkPreProcessor implements ItemProcessor<OperationChunk, MappingComposite<MarcRecord>> {

  private static final String STEP_FILE_NAME = OPERATION_FILES_PATH + "%s_%s_%s";

  private final AuthorityJdbcService authorityJdbcService;
  private final ChunkStepJdbcService chunkStepJdbcService;
  private final InstanceJdbcService instanceJdbcService;
  private final OperationJdbcService operationJdbcService;
  private final MigrationProperties props;

  @Setter
  @Value("#{jobParameters['entityType']}")
  private EntityType entityType;

  @Override
  public MappingComposite<MarcRecord> process(OperationChunk chunk) {
    log.trace("process:: for operation {} chunk {}", chunk.getOperationId(), chunk.getId());

    var records = (entityType == EntityType.AUTHORITY)
        ? authorityJdbcService.getAuthoritiesChunk(chunk.getStartRecordId(), chunk.getEndRecordId()) :
        instanceJdbcService.getInstancesChunk(chunk.getStartRecordId(), chunk.getEndRecordId());
    log.debug("process:: retrieved {} records for operation {} chunk {}", records.size(), chunk.getOperationId(),
        chunk.getId());

    ChunkStep chunkStep;
    if (!OperationStatusType.NEW.equals(chunk.getStatus())) {
      chunkStep = chunkStepJdbcService.getChunkStepByChunkIdAndOperationStep(chunk.getId(), OperationStep.DATA_MAPPING);
      if (chunkStep != null) {
        log.debug("process:: Updating existing chunk step for operation {} chunk {}", chunk.getOperationId(),
            chunk.getId());
        chunkStepJdbcService.updateChunkStep(chunkStep.getId(), StepStatus.IN_PROGRESS, Timestamp.from(Instant.now()));
        reduceMappedNumOfRecords(chunk, chunkStep.getNumOfErrors(), records);
      } else {
        log.debug("process:: Creating new chunk step for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
        chunkStep = createChunkStep(chunk);
      }
    } else {
      log.debug("process:: Creating new chunk step for operation {} chunk {}", chunk.getOperationId(), chunk.getId());
      chunkStep = createChunkStep(chunk);
    }

    if (records.size() != chunk.getNumOfRecords()) {
      log.warn("process:: Wrong number of records [{}] for operation {} chunk {}, step {}; record ids from {} to {},"
          + " expected - [{}].", records.size(), chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
        chunk.getStartRecordId(), chunk.getEndRecordId(), chunk.getNumOfRecords());
    }

    var mappingData = new RecordsMappingData(chunk.getOperationId(), chunk.getId(), chunkStep.getId(),
      chunk.getEntityChunkFileName(), chunk.getNumOfRecords(), chunkStep.getEntityErrorChunkFileName(),
      chunkStep.getErrorChunkFileName());
    return new MappingComposite<>(mappingData, records);
  }

  private void reduceMappedNumOfRecords(OperationChunk chunk, Integer numOfErrors, List<MarcRecord> records) {
    if (CollectionUtils.isNotEmpty(records)) {
      var errorCount = numOfErrors != null ? numOfErrors : 0;
      var reducedMappedNumOfRecords = records.size() - errorCount;
      if (reducedMappedNumOfRecords > 0) {
        operationJdbcService.updateOperationMappedNumber(chunk.getOperationId(), reducedMappedNumOfRecords);
      }
    }
  }

  private ChunkStep createChunkStep(OperationChunk chunk) {
    var stepId = UUID.randomUUID();
    var s3SubPath = props.getS3SubPath();
    var chunkStep = ChunkStep.builder()
      .id(stepId)
      .operationId(chunk.getOperationId())
      .operationChunkId(chunk.getId())
      .operationStep(OperationStep.DATA_MAPPING)
      .entityErrorChunkFileName(
          STEP_FILE_NAME.formatted(s3SubPath, chunk.getOperationId(), chunk.getId(), stepId, "entity-error"))
      .errorChunkFileName(
          STEP_FILE_NAME.formatted(s3SubPath, chunk.getOperationId(), chunk.getId(), stepId, "error"))
      .status(StepStatus.IN_PROGRESS)
      .stepStartTime(Timestamp.from(Instant.now()))
      .build();

    chunkStepJdbcService.createChunkStep(chunkStep);
    return chunkStep;
  }
}
