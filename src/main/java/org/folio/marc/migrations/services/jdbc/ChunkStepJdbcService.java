package org.folio.marc.migrations.services.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.spring.FolioExecutionContext;
import org.hibernate.type.SqlTypes;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
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
    SET %s
    WHERE id = '%s';
    """;

  private static final String GET_CHUNK_STEP = """
      SELECT * FROM %s.operation_chunk_step
      WHERE operation_chunk_id = ?
      AND operation_step = ?::operationstep;
      """;

  private final BeanPropertyRowMapper<ChunkStep> mapper;

  public ChunkStepJdbcService(FolioExecutionContext context, JdbcTemplate jdbcTemplate,
                              BeanPropertyRowMapper<ChunkStep> mapper) {
    super(jdbcTemplate, context);
    this.mapper = mapper;
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

    var setFields = """
        status = '%s',
        step_end_time = '%s',
        num_of_errors = %s
        """.formatted(status, stepEndTime, numOfErrors);
    var sql = UPDATE_CHUNK_STEP.formatted(getSchemaName(), setFields, id);
    jdbcTemplate.update(sql);
  }

  public void updateChunkStep(UUID id, StepStatus status, Timestamp stepEndTime, int numOfErrors,
                              String entityErrorChunkFileName, String errorChunkFileName) {
    log.debug("updateChunkStep::For step {}: status {}, stepEndTime {}, numOfErrors {}",
        id, status, stepEndTime, numOfErrors);

    var setFields = """
        status = '%s',
        entity_error_chunk_file_name = '%s',
        error_chunk_file_name = '%s',
        step_end_time = '%s',
        num_of_errors = %s
        """.formatted(status, entityErrorChunkFileName, errorChunkFileName, stepEndTime, numOfErrors);
    var sql = UPDATE_CHUNK_STEP.formatted(getSchemaName(), setFields, id);
    jdbcTemplate.update(sql);
  }

  public void updateChunkStep(UUID id, StepStatus status, Timestamp stepStartTime) {
    log.debug("updateChunkStep::For step {}: status {}, stepStartTime {}", id, status, stepStartTime);
    var setFields = """
        status = '%s',
        step_start_time = '%s',
        num_of_errors = 0
        """.formatted(status, stepStartTime);
    var sql = UPDATE_CHUNK_STEP.formatted(getSchemaName(), setFields, id);
    jdbcTemplate.update(sql);
  }

  public List<ChunkStep> getChunkStepsByOperationIdAndStatus(UUID operationId, StepStatus status) {
    log.debug("getChunkStepsByOperationId::For operationId {}, status {}", operationId, status);
    var sql = """
        SELECT * FROM %s.operation_chunk_step
        WHERE operation_id = ? AND status = ?::stepstatus;
        """.formatted(getSchemaName());
    return jdbcTemplate.query(sql, mapper, operationId, status.name());
  }

  public ChunkStep getChunkStepByChunkIdAndOperationStep(UUID chunkId, OperationStep step) {
    log.debug("getChunkStepByChunkIdAndOperationStep::For chunkId {}, operation step {}", chunkId, step);
    var sql = GET_CHUNK_STEP.formatted(getSchemaName());
    var results = jdbcTemplate.query(sql, mapper, chunkId, step.name());
    if (results.isEmpty()) {
      log.warn("getChunkStepByChunkIdAndOperationStep:: No ChunkStep found for chunkId {} and operation step {}",
          chunkId, step);
      return null;
    }
    return results.getFirst();
  }
}
