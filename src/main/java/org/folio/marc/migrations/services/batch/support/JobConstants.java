package org.folio.marc.migrations.services.batch.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JobConstants {

  public static final String OPERATION_FILES_PATH = "operation/%s/";
  public static final String JOB_FILES_PATH = "%s/%s";

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class JobParameterNames {
    public static final String OPERATION_ID = "operationId";
    public static final String ENTITY_TYPE = "entityType";
  }
}
