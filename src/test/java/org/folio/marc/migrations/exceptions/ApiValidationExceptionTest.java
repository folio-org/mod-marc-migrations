package org.folio.marc.migrations.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
