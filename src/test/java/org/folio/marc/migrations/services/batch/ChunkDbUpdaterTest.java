package org.folio.marc.migrations.services.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.domain.RecordsMappingData;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkDbUpdaterTest {

  private @Mock ChunkJdbcService chunkJdbcService;
  private @Mock ChunkStepJdbcService chunkStepJdbcService;
  private @InjectMocks ChunkDbUpdater chunkDbUpdater;

  @Test
  void process_positive_partialErrors() {
    var results = List.of(new MappingResult("", null, null),
      new MappingResult(null, null, null));
    var mappingData = new RecordsMappingData(null, UUID.randomUUID(), UUID.randomUUID(), null,
      results.size(), null, null);
    var composite = new MappingComposite<>(mappingData, results);

    var result = chunkDbUpdater.process(composite);

    verify(chunkStepJdbcService).updateChunkStep(eq(mappingData.stepId()), eq(StepStatus.COMPLETED), notNull(), eq(1));
    verify(chunkJdbcService).updateChunk(mappingData.chunkId(), OperationStatusType.DATA_MAPPING_COMPLETED);
    assertThat(result).isEqualTo(composite);
  }

  @Test
  void process_positive_onlyErrors() {
    var results = List.of(new MappingResult(null, null, null),
      new MappingResult(null, null, null));
    var mappingData = new RecordsMappingData(null, UUID.randomUUID(), UUID.randomUUID(), null,
      results.size(), null, null);
    var composite = new MappingComposite<>(mappingData, results);

    var result = chunkDbUpdater.process(composite);

    verify(chunkStepJdbcService).updateChunkStep(eq(mappingData.stepId()), eq(StepStatus.FAILED), notNull(), eq(2));
    verify(chunkJdbcService).updateChunk(mappingData.chunkId(), OperationStatusType.DATA_MAPPING_FAILED);
    assertThat(result).isEqualTo(composite);
  }
}
