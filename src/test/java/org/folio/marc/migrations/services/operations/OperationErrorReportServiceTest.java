package org.folio.marc.migrations.services.operations;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationError;
import org.folio.marc.migrations.domain.entities.OperationErrorReport;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.domain.repositories.OperationErrorReportRepository;
import org.folio.marc.migrations.services.TenantContextRunner;
import org.folio.marc.migrations.services.batch.support.FolioS3Service;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationErrorJdbcService;
import org.folio.spring.data.OffsetRequest;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OperationErrorReportServiceTest {

  @Mock
  private FolioS3Service s3Service;
  @Mock
  private ChunkStepJdbcService chunkStepJdbcService;
  @Mock
  private OperationErrorJdbcService operationErrorJdbcService;
  @Mock
  private OperationErrorReportRepository errorReportRepository;
  @Mock
  private TenantContextRunner tenantContextRunner;

  private OperationErrorReportService service;

  @BeforeEach
  void setUp() {
    service = new OperationErrorReportService(
      s3Service,
      chunkStepJdbcService,
      operationErrorJdbcService,
      errorReportRepository,
      tenantContextRunner
    );
  }

  @Test
  void createErrorReport_Success() {
    UUID operationId = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(operationId);

    OperationErrorReport expectedReport = new OperationErrorReport();
    expectedReport.setId(operationId);
    expectedReport.setOperationId(operationId);
    expectedReport.setStatus(ErrorReportStatus.NOT_STARTED);

    when(errorReportRepository.save(any(OperationErrorReport.class))).thenReturn(expectedReport);

    OperationErrorReport result = service.createErrorReport(operation);

    assertNotNull(result);
    assertEquals(operationId, result.getId());
    assertEquals(operationId, result.getOperationId());
    assertEquals(ErrorReportStatus.NOT_STARTED, result.getStatus());
    verify(errorReportRepository).save(any(OperationErrorReport.class));
  }

  @Test
  void updateErrorReportStatus_Success() {
    UUID reportId = UUID.randomUUID();
    when(errorReportRepository.updateStatusById(any(), any())).thenReturn(1);

    service.updateErrorReportStatus(reportId, ErrorReportStatus.COMPLETED);

    verify(errorReportRepository).updateStatusById(ErrorReportStatus.COMPLETED, reportId);
  }

  @Test
  void initiateErrorReport_CompletedOperation_Success() {
    UUID operationId = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(operationId);
    operation.setStatus(OperationStatusType.DATA_MAPPING_COMPLETED);

    CompletableFuture<Void> result = service.initiateErrorReport(operation, "testTenant");

    assertTrue(result.isDone());
    verify(errorReportRepository).updateStatusById(ErrorReportStatus.IN_PROGRESS, operationId);
    verify(errorReportRepository).updateStatusById(ErrorReportStatus.COMPLETED, operationId);
  }

  @Test
  void initiateErrorReport_WithFailedChunks_Success() {
    UUID operationId = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(operationId);
    operation.setStatus(OperationStatusType.DATA_MAPPING_FAILED);

    ChunkStep failedChunk = ChunkStep.builder()
      .id(UUID.randomUUID())
      .operationId(operationId)
      .operationChunkId(UUID.randomUUID())
      .operationStep(OperationStep.DATA_MAPPING)
      .status(StepStatus.FAILED)
      .errorChunkFileName("error.txt")
      .build();

    when(chunkStepJdbcService.getChunkStepsByOperationIdAndStatus(operationId, StepStatus.FAILED))
      .thenReturn(List.of(failedChunk));
    when(s3Service.readFile(anyString())).thenReturn(List.of("record1,error message"));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(1);
      runnable.run();
      return null;
    }).when(tenantContextRunner).runInContext(anyString(), any(Runnable.class));

    CompletableFuture<Void> result = service.initiateErrorReport(operation, "testTenant");

    assertNotNull(result);
    verify(errorReportRepository).updateStatusById(ErrorReportStatus.IN_PROGRESS, operationId);
    await().untilAsserted(() -> verify(operationErrorJdbcService).saveOperationErrors(any(), anyString()));
  }

  @Test
  void getErrorReport_Success() {
    UUID operationId = UUID.randomUUID();
    OperationErrorReport expectedReport = new OperationErrorReport();
    when(errorReportRepository.findById(operationId)).thenReturn(Optional.of(expectedReport));

    Optional<OperationErrorReport> result = service.getErrorReport(operationId);

    assertTrue(result.isPresent());
    assertEquals(expectedReport, result.get());
  }

  @Test
  void getErrorReportEntries_Success() {
    UUID operationId = UUID.randomUUID();
    OffsetRequest offsetRequest = mock(OffsetRequest.class);
    List<OperationError> expectedErrors = List.of(new OperationError());
    when(operationErrorJdbcService.getOperationErrors(operationId, offsetRequest))
      .thenReturn(expectedErrors);

    List<OperationError> result = service.getErrorReportEntries(operationId, offsetRequest);

    assertNotNull(result);
    assertEquals(expectedErrors, result);
    verify(operationErrorJdbcService).getOperationErrors(operationId, offsetRequest);
  }
}
