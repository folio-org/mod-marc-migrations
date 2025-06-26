package org.folio.marc.migrations.services.jdbc;

import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class SpringBatchExecutionParamsJdbcService extends JdbcService {

  private static final String GET_BATCH_EXECUTION_PARAM = """
      SELECT parameter_value
      FROM %s.batch_job_execution_params
      WHERE parameter_name = '%s'
        AND parameter_value IS NOT NULL
        AND job_execution_id IN (
          SELECT job_execution_id
          FROM %s.batch_job_execution_params
          WHERE parameter_name = 'operationId'
            AND parameter_value = '%s'
        )
      LIMIT 1;
      """;

  public SpringBatchExecutionParamsJdbcService(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    super(jdbcTemplate, context);
  }

  public String getBatchExecutionParam(String parameterName, String operationId) {
    log.info("getBatchExecutionParam::Fetching '{}' parameter for operationId '{}'", parameterName, operationId);
    var schemaName = getSchemaName();
    String sql = GET_BATCH_EXECUTION_PARAM.formatted(schemaName, parameterName, schemaName, operationId);
    var results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("parameter_value"));
    if (results.isEmpty()) {
      log.warn("getBatchExecutionParam::No result found for parameter '{}' and operationId '{}'", parameterName,
          operationId);
      return null;
    }
    return results.getFirst();
  }
}
