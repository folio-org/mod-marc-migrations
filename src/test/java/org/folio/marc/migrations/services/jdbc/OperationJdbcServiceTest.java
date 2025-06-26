package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.domain.OperationTimeType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OperationJdbcServiceTest extends JdbcServiceTestBase {

  private @InjectMocks OperationJdbcService service;

  @Test
  void updateOperationStatus_positive() {
    var id = UUID.randomUUID().toString();
    var status = OperationStatusType.DATA_MAPPING_COMPLETED;
    var timeType = OperationTimeType.MAPPING_END;
    var timestamp = Timestamp.from(Instant.now());

    service.updateOperationStatus(id, status, timeType, timestamp);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains(id, status.name(), "end_time_mapping", timestamp.toString(), TENANT_ID);
  }

  @Test
  void addProcessedOperationRecords() {
    var id = UUID.randomUUID();
    var recordsMapped = 3;
    var recordsSaved = 1;

    service.addProcessedOperationRecords(id, recordsMapped, recordsSaved);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains(id.toString(), String.valueOf(recordsMapped), String.valueOf(recordsSaved), TENANT_ID);
  }

  @Test
  void updateOperationMappedNumber_positive() {
    // Arrange
    var id = UUID.randomUUID();
    var recordsMapped = 5;

    // Act
    service.updateOperationMappedNumber(id, recordsMapped);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue()).contains(id.toString(), String.valueOf(recordsMapped));
  }

  @Test
  void updateOperationSavedNumber_positive() {
    // Arrange
    var id = UUID.randomUUID();
    var recordsReduced = 10;

    // Act
    service.updateOperationSavedNumber(id, recordsReduced);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .contains(id.toString(), String.valueOf(recordsReduced));
  }
}
