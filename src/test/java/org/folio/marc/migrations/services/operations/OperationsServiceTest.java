package org.folio.marc.migrations.services.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.JdbcService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OperationsServiceTest {

  private @Mock FolioExecutionContext context;
  private @Mock OperationRepository operationRepository;
  private @Mock JdbcService jdbcService;
  private @InjectMocks OperationsService operationsService;

  @Test
  void createOperation_SetsFieldsAndSavesOperation() {
    // Arrange
    Operation operation = new Operation();
    UUID userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);
    when(operationRepository.save(operation)).thenReturn(operation);
    when(jdbcService.countNumOfRecords()).thenReturn(10);

    // Act
    Operation result = operationsService.createOperation(operation);

    ArgumentCaptor<Operation> captor = captor();

    // Assert
    verify(operationRepository).save(captor.capture());
    assertNotNull(result);
    assertEquals(userId, captor.getValue().getUserId());
    assertEquals(OperationStatusType.NEW, captor.getValue().getStatus());
    assertEquals(10, captor.getValue().getTotalNumOfRecords());
    assertEquals(0, captor.getValue().getProcessedNumOfRecords());
  }
}
