package org.folio.marc.migrations.services.jdbc;

import java.sql.Timestamp;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class SpringBatchJdbcService extends JdbcService {

  private static final String DELETE_BATCH_JOB_INSTANCES_OLDER_THAN = """
    DELETE FROM %s.batch_job_instance
    WHERE job_instance_id IN (
      SELECT bji.job_instance_id
      FROM %s.batch_job_instance bji
      JOIN %s.batch_job_execution bje ON bji.job_instance_id = bje.job_instance_id
      WHERE bje.last_updated < '%s'
    )
    """;

  public SpringBatchJdbcService(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    super(jdbcTemplate, context);
  }

  public void deleteBatchJobInstancesOlderThan(Timestamp date) {
    log.info("deleteBatchJobInstancesOlderThan::Deleting batch job instances older than '{}'", date);
    var schemaName = getSchemaName();
    var sql = DELETE_BATCH_JOB_INSTANCES_OLDER_THAN.formatted(schemaName, schemaName, schemaName, date);
    jdbcTemplate.update(sql);
  }
}
