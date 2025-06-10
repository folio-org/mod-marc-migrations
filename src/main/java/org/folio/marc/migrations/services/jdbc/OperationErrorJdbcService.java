package org.folio.marc.migrations.services.jdbc;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.OperationError;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.data.OffsetRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class OperationErrorJdbcService extends JdbcService {

  public OperationErrorJdbcService(FolioExecutionContext context, JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, context);
  }

  public void saveOperationErrors(Collection<OperationError> operationErrors, String tenantId) {
    if (operationErrors.isEmpty()) {
      return;
    }
    log.debug("saveOperationErrors:: Saving {} operation errors", operationErrors.size());
    var sql = """
      INSERT INTO %s.operation_error
      (id, report_id, operation_chunk_id, operation_step, chunk_status, record_id, error_message)
      VALUES (?, ?, ?, ?::operationstep, ?::stepstatus, ?, ?);
      """.formatted(getSchemaName(tenantId));
    jdbcTemplate.batchUpdate(sql, operationErrors, operationErrors.size(),
      (ps, operationError) -> {
        ps.setObject(1, operationError.getId());
        ps.setObject(2, operationError.getReportId());
        ps.setObject(3, operationError.getChunkId());
        ps.setString(4, operationError.getOperationStep().name());
        ps.setString(5, operationError.getChunkStatus().name());
        ps.setString(6, operationError.getRecordId());
        ps.setString(7, operationError.getErrorMessage());
      });
  }

  public List<OperationError> getOperationErrors(UUID operationId, OffsetRequest offsetRequest) {
    var offset = offsetRequest.getOffset();
    var limit = offsetRequest.getPageSize();
    log.debug("getOperationErrors:: For operationId {}, offset {}, limit {}", operationId, offset, limit);
    var sql = """
      SELECT id, report_id, operation_chunk_id, operation_step, chunk_status, record_id, error_message
      FROM %s.operation_error
      WHERE report_id = ?
      ORDER BY id
      OFFSET ? LIMIT ?;
      """.formatted(getSchemaName());
    return jdbcTemplate.query(sql, (rs, rowNum) -> {
      var operationError = new OperationError();
      operationError.setId(rs.getObject("id", UUID.class));
      operationError.setReportId(rs.getObject("report_id", UUID.class));
      operationError.setChunkId(rs.getObject("operation_chunk_id", UUID.class));
      operationError.setOperationStep(Enum.valueOf(OperationStep.class, rs.getString("operation_step")));
      operationError.setChunkStatus(Enum.valueOf(StepStatus.class, rs.getString("chunk_status")));
      operationError.setRecordId(rs.getString("record_id"));
      operationError.setErrorMessage(rs.getString("error_message"));
      return operationError;
    }, operationId, offset, limit);
  }

  public void deleteOperationErrorsByReportIdAndChunkId(UUID reportId, UUID chunkId) {
    log.debug("deleteOperationErrorsByReportIdAndChunkId:: Deleting operation errors for reportId {} and chunkId {}",
        reportId, chunkId);
    var sql = """
        DELETE FROM %s.operation_error
        WHERE report_id = ? AND operation_chunk_id = ?;
        """.formatted(getSchemaName());
    jdbcTemplate.update(sql, reportId, chunkId);
  }
}
