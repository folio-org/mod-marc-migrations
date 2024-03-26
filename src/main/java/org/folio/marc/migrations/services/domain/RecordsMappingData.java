package org.folio.marc.migrations.services.domain;

import java.util.UUID;

public record RecordsMappingData(UUID operationId, UUID chunkId, UUID stepId, String entityChunkFile,
                                 int numberOfRecords, String entityErrorChunkFileName, String errorChunkFileName) {
}
