package org.folio.marc.migrations.services.domain;

import org.folio.marc.migrations.domain.dto.BulkResponse;

public record DataSavingResult(RecordsSavingData recordsSavingData, BulkResponse saveResponse) {
}
