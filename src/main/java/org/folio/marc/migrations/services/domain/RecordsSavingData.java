package org.folio.marc.migrations.services.domain;

import java.util.UUID;

public record RecordsSavingData(UUID operationId, UUID chunkId, UUID stepId, int numberOfRecords) {
}
