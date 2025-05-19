package org.folio.marc.migrations.services.operations;

import static org.folio.marc.migrations.domain.entities.types.EntityType.AUTHORITY;
import static org.folio.marc.migrations.domain.entities.types.EntityType.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.repositories.OperationRepository;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OperationsServiceTest {

  private @Mock FolioExecutionContext context;
  private @Mock OperationRepository repository;
  private @Mock AuthorityJdbcService authorityJdbcService;
  private @Mock InstanceJdbcService instanceJdbcService;
  private @Mock OperationErrorReportService errorReportService;
  private @InjectMocks OperationsService service;

  @Test
  void createAuthorityOperation_SetsFieldsAndSavesOperation() {
    when(authorityJdbcService.countNumOfRecords()).thenReturn(10);
    createOperation_SetsFieldsAndSavesOperation(AUTHORITY);
  }

  @Test
  void createInstanceOperation_SetsFieldsAndSavesOperation() {
    when(instanceJdbcService.countNumOfRecords()).thenReturn(10);
    createOperation_SetsFieldsAndSavesOperation(INSTANCE);
  }

  void createOperation_SetsFieldsAndSavesOperation(EntityType entityType) {
    // Arrange
    var operation = new Operation();
    operation.setEntityType(entityType);
    var userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);
    when(repository.save(operation)).thenReturn(operation);

    // Act
    var result = service.createOperation(operation);

    var captor = ArgumentCaptor.<Operation>captor();

    // Assert
    verify(repository).save(captor.capture());
    var createdOperation = captor.getValue();
    verify(errorReportService).createErrorReport(createdOperation);
    assertNotNull(result);
    assertEquals(userId, createdOperation.getUserId());
    assertEquals(OperationStatusType.NEW, createdOperation.getStatus());
    assertEquals(10, createdOperation.getTotalNumOfRecords());
    assertEquals(0, createdOperation.getMappedNumOfRecords());
    assertEquals(0, createdOperation.getSavedNumOfRecords());
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

  @Test
  void getOperations_ReturnsOperations() {
    // Arrange
    var expected = new PageImpl<>(List.of(new Operation()));
    when(repository.findAll(any(Pageable.class))).thenReturn(expected);

    // Act
    var result = service.getOperations(0, 100, null);

    // Assert
    assertEquals(expected, result);
    verify(repository).findAll(any(Pageable.class));
  }

  @Test
  void getOperations_ReturnsOperationsForGivenEntityType() {
    // Arrange
    var expected = new PageImpl<>(List.of(new Operation()));
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(expected);

    // Act
    var result = service.getOperations(0, 100, AUTHORITY);

    // Assert
    assertEquals(expected, result);
    verify(repository).findAll(any(Specification.class), any(Pageable.class));
  }
}
