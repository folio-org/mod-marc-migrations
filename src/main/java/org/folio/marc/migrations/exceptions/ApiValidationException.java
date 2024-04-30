package org.folio.marc.migrations.exceptions;

import lombok.Getter;

@Getter
public class ApiValidationException extends RuntimeException {

  private static final String MSG_TEMPLATE = "Unexpected value '%s' in field '%s'";
  private static final String NOT_ALLOWED_ACTION = "Not allowed %s action for operation with value '%s' in field '%s'";

  private final String fieldName;
  private final String fieldValue;

  public ApiValidationException(String fieldName, String fieldValue) {
    super(MSG_TEMPLATE.formatted(fieldValue, fieldName));
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public ApiValidationException(String action, String fieldName, String fieldValue) {
    super(NOT_ALLOWED_ACTION.formatted(action, fieldValue, fieldName));
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public static ApiValidationException forOperationType(String value) {
    return new ApiValidationException("operationType", value);
  }

  public static ApiValidationException forEntityType(String value) {
    return new ApiValidationException("entityType", value);
  }

  public static ApiValidationException forOperationStatus(String value) {
    return new ApiValidationException("status", value);
  }

  public static ApiValidationException notAllowedSaveForOperationStatus(String value) {
    return new ApiValidationException("data save", "status", value);
  }
}
