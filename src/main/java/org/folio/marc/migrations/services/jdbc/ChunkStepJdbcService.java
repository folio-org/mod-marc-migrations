package org.folio.marc.migrations.services.jdbc;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.spring.FolioExecutionContext;
import org.hibernate.type.SqlTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ChunkStepJdbcService extends JdbcService {

  private static final String CREATE_CHUNK_STEP = """
    INSERT INTO %s.operation_chunk_step
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String UPDATE_CHUNK_STEP = """
    UPDATE %s.operation_chunk_step
    SET status = '%s',
        step_end_time = '%s',
        num_of_errors = %s
    WHERE id = '%s';
    """;

  private final JdbcTemplate jdbcTemplate;

  public ChunkStepJdbcService(FolioExecutionContext context, JdbcTemplate jdbcTemplate) {
    super(context);
    this.jdbcTemplate = jdbcTemplate;
  }

  public void createChunkStep(ChunkStep chunkStep) {
    log.debug("createChunkStep::Id {}: operationId {}, chunkId {}, stepStartTime {}",
      chunkStep.getId(), chunkStep.getOperationId(), chunkStep.getOperationChunkId(), chunkStep.getStepStartTime());

    var sql = CREATE_CHUNK_STEP.formatted(getSchemaName());
    var params = new Object[]{chunkStep.getId(), chunkStep.getOperationId(),
      chunkStep.getOperationChunkId(), chunkStep.getOperationStep(), chunkStep.getEntityErrorChunkFileName(),
      chunkStep.getErrorChunkFileName(), chunkStep.getStatus(), chunkStep.getStepStartTime(),
      chunkStep.getStepEndTime(), 0};
    var types = new int[]{SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.OTHER, SqlTypes.VARCHAR,
      SqlTypes.VARCHAR, SqlTypes.OTHER, SqlTypes.TIMESTAMP, SqlTypes.TIMESTAMP, SqlTypes.INTEGER};
    jdbcTemplate.update(sql, params, types);
  }

  public void updateChunkStep(UUID id, StepStatus status, Timestamp stepEndTime, int numOfErrors) {
    log.debug("updateChunkStep::For step {}: status {}, stepEndTime {}, numOfErrors {}",
      id, status, stepEndTime, numOfErrors);

    var sql = UPDATE_CHUNK_STEP.formatted(getSchemaName(), status, stepEndTime, numOfErrors, id);
    jdbcTemplate.update(sql);
  }
}
