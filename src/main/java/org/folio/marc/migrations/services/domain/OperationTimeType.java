package org.folio.marc.migrations.services.domain;

import lombok.Getter;

public enum OperationTimeType {
  MAPPING_START("start_time_mapping"),
  MAPPING_END("end_time_mapping"),
  SAVING_START("start_time_saving"),
  SAVING_END("end_time_saving");

  @Getter
  private final String dbColumnName;

  OperationTimeType(String dbColumnName) {
    this.dbColumnName = dbColumnName;
  }
}
