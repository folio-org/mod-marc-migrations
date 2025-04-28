package org.folio.marc.migrations.services;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.jdbc.SpringBatchJdbcService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ExpirationService {

  private final OperationJdbcService operationJdbcService;
  private final SpringBatchJdbcService springBatchJdbcService;
  private final MigrationProperties migrationProperties;

  public ExpirationService(OperationJdbcService operationJdbcService,
                           SpringBatchJdbcService springBatchJdbcService,
                           MigrationProperties migrationProperties) {
    this.operationJdbcService = operationJdbcService;
    this.springBatchJdbcService = springBatchJdbcService;
    this.migrationProperties = migrationProperties;
  }

  public void deleteExpiredData() {
    int jobRetentionDays = migrationProperties.getJobRetentionDays();
    var expirationDate = Timestamp.from(Instant.now().minus(jobRetentionDays, ChronoUnit.DAYS));

    log.info("deleteExpiredData:: [retention: {}, delete older than: {}]", jobRetentionDays, expirationDate);

    operationJdbcService.deleteOperationsOlderThan(expirationDate);
    springBatchJdbcService.deleteBatchJobInstancesOlderThan(expirationDate);

    log.info("deleteExpiredData::Expired data deletion completed.");
  }
}
