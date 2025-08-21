package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SpringBatchExecutionParamsJdbcServiceTest extends JdbcServiceTestBase {
  @InjectMocks
  private SpringBatchExecutionParamsJdbcService service;

  @Test
  void getBatchExecutionParam_positive() {
    // Arrange
    var parameterName = "testParam";
    var operationId = "testOperationId";
    var expectedValue = "testValue";

    when(jdbcTemplate.query(any(String.class), any(RowMapper.class)))
        .thenReturn(Collections.singletonList(expectedValue));

    // Act
    var result = service.getBatchExecutionParam(parameterName, operationId);

    // Assert
    assertThat(result).isEqualTo(expectedValue);

    // Verify SQL execution
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
    assertThat(sqlCaptor.getValue()).contains("SELECT parameter_value")
      .contains(parameterName)
      .contains(operationId);
  }

  @Test
  void getBatchExecutionParam_negative() {
    // Arrange
    var parameterName = "testParam";
    var operationId = "testOperationId";
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(Collections.emptyList());

    // Act
    var result = service.getBatchExecutionParam(parameterName, operationId);

    // Assert
    assertThat(result).isNull();

    // Verify SQL execution
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
    assertThat(sqlCaptor.getValue()).contains("SELECT parameter_value")
      .contains(parameterName)
      .contains(operationId);
  }
}
