package org.folio.marc.migrations.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.services.jdbc.OperationJdbcService;
import org.folio.marc.migrations.services.jdbc.SpringBatchJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ExpirationServiceTest {

  private @Mock OperationJdbcService operationJdbcService;
  private @Mock SpringBatchJdbcService springBatchJdbcService;
  private @Mock MigrationProperties migrationProperties;
  private @InjectMocks ExpirationService expirationService;

  @Test
  void deleteExpiredData_shouldCallDependenciesWithCorrectParameters() {
    int jobRetentionDays = 30;
    final var expectedExpirationDate = Instant.now()
      .minus(jobRetentionDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

    when(migrationProperties.getJobRetentionDays()).thenReturn(jobRetentionDays);

    expirationService.deleteExpiredData();

    var operationCaptor = ArgumentCaptor.forClass(Timestamp.class);
    var batchCaptor = ArgumentCaptor.forClass(Timestamp.class);

    verify(operationJdbcService).deleteOperationsOlderThan(operationCaptor.capture());
    verify(springBatchJdbcService).deleteBatchJobInstancesOlderThan(batchCaptor.capture());

    assertThat(operationCaptor.getValue().toInstant().truncatedTo(ChronoUnit.MINUTES))
      .isEqualTo(expectedExpirationDate);
    assertThat(batchCaptor.getValue().toInstant().truncatedTo(ChronoUnit.MINUTES))
      .isEqualTo(expectedExpirationDate);
  }
}
