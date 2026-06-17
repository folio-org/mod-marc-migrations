package org.folio.marc.migrations.services.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RetryListPartitionerTest {

  @Test
  void partition_splitsList_intoGridSizeBuckets() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    var partitions = new RetryListPartitioner(ids).partition(2);

    assertThat(partitions).hasSize(2);
    var allIds = partitions.values().stream()
      .flatMap(ctx -> Arrays.stream(ctx.getString(RetryListPartitioner.CHUNK_IDS_KEY).split(",")))
      .map(UUID::fromString)
      .toList();
    assertThat(allIds).containsExactlyInAnyOrderElementsOf(ids);
  }

  @Test
  void partition_distributesRoundRobin() {
    var id0 = UUID.randomUUID();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var id3 = UUID.randomUUID();

    var partitions = new RetryListPartitioner(List.of(id0, id1, id2, id3)).partition(2);

    var p0Ids = Arrays.stream(partitions.get("partition0").getString(RetryListPartitioner.CHUNK_IDS_KEY).split(","))
      .map(UUID::fromString).toList();
    var p1Ids = Arrays.stream(partitions.get("partition1").getString(RetryListPartitioner.CHUNK_IDS_KEY).split(","))
      .map(UUID::fromString).toList();

    assertThat(p0Ids).containsExactly(id0, id2);
    assertThat(p1Ids).containsExactly(id1, id3);
  }

  @Test
  void partition_fewerIdsThanGridSize_createsOnlyNecessaryPartitions() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());

    var partitions = new RetryListPartitioner(ids).partition(4);

    assertThat(partitions).hasSize(2);
  }

  @Test
  void partition_singleId_createsSinglePartition() {
    var id = UUID.randomUUID();

    var partitions = new RetryListPartitioner(List.of(id)).partition(4);

    assertThat(partitions).hasSize(1);
    assertThat(partitions.get("partition0").getString(RetryListPartitioner.CHUNK_IDS_KEY))
      .isEqualTo(id.toString());
  }

  @Test
  void partition_returnsEmptyMap_whenIdsListIsEmpty() {
    var partitions = new RetryListPartitioner(List.of()).partition(4);

    assertThat(partitions).isEmpty();
  }

  @Test
  void partition_returnsEmptyMap_whenIdsListIsNull() {
    var partitions = new RetryListPartitioner(null).partition(4);

    assertThat(partitions).isEmpty();
  }
}
