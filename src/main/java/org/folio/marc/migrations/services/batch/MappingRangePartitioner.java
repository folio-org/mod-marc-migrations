package org.folio.marc.migrations.services.batch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Splits an operation's {@code operation_chunk}s into up to {@code gridSize} contiguous, equally-sized partitions by
 * id order. Each partition is handed an inclusive upper bound ({@link #TO_ID_KEY}) and, for all but the first, an
 * exclusive lower bound ({@link #FROM_ID_KEY}); the worker reader uses these as a keyset window {@code (fromId, toId]}.
 */
@Log4j2
@RequiredArgsConstructor
public class MappingRangePartitioner implements Partitioner {

  public static final String FROM_ID_KEY = "fromId";
  public static final String TO_ID_KEY = "toId";

  private final String operationId;
  private final ChunkJdbcService jdbcService;

  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    var boundaries = jdbcService.getChunkRangeBoundaries(operationId, gridSize);
    log.info("partition:: operation {} split into {} partition(s) (requested gridSize {})",
      operationId, boundaries.size(), gridSize);

    var partitions = new HashMap<String, ExecutionContext>();
    UUID fromId = null;
    for (int i = 0; i < boundaries.size(); i++) {
      var toId = boundaries.get(i);
      var context = new ExecutionContext();
      if (fromId != null) {
        context.putString(FROM_ID_KEY, fromId.toString());
      }
      context.putString(TO_ID_KEY, toId.toString());
      partitions.put("partition" + i, context);
      fromId = toId;
    }
    return partitions;
  }
}
