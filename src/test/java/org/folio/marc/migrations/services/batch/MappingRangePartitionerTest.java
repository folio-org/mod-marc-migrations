package org.folio.marc.migrations.services.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MappingRangePartitionerTest {

  private ChunkJdbcService jdbcService;
  private String operationId;

  @BeforeEach
  void setUp() {
    jdbcService = mock(ChunkJdbcService.class);
    operationId = UUID.randomUUID().toString();
  }

  @Test
  void partition_createsOnePartitionPerBoundary() {
    var b0 = UUID.randomUUID();
    var b1 = UUID.randomUUID();
    var b2 = UUID.randomUUID();
    var b3 = UUID.randomUUID();
    when(jdbcService.getChunkRangeBoundaries(operationId, 4)).thenReturn(List.of(b0, b1, b2, b3));

    var partitioner = new MappingRangePartitioner(operationId, jdbcService);
    var partitions = partitioner.partition(4);

    assertThat(partitions).hasSize(4);
    verify(jdbcService).getChunkRangeBoundaries(operationId, 4);
  }

  @Test
  void partition_firstPartitionHasNoFromId() {
    var b0 = UUID.randomUUID();
    var b1 = UUID.randomUUID();
    when(jdbcService.getChunkRangeBoundaries(operationId, 2)).thenReturn(List.of(b0, b1));

    var partitions = new MappingRangePartitioner(operationId, jdbcService).partition(2);

    var first = partitions.get("partition0");
    assertThat(first).isNotNull();
    assertThat(first.containsKey(MappingRangePartitioner.FROM_ID_KEY)).isFalse();
    assertThat(first.getString(MappingRangePartitioner.TO_ID_KEY)).isEqualTo(b0.toString());
  }

  @Test
  void partition_subsequentPartitionsHaveFromIdEqualToPreviousBoundary() {
    var b0 = UUID.randomUUID();
    var b1 = UUID.randomUUID();
    var b2 = UUID.randomUUID();
    when(jdbcService.getChunkRangeBoundaries(operationId, 3)).thenReturn(List.of(b0, b1, b2));

    var partitions = new MappingRangePartitioner(operationId, jdbcService).partition(3);

    var second = partitions.get("partition1");
    assertThat(second.getString(MappingRangePartitioner.FROM_ID_KEY)).isEqualTo(b0.toString());
    assertThat(second.getString(MappingRangePartitioner.TO_ID_KEY)).isEqualTo(b1.toString());

    var third = partitions.get("partition2");
    assertThat(third.getString(MappingRangePartitioner.FROM_ID_KEY)).isEqualTo(b1.toString());
    assertThat(third.getString(MappingRangePartitioner.TO_ID_KEY)).isEqualTo(b2.toString());
  }

  @Test
  void partition_returnsEmptyMapWhenNoBoundaries() {
    when(jdbcService.getChunkRangeBoundaries(operationId, 4)).thenReturn(Collections.emptyList());

    var partitions = new MappingRangePartitioner(operationId, jdbcService).partition(4);

    assertThat(partitions).isEmpty();
  }

  @Test
  void partition_fewerChunksThanGridSizeProducesFewerPartitions() {
    var b0 = UUID.randomUUID();
    when(jdbcService.getChunkRangeBoundaries(operationId, 4)).thenReturn(List.of(b0));

    var partitions = new MappingRangePartitioner(operationId, jdbcService).partition(4);

    assertThat(partitions).hasSize(1);
    var only = partitions.get("partition0");
    assertThat(only.containsKey(MappingRangePartitioner.FROM_ID_KEY)).isFalse();
    assertThat(only.getString(MappingRangePartitioner.TO_ID_KEY)).isEqualTo(b0.toString());
  }
}
