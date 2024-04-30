package org.folio.marc.migrations.services.domain;

public record MappingResult(String mappedRecord, String invalidMarcRecord, String errorCause) {
  public MappingResult(String mappedRecord) {
    this(mappedRecord, null, null);
  }

  public MappingResult(String invalidMarcRecord, String errorCause) {
    this(null, invalidMarcRecord, errorCause);
  }
}
