package org.folio.marc.migrations.services.domain;

import org.folio.marc.migrations.domain.dto.AuthorityBulkResponse;

public record DataSavingResult(RecordsSavingData recordsSavingData, AuthorityBulkResponse saveResponse) {
}
