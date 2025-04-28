package org.folio.marc.migrations.services.jdbc;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class OperationJdbcService extends JdbcService {

  private static final String UPDATE_OPERATION_STATUS = """
    UPDATE %s.operation
    SET status = '%s',
        %s = '%s'
    WHERE id = '%s';
    """;

  private static final String UPDATE_OPERATION_RECORDS = """
    UPDATE %s.operation
    SET mapped_num_of_records = mapped_num_of_records + %s,
        saved_num_of_records = saved_num_of_records + %s
    WHERE id = '%s';
    """;

  private static final String GET_OPERATION = """
    SELECT *
    FROM %s.operation
    WHERE id = '%s'
    """;

  private static final String DELETE_OPERATIONS_OLDER_THAN = """
    DELETE FROM %s.operation
    WHERE end_time_mapping < '%s'
    """;

  private final BeanPropertyRowMapper<Operation> mapper;

  public OperationJdbcService(FolioExecutionContext context,
                              JdbcTemplate jdbcTemplate,
                              BeanPropertyRowMapper<Operation> mapper) {
    super(jdbcTemplate, context);
    this.mapper = mapper;
  }

  public Operation getOperation(String id) {
    if (id == null) {
      return null;
    }

    var sql = GET_OPERATION.formatted(getSchemaName(), id);
    return this.jdbcTemplate.queryForObject(sql, mapper);
  }

  public void updateOperationStatus(String id, OperationStatusType status, OperationTimeType operationTimeType,
                                    Timestamp operationTimestamp) {
    log.info("updateOperationStatus::For operation {}: status {}, {} {}",
      id, status, operationTimeType.name(), operationTimestamp);

    var sql = UPDATE_OPERATION_STATUS.formatted(getSchemaName(), status, operationTimeType.getDbColumnName(),
      operationTimestamp, id);
    jdbcTemplate.update(sql);
  }

  public void addProcessedOperationRecords(UUID id, int recordsMapped, int recordsSaved) {
    log.info("addProcessedOperationRecords::For operation {}: recordsMapped {}, recordsSaved {}",
      id, recordsMapped, recordsSaved);

    var sql = UPDATE_OPERATION_RECORDS.formatted(getSchemaName(), recordsMapped, recordsSaved, id);
    jdbcTemplate.update(sql);
  }

  public void deleteOperationsOlderThan(Timestamp date) {
    log.info("deleteOperationsOlderThan::Deleting operations older than '{}'", date);
    var sql = DELETE_OPERATIONS_OLDER_THAN.formatted(getSchemaName(), date);
    jdbcTemplate.update(sql);
  }
}
