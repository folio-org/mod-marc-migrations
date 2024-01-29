package org.folio.marc.migrations.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JdbcServiceTest {

  private @Mock JdbcTemplate jdbcTemplate;
  private @Mock FolioExecutionContext context;
  private @InjectMocks JdbcService service;

  @Test
  void updateStatus_updatesStatus() {
    // Arrange
    var operationId = UUID.randomUUID();
    var operationStatus = OperationStatusType.DATA_MAPPING;

    // Act
    service.updateOperationStatus(operationId, operationStatus);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).execute(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .contains(operationId.toString(), operationStatus.toString());
  }
}
