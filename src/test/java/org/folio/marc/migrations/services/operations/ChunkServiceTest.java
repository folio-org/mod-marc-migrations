package org.folio.marc.migrations.services.operations;

import static java.util.Collections.emptyList;
import static org.folio.marc.migrations.domain.entities.types.OperationStatusType.NEW;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.folio.marc.migrations.domain.repositories.OperationChunkRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

  private @Mock FolioModuleMetadata metadata;
  private @Mock MigrationProperties props;
  private @Mock FolioExecutionContext context;
  private @Mock JdbcTemplate jdbcTemplate;
  private @Mock OperationChunkRepository repository;
  private @InjectMocks ChunkService service;

  @Test
  void prepareChunks_positive() {
    // Arrange
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(props.getChunkSize()).thenReturn(2); //number of records in one chunk
    when(props.getChunkFetchIdsCount()).thenReturn(4); //number of records to fetch in one query
    when(props.getChunkPersistCount()).thenReturn(4); //number of chunks to persist into db at a time
    var recordIdsMock = List.of(
        //returned from first query and partitioned into 2 chunks
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        //returned from query in a loop and partitioned into 2 chunks. Persisted to db in a loop
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        //returned from query in a loop, 1 chunk created. Persisted to db after a loop
        List.of(UUID.randomUUID(), UUID.randomUUID()));
    when(jdbcTemplate.queryForList(any(), eq(UUID.class)))
        .thenReturn(recordIdsMock.get(0), recordIdsMock.get(1), recordIdsMock.get(2), emptyList());
    var actualChunks = new LinkedList<OperationChunk>();
    doAnswer(invocation -> {
      actualChunks.addAll(invocation.<List<OperationChunk>>getArgument(0));
      return null;
    }).when(repository).saveAll(any());
    var operation = new Operation();
    operation.setId(UUID.randomUUID());
    operation.setEntityType(EntityType.AUTHORITY);

    // Act
    service.prepareChunks(operation);

    // Assert
    verify(repository, times(2)).saveAll(any());
    assertEquals(5, actualChunks.size());

    var recordIdsPartitioned = Lists.partition(recordIdsMock.stream().flatMap(Collection::stream).toList(), 2);
    var softAssertions = new SoftAssertions();
    for (int i = 0; i < actualChunks.size(); i++) {
      var chunk = actualChunks.get(i);
      softAssertions.assertThat(chunk.getId()).isNotNull();
      softAssertions.assertThat(chunk.getOperation()).isEqualTo(operation);
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
  void prepareChunks_failOnUnsupportedEntity() {
    var operation = new Operation();
    operation.setEntityType(EntityType.INSTANCE);

    // Act
    var exc = assertThrows(UnsupportedOperationException.class, () -> service.prepareChunks(operation));

    // Assert
    assertEquals("prepareChunks:: Unsupported entity type: INSTANCE", exc.getMessage());
    verifyNoInteractions(jdbcTemplate, repository);
  }

  private boolean fileNameValid(String fileName, UUID operationId, UUID chunkId, String postfix) {
    return fileName.contains(operationId.toString()) && fileName.contains(chunkId.toString())
        && fileName.endsWith(postfix);
  }
}
