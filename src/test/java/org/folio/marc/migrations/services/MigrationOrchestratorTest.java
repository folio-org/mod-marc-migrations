package org.folio.marc.migrations.services;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.operations.ChunkService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {
  private @Spy MigrationProperties migrationProperties;
  private @Mock ChunkService chunkService;
  private @Mock JdbcService jdbcService;
  private @InjectMocks MigrationOrchestrator service;

  @Test
  @SneakyThrows
  void submitMappingTask_positive() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());

    // Act
    service.submitMappingTask(operation).get(100, TimeUnit.MILLISECONDS);

    // Assert
    verify(chunkService).prepareChunks(operation);
    verify(jdbcService).updateOperationStatus(operation.getId(), OperationStatusType.DATA_MAPPING);
  }

  @Test
  @SneakyThrows
  void submitMappingTask_shouldFailAndUpdateOperationStatusOnChunksPre() {
    // Arrange
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    doThrow(new IllegalStateException()).when(chunkService).prepareChunks(operation);

    // Act
    service.submitMappingTask(operation).get(100, TimeUnit.MILLISECONDS);

    // Assert
    verify(chunkService).prepareChunks(operation);
    verify(jdbcService).updateOperationStatus(operation.getId(), OperationStatusType.DATA_MAPPING_FAILED);
  }
}
