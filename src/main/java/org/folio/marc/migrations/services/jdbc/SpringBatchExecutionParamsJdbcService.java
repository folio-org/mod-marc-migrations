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
        AND job_execution_id = (
          SELECT MAX(job_execution_id)
          FROM %s.batch_job_execution_params
          WHERE parameter_name = 'operationId'
            AND parameter_value = '%s'
        );
      """;

  public SpringBatchExecutionParamsJdbcService(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    super(jdbcTemplate, context);
  }

  public String getBatchExecutionParam(String parameterName, String operationId) {
    log.info("getBatchExecutionParam::Fetching '{}' parameter for operationId '{}'", parameterName, operationId);
    var schemaName = getSchemaName();
    String sql = GET_BATCH_EXECUTION_PARAM.formatted(schemaName, parameterName, schemaName, operationId);
    return jdbcTemplate.queryForObject(sql, String.class);
  }
}
