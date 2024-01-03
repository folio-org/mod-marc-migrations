package org.folio.marc.migrations.exceptions;

import lombok.Getter;

@Getter
public class ApiValidationException extends RuntimeException {

  private static final String MSG_TEMPLATE = "Unexpected value '%s' in field '%s'";

  private final String fieldName;
  private final String fieldValue;

  public ApiValidationException(String fieldName, String fieldValue) {
    super(MSG_TEMPLATE.formatted(fieldValue, fieldName));
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public static ApiValidationException forOperationType(String value) {
    return new ApiValidationException("operationType", value);
  }

  public static ApiValidationException forEntityType(String value) {
    return new ApiValidationException("entityType", value);
  }
}
