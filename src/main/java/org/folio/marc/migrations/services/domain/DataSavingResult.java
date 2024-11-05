package org.folio.marc.migrations.services.domain;

import org.folio.marc.migrations.client.BulkClient.BulkResponse;

public record DataSavingResult(RecordsSavingData recordsSavingData, BulkResponse saveResponse) {
}
