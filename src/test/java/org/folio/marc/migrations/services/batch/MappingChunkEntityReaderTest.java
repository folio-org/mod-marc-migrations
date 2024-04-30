package org.folio.marc.migrations.services.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.marc.migrations.config.MigrationProperties;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.batch.mapping.MappingChunkEntityReader;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingChunkEntityReaderTest {

  private String operationId;
  private MigrationProperties props;
  private ChunkJdbcService jdbcService;
  private MappingChunkEntityReader reader;

  @BeforeEach
  void setUp() {
    operationId = UUID.randomUUID().toString();
    props = mock(MigrationProperties.class);
    jdbcService = mock(ChunkJdbcService.class);
    reader = new MappingChunkEntityReader(operationId, props, jdbcService);
  }

  @Test
  void read_positive() {
    var chunksCount = 3;
    var firstChunksRead = chunks(chunksCount);
    var secondChunksRead = chunks(chunksCount - 1);

    when(props.getChunkPersistCount()).thenReturn(chunksCount);
    when(jdbcService.getChunks(operationId, null, chunksCount))
      .thenReturn(firstChunksRead);
    when(jdbcService.getChunks(operationId, firstChunksRead.get(chunksCount - 1).getId(), chunksCount))
      .thenReturn(secondChunksRead);

    var actualChunks = new LinkedList<OperationChunk>();
    for (var chunk = reader.read(); chunk != null; chunk = reader.read()) {
      actualChunks.add(chunk);
    }

    assertThat(actualChunks)
      .hasSize(firstChunksRead.size() + secondChunksRead.size())
      .allMatch(Objects::nonNull);
    verify(jdbcService, times(2)).getChunks(eq(operationId), any(), eq(chunksCount));
  }

  private List<OperationChunk> chunks(int count) {
    var chunks = Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i -> new OperationChunk())
      .toList();
    chunks.get(count - 1).setId(UUID.randomUUID());
    return chunks;
  }
}
