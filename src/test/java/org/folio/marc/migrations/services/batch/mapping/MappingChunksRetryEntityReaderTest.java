package org.folio.marc.migrations.services.batch.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingChunksRetryEntityReaderTest {
  private ChunkJdbcService jdbcService;
  private List<UUID> chunkIds;
  private MappingChunksRetryEntityReader reader;

  @BeforeEach
  void setUp() {
    jdbcService = mock(ChunkJdbcService.class);
    chunkIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    reader = new MappingChunksRetryEntityReader(jdbcService, chunkIds);
  }

  @Test
  void read_FetchesChunksAndReturnsThemSequentially() {
    // Arrange
    var operationChunks = List.of(OperationChunk.builder()
      .id(chunkIds.get(0))
      .build(),
        OperationChunk.builder()
          .id(chunkIds.get(1))
          .build());
    when(jdbcService.getChunks(chunkIds)).thenReturn(operationChunks);

    // Act & Assert
    var firstChunk = reader.read();
    assertThat(firstChunk).isNotNull();
    assertThat(firstChunk.getId()).isEqualTo(chunkIds.getFirst());

    var secondChunk = reader.read();
    assertThat(secondChunk).isNotNull();
    assertThat(secondChunk.getId()).isEqualTo(chunkIds.get(1));

    var thirdChunk = reader.read();
    assertThat(thirdChunk).isNull();

    verify(jdbcService).getChunks(chunkIds);
  }

  @Test
  void read_ReturnsNullWhenAllChunksAreProcessed() {
    // Arrange
    var operationChunks = List.of(OperationChunk.builder()
      .id(chunkIds.getFirst())
      .build());
    when(jdbcService.getChunks(chunkIds)).thenReturn(operationChunks);

    // Act
    reader.read(); // First chunk
    var result = reader.read(); // No more chunks

    // Assert
    assertThat(result).isNull();
    verify(jdbcService).getChunks(chunkIds);
  }
}
