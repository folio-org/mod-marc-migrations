package org.folio.marc.migrations.services.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
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
  private @Mock OperationRepository repository;
  private @Mock AuthorityJdbcService authorityJdbcService;
  private @InjectMocks OperationsService service;

  @Test
  void createOperation_SetsFieldsAndSavesOperation() {
    // Arrange
    var operation = new Operation();
    var userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);
    when(repository.save(operation)).thenReturn(operation);
    when(authorityJdbcService.countNumOfRecords()).thenReturn(10);

    // Act
    var result = service.createOperation(operation);

    var captor = ArgumentCaptor.<Operation>captor();

    // Assert
    verify(repository).save(captor.capture());
    assertNotNull(result);
    assertEquals(userId, captor.getValue().getUserId());
    assertEquals(OperationStatusType.NEW, captor.getValue().getStatus());
    assertEquals(10, captor.getValue().getTotalNumOfRecords());
    assertEquals(0, captor.getValue().getMappedNumOfRecords());
    assertEquals(0, captor.getValue().getSavedNumOfRecords());
  }

  @Test
  void getOperation_ReturnsOperation() {
    // Arrange
    var operationId = UUID.randomUUID();
    var fetchedOperation = Optional.of(new Operation());
    when(repository.findById(operationId)).thenReturn(fetchedOperation);

    // Act
    var result = service.getOperation(operationId);

    // Assert
    assertEquals(fetchedOperation, result);
  }
}
