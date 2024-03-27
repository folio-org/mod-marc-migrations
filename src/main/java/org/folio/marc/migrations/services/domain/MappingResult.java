package org.folio.marc.migrations.services.domain;

public record MappingResult(String mappedRecord, String invalidMarcRecord, String errorCause) {
}
