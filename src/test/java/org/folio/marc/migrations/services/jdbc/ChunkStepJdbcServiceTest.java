package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.spring.testing.type.UnitTest;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkStepJdbcServiceTest extends JdbcServiceTestBase {

  private @InjectMocks ChunkStepJdbcService service;

  @Test
  void createChunkStep_positive() {
    var step = ChunkStep.builder()
      .id(UUID.randomUUID())
      .operationId(UUID.randomUUID())
      .operationChunkId(UUID.randomUUID())
      .operationStep(OperationStep.DATA_MAPPING)
      .stepStartTime(Timestamp.from(Instant.now()))
      .stepEndTime(Timestamp.from(Instant.now()))
      .entityErrorChunkFileName("entityError")
      .errorChunkFileName("error")
      .status(StepStatus.IN_PROGRESS)
      .numOfErrors(3)
      .build();
    var expectedParams = new Object[]{step.getId(), step.getOperationId(),
      step.getOperationChunkId(), step.getOperationStep(), step.getEntityErrorChunkFileName(),
      step.getErrorChunkFileName(), step.getStatus(), step.getStepStartTime(),
      step.getStepEndTime(), 0};
    var expectedTypes = new int[]{SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.VARCHAR,
      SqlTypes.VARCHAR, SqlTypes.OTHER, SqlTypes.TIMESTAMP, SqlTypes.TIMESTAMP, SqlTypes.INTEGER};

    service.createChunkStep(step);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture(), eq(expectedParams), eq(expectedTypes));
    assertThat(sqlCaptor.getValue())
      .contains(TENANT_ID);
  }

  @Test
  void updateChunkStep_positive() {
    var id = UUID.randomUUID();
    var status = StepStatus.COMPLETED;
    var stepEndTime = Timestamp.from(Instant.now());
    var numOfErrors = 3;

    service.updateChunkStep(id, status, stepEndTime, numOfErrors);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains(id.toString(), status.name(), stepEndTime.toString(), String.valueOf(numOfErrors), TENANT_ID);
  }
}
