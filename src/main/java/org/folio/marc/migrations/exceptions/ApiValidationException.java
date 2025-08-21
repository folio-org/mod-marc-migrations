package org.folio.marc.migrations.exceptions;

import lombok.Getter;

@Getter
public class ApiValidationException extends RuntimeException {

  private static final String MSG_TEMPLATE = "Unexpected value '%s' in field '%s'";
  private static final String NOT_ALLOWED_ACTION = "Not allowed %s action for operation with value '%s' in field '%s'";
  private static final String MAX_SIZE_EXCEEDED = "The maximum allowed number of chunk IDs is '%s', but received '%s'.";
  private static final String STATUS = "status";

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

  public ApiValidationException(int maxSize, int actualSize) {
    super(MAX_SIZE_EXCEEDED.formatted(maxSize, actualSize));
    this.fieldName = null;
    this.fieldValue = null;
  }

  public ApiValidationException(String errorMessage) {
    super(errorMessage);
    this.fieldName = null;
    this.fieldValue = null;
  }

  public static ApiValidationException forOperationType(String value) {
    return new ApiValidationException("operationType", value);
  }

  public static ApiValidationException forEntityType(String value) {
    return new ApiValidationException("entityType", value);
  }

  public static ApiValidationException forOperationStatus(String value) {
    return new ApiValidationException(STATUS, value);
  }

  public static ApiValidationException notAllowedSaveForOperationStatus(String value) {
    return new ApiValidationException("data save", STATUS, value);
  }

  public static ApiValidationException notAllowedRetryForOperationStatus(String value) {
    return new ApiValidationException("retry", STATUS, value);
  }

  public static ApiValidationException maxSizeExceeded(int maxSize, int actualSize) {
    return new ApiValidationException(maxSize, actualSize);
  }
}
