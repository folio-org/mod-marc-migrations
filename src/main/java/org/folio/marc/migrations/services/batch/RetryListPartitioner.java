package org.folio.marc.migrations.services.batch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Distributes an explicit list of retry chunk ids round-robin across up to {@code gridSize} partitions. Each partition
 * receives its sublist as a comma-joined id string ({@link #CHUNK_IDS_KEY}); the worker reader parses it back and
 * reads those chunks by id. Retry lists are bounded (chunk-retrying-max-ids-count), so storing ids in the partition
 * context is safe.
 */
@Log4j2
@RequiredArgsConstructor
public class RetryListPartitioner implements Partitioner {

  public static final String CHUNK_IDS_KEY = "chunkIds";

  private final List<UUID> chunkIds;

  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    var partitions = new HashMap<String, ExecutionContext>();
    if (chunkIds == null || chunkIds.isEmpty()) {
      log.warn("partition:: no chunkIds provided for retry");
      return partitions;
    }

    var partitionCount = Math.min(gridSize, chunkIds.size());
    List<List<UUID>> buckets = new ArrayList<>();
    for (int i = 0; i < partitionCount; i++) {
      buckets.add(new ArrayList<>());
    }
    for (int i = 0; i < chunkIds.size(); i++) {
      buckets.get(i % partitionCount).add(chunkIds.get(i));
    }

    log.info("partition:: {} retry chunk id(s) split into {} partition(s) (requested gridSize {})",
      chunkIds.size(), partitionCount, gridSize);
    for (int i = 0; i < partitionCount; i++) {
      var context = new ExecutionContext();
      context.putString(CHUNK_IDS_KEY,
        buckets.get(i).stream().map(UUID::toString).collect(Collectors.joining(",")));
      partitions.put("partition" + i, context);
    }
    return partitions;
  }
}
