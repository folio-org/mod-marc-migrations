package org.folio.marc.migrations.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ApiValidationExceptionTest {

  @Test
  void apiValidationException_ConstructedWithFieldAndValue_CorrectlySetsValues() {
    // Arrange
    String fieldName = "operationType";
    String fieldValue = "invalidValue";

    // Act
    ApiValidationException exception = new ApiValidationException(fieldName, fieldValue);

    // Assert
    assertEquals(fieldName, exception.getFieldName());
    assertEquals(fieldValue, exception.getFieldValue());
    assertEquals("Unexpected value 'invalidValue' in field 'operationType'", exception.getMessage());
  }

  @Test
  void forOperationType_StaticMethod_CreatesExceptionWithCorrectFieldAndValue() {
    // Arrange
    String operationTypeValue = "invalidOperationTypeValue";

    // Act
    ApiValidationException exception = ApiValidationException.forOperationType(operationTypeValue);

    // Assert
    assertEquals("operationType", exception.getFieldName());
    assertEquals(operationTypeValue, exception.getFieldValue());
    assertEquals("Unexpected value 'invalidOperationTypeValue' in field 'operationType'", exception.getMessage());
  }

  @Test
  void forEntityType_StaticMethod_CreatesExceptionWithCorrectFieldAndValue() {
    // Arrange
    String entityTypeValue = "invalidEntityTypeValue";

    // Act
    ApiValidationException exception = ApiValidationException.forEntityType(entityTypeValue);

    // Assert
    assertEquals("entityType", exception.getFieldName());
    assertEquals(entityTypeValue, exception.getFieldValue());
    assertEquals("Unexpected value 'invalidEntityTypeValue' in field 'entityType'", exception.getMessage());
  }

  @Test
  void maxSizeExceeded_CorrectlySetsMessage() {
    // Act
    var exception = ApiValidationException.maxSizeExceeded(1000, 1100);

    // Assert
    assertEquals("The maximum allowed number of chunk IDs is '1000', but received '1100'.", exception.getMessage());
    assertNull(exception.getFieldName());
    assertNull(exception.getFieldValue());
  }

  @Test
  void notAllowedRetryForOperationStatus_CreatesExceptionWithCorrectMessage() {
    // Arrange
    var statusValue = "INVALID_STATUS";

    // Act
    var exception = ApiValidationException.notAllowedRetryForOperationStatus(statusValue);

    // Assert
    assertEquals("status", exception.getFieldName());
    assertEquals(statusValue, exception.getFieldValue());
    assertEquals("Not allowed retry action for operation with value '%s' in field 'status'".formatted(statusValue),
        exception.getMessage());
  }
}
