package org.folio.marc.migrations.services.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationError;
import org.folio.marc.migrations.domain.entities.OperationErrorReport;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.folio.marc.migrations.domain.repositories.OperationErrorReportRepository;
import org.folio.marc.migrations.services.TenantContextRunner;
import org.folio.marc.migrations.services.batch.support.FolioS3Service;
import org.folio.marc.migrations.services.jdbc.ChunkStepJdbcService;
import org.folio.marc.migrations.services.jdbc.OperationErrorJdbcService;
import org.folio.spring.data.OffsetRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class OperationErrorReportService {

  public static final String UNKNOWN_RECORD_ID = "<unknown>";
  private final FolioS3Service s3Service;
  private final ChunkStepJdbcService chunkStepJdbcService;
  private final OperationErrorJdbcService operationErrorJdbcService;
  private final OperationErrorReportRepository errorReportRepository;
  private final TenantContextRunner tenantContextRunner;

  public OperationErrorReportService(FolioS3Service s3Service, ChunkStepJdbcService chunkStepJdbcService,
                                     OperationErrorJdbcService operationErrorJdbcService,
                                     OperationErrorReportRepository errorReportRepository,
                                     TenantContextRunner tenantContextRunner) {
    this.s3Service = s3Service;
    this.chunkStepJdbcService = chunkStepJdbcService;
    this.operationErrorJdbcService = operationErrorJdbcService;
    this.errorReportRepository = errorReportRepository;
    this.tenantContextRunner = tenantContextRunner;
  }

  public OperationErrorReport createErrorReport(@NonNull Operation operation) {
    log.info("createErrorReport::Creating empty error report for operation: {}", operation.getId());
    var errorReport = new OperationErrorReport();
    errorReport.setId(operation.getId());
    errorReport.setOperationId(operation.getId());
    errorReport.setStatus(ErrorReportStatus.NOT_STARTED);
    return errorReportRepository.save(errorReport);
  }

  public void updateErrorReportStatus(@NonNull UUID id, @NonNull ErrorReportStatus status) {
    log.info("updateErrorReportStatus::Updating error report status [id: {}, status: {}]", id, status);
    var updatedRecords = errorReportRepository.updateStatusById(status, id);
    if (updatedRecords == 0) {
      log.error("updateErrorReportStatus::Failed to update error report status [id: {}, status: {}]", id, status);
    }
  }

  public CompletableFuture<Void> initiateErrorReport(@NonNull Operation operation, String tenantId) {
    log.info("initiateErrorReport::Initiating error report for operation: {}", operation.getId());
    updateErrorReportStatus(operation.getId(), ErrorReportStatus.IN_PROGRESS);

    if (operation.getStatus() == OperationStatusType.DATA_MAPPING_COMPLETED
        || operation.getStatus() == OperationStatusType.DATA_SAVING_COMPLETED) {
      updateErrorReportStatus(operation.getId(), ErrorReportStatus.COMPLETED);
      return CompletableFuture.completedFuture(null);
    }

    var failedChunks = chunkStepJdbcService.getChunkStepsByOperationIdAndStatus(operation.getId(), StepStatus.FAILED);
    if (failedChunks.isEmpty()) {
      log.warn("initiateErrorReport::No failed chunks found for operation: {}", operation.getId());
      updateErrorReportStatus(operation.getId(), ErrorReportStatus.COMPLETED);
      return CompletableFuture.completedFuture(null);
    }
    // Process each failed chunk in parallel
    List<CompletableFuture<Void>> futures = failedChunks.stream()
      .map(chunkStep -> CompletableFuture.runAsync(() -> tenantContextRunner.runInContext(tenantId, () -> {
        var errorFileLines = s3Service.readFile(chunkStep.getErrorChunkFileName());
        if (CollectionUtils.isEmpty(errorFileLines)) {
          log.warn("initiateErrorReport::No error file lines found for chunk step: {}", chunkStep.getId());
          return;
        }
        List<OperationError> operationErrors = new ArrayList<>();
        for (var errorFileLine : errorFileLines) {
          String errorMessage = errorFileLine;
          var recordId = StringUtils.substringBefore(errorFileLine, ',');
          if (StringUtils.isBlank(recordId)) {
            recordId = UNKNOWN_RECORD_ID;
          } else {
            errorMessage = StringUtils.substringAfter(errorFileLine, ',');
          }
          var operationError = new OperationError();
          operationError.setId(UUID.randomUUID());
          operationError.setReportId(operation.getId());
          operationError.setChunkId(chunkStep.getOperationChunkId());
          operationError.setOperationStep(chunkStep.getOperationStep());
          operationError.setChunkStatus(chunkStep.getStatus());
          operationError.setRecordId(recordId);
          operationError.setErrorMessage(errorMessage);
          operationErrors.add(operationError);
        }
        operationErrorJdbcService.saveOperationErrors(operationErrors, tenantId);
      })))
      .toList();

    // Wait for all futures to complete and then update the status
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenAccept(unused -> tenantContextRunner.runInContext(tenantId,
        () -> updateErrorReportStatus(operation.getId(), ErrorReportStatus.COMPLETED)));
  }

  public Optional<OperationErrorReport> getErrorReport(UUID operationId) {
    return errorReportRepository.findById(operationId);
  }

  public List<OperationError> getErrorReportEntries(UUID operationId, OffsetRequest offsetRequest) {
    return operationErrorJdbcService.getOperationErrors(operationId, offsetRequest);
  }
}
