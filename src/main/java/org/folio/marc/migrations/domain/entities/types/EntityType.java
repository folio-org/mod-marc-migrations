package org.folio.marc.migrations.domain.entities.types;

import lombok.Getter;

@Getter
public enum EntityType {

  AUTHORITY("marc-authority"),
  INSTANCE("marc-bib");

  public final String mappingMetadataRecordType;

  EntityType(String mappingMetadataRecordType) {
    this.mappingMetadataRecordType = mappingMetadataRecordType;
  }
}

