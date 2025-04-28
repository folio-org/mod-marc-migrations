package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SpringBatchJdbcServiceTest extends JdbcServiceTestBase {

  private @InjectMocks SpringBatchJdbcService service;

  @Test
  void deleteBatchJobInstancesOlderThan_positive() {
    var timestamp = Timestamp.from(Instant.now().minusSeconds(3600)); // 1 hour ago

    service.deleteBatchJobInstancesOlderThan(timestamp);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains("DELETE FROM", "batch_job_instance", timestamp.toString(), TENANT_ID);
  }
}
