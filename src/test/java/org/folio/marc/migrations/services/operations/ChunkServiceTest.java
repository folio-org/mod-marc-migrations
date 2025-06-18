package org.folio.marc.migrations.services.operations;

import static java.util.Collections.emptyList;
import static org.folio.marc.migrations.domain.entities.types.OperationStatusType.NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.services.jdbc.AuthorityJdbcService;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.marc.migrations.services.jdbc.InstanceJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

  private @Mock MigrationProperties props;
  private @Mock AuthorityJdbcService authorityJdbcService;
  private @Mock InstanceJdbcService instanceJdbcService;
  private @Mock ChunkJdbcService chunkJdbcService;
  private @InjectMocks ChunkService service;

  @BeforeEach
  void beforeEach() {
    lenient().when(props.getChunkSize()).thenReturn(2); //number of records in one chunk
    lenient().when(props.getChunkFetchIdsCount()).thenReturn(4); //number of records to fetch in one query
    lenient().when(props.getChunkPersistCount()).thenReturn(4); //number of chunks to persist into db at a time
  }

  void prepareChunks_positive(EntityType entityType, List<List<UUID>> recordIdsMock) {
    var actualChunks = new LinkedList<OperationChunk>();
    doAnswer(invocation -> {
      actualChunks.addAll(invocation.<List<OperationChunk>>getArgument(0));
      return null;
    }).when(chunkJdbcService).createChunks(any());
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setEntityType(entityType);

    // Act
    service.prepareChunks(operation);

    // Assert
    verify(chunkJdbcService, times(2)).createChunks(any());
    assertEquals(5, actualChunks.size());

    var recordIdsPartitioned = Lists.partition(recordIdsMock.stream().flatMap(Collection::stream).toList(), 2);
    var softAssertions = new SoftAssertions();
    for (int i = 0; i < actualChunks.size(); i++) {
      var chunk = actualChunks.get(i);
      softAssertions.assertThat(chunk.getId()).isNotNull();
      softAssertions.assertThat(chunk.getOperationId()).isEqualTo(operation.getId());
      softAssertions.assertThat(chunk.getStartRecordId()).isEqualTo(recordIdsPartitioned.get(i).get(0));
      softAssertions.assertThat(chunk.getEndRecordId()).isEqualTo(recordIdsPartitioned.get(i).get(1));
      softAssertions.assertThat(
          fileNameValid(chunk.getSourceChunkFileName(), operation.getId(), chunk.getId(), "source")).isTrue();
      softAssertions.assertThat(
          fileNameValid(chunk.getMarcChunkFileName(), operation.getId(), chunk.getId(), "marc")).isTrue();
      softAssertions.assertThat(
          fileNameValid(chunk.getEntityChunkFileName(), operation.getId(), chunk.getId(), "entity")).isTrue();
      softAssertions.assertThat(chunk.getStatus()).isEqualTo(NEW);
      softAssertions.assertThat(chunk.getNumOfRecords()).isEqualTo(2);
    }
    softAssertions.assertAll();
  }

  @Test
  void prepareAuthorityChunks_positive() {
    var recordIdsMock = getRecordIdsMocks();
    when(authorityJdbcService.getAuthorityIdsChunk(any()))
        .thenReturn(recordIdsMock.get(0));
    when(authorityJdbcService.getAuthorityIdsChunk(any(), any()))
        .thenReturn(recordIdsMock.get(1), recordIdsMock.get(2), emptyList());
    prepareChunks_positive(EntityType.AUTHORITY, recordIdsMock);
  }

  @Test
  void prepareInstanceChunks_positive() {
    var recordIdsMock = getRecordIdsMocks();
    when(instanceJdbcService.getInstanceIdsChunk(any()))
        .thenReturn(recordIdsMock.get(0));
    when(instanceJdbcService.getInstanceIdsChunk(any(), any()))
        .thenReturn(recordIdsMock.get(1), recordIdsMock.get(2), emptyList());
    prepareChunks_positive(EntityType.INSTANCE, recordIdsMock);
  }

  @Test
  void updateChunkStatus_CallsJdbcServiceWithCorrectArguments() {
    // Arrange
    var chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var status = OperationStatusType.DATA_MAPPING;

    // Act
    service.updateChunkStatus(chunkIds, status);

    // Assert
    verify(chunkJdbcService).updateChunkStatus(chunkIds, status);
  }

  private List<List<UUID>> getRecordIdsMocks() {
    return List.of(
        //returned from first query and partitioned into 2 chunks
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        //returned from query in a loop and partitioned into 2 chunks. Persisted to db in a loop
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        //returned from query in a loop, 1 chunk created. Persisted to db after a loop
        List.of(UUID.randomUUID(), UUID.randomUUID()));
  }

  private boolean fileNameValid(String fileName, UUID operationId, UUID chunkId, String postfix) {
    return fileName.contains(operationId.toString()) && fileName.contains(chunkId.toString())
        && fileName.endsWith(postfix);
  }
}
