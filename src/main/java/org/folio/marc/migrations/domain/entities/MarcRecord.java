package org.folio.marc.migrations.domain.entities;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.types.RecordState;

public record MarcRecord(UUID marcId, UUID recordId, Object marc, RecordState state, Integer version) {
}
