package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkStepJdbcServiceTest extends JdbcServiceTestBase {

  private @Mock BeanPropertyRowMapper<ChunkStep> mapper;
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
    var expectedParams = new Object[] {step.getId(), step.getOperationId(),
                                       step.getOperationChunkId(), step.getOperationStep(),
                                       step.getEntityErrorChunkFileName(),
                                       step.getErrorChunkFileName(), step.getStatus(), step.getStepStartTime(),
                                       step.getStepEndTime(), 0};
    var expectedTypes = new int[] {SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.VARCHAR,
                                   SqlTypes.VARCHAR, SqlTypes.OTHER, SqlTypes.TIMESTAMP, SqlTypes.TIMESTAMP,
                                   SqlTypes.INTEGER};

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

  @Test
  void getChunkStepsByOperationIdAndStatus_positive() {
    // given
    var operationId = UUID.randomUUID();
    var status = StepStatus.IN_PROGRESS;
    var expectedResult = List.of(
      ChunkStep.builder()
        .id(UUID.randomUUID())
        .operationId(operationId)
        .status(status)
        .build()
    );

    when(jdbcTemplate.query(anyString(), eq(mapper), eq(operationId), eq(status.name())))
      .thenReturn(expectedResult);

    // when
    var result = service.getChunkStepsByOperationIdAndStatus(operationId, status);

    // then
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), eq(mapper), eq(operationId), eq(status.name()));
    assertThat(sqlCaptor.getValue())
      .contains(TENANT_ID)
      .contains("operation_chunk_step")
      .contains("operation_id")
      .contains("status");
    assertThat(result)
      .isEqualTo(expectedResult);
  }
}
