package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChunkJdbcServiceTest extends JdbcServiceTestBase {

  private @Mock BeanPropertyRowMapper<MarcRecord> recordsMapper;
  private @InjectMocks ChunkJdbcService service;

  @Test
  void getChunks_positive() {
    // Arrange
    var operationId = UUID.randomUUID().toString();
    var idFrom = UUID.randomUUID();
    var limit = 5;
    var chunksMock = List.of(OperationChunk.builder().id(UUID.randomUUID()).build(),
      OperationChunk.builder().id(UUID.randomUUID()).build());
    when(jdbcTemplate.query(any(String.class), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any()))
      .thenReturn(chunksMock);

    // Act
    var chunks = service.getChunks(operationId, idFrom, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any());
    assertThat(sqlCaptor.getValue())
      .contains(operationId, idFrom.toString(), String.valueOf(limit), TENANT_ID);
  }

  @Test
  void getChunks_positive_withoutSeek() {
    // Arrange
    var operationId = UUID.randomUUID().toString();
    var limit = 5;
    var chunksMock = List.of(OperationChunk.builder().id(UUID.randomUUID()).build(),
      OperationChunk.builder().id(UUID.randomUUID()).build());
    when(jdbcTemplate.query(any(String.class), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any()))
      .thenReturn(chunksMock);

    // Act
    var chunks = service.getChunks(operationId, null, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any());
    assertThat(sqlCaptor.getValue())
      .doesNotContain("AND id >");
    assertThat(sqlCaptor.getValue())
      .contains(String.valueOf(limit), TENANT_ID);
  }

  @Test
  void createChunks_positive() {
    var chunksMock = List.of(OperationChunk.builder().id(UUID.randomUUID()).build(),
      OperationChunk.builder().id(UUID.randomUUID()).build());

    service.createChunks(chunksMock);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(chunksMock), eq(chunksMock.size()), any());
    assertThat(sqlCaptor.getValue())
      .contains(TENANT_ID);
  }

  @Test
  void createChunks_negative_emptyChunks() {
    var chunksMock = List.<OperationChunk>of();

    service.createChunks(chunksMock);

    verifyNoInteractions(context);
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void createChunks_negative_batchUpdateException() {
    var exMessage = "exception";
    var chunksMock = List.of(OperationChunk.builder().id(UUID.randomUUID()).build(),
      OperationChunk.builder().id(UUID.randomUUID()).build());
    when(jdbcTemplate.batchUpdate(any(), eq(chunksMock), eq(chunksMock.size()), any()))
      .thenThrow(new IllegalArgumentException(exMessage));

    var ex = assertThrows(IllegalStateException.class, () -> service.createChunks(chunksMock));

    assertThat(ex.getMessage()).isEqualTo("java.lang.IllegalArgumentException: " + exMessage);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(chunksMock), eq(chunksMock.size()), any());
    assertThat(sqlCaptor.getValue())
      .contains(TENANT_ID);
  }

  @Test
  void updateChunk_positive() {
    var id = UUID.randomUUID();
    var status = OperationStatusType.DATA_MAPPING_COMPLETED;

    service.updateChunk(id, status);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains(id.toString(), status.name(), TENANT_ID);
  }

  @Test
  void getChunks_ReturnsExpectedResult() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var expectedChunks = List.of(OperationChunk.builder()
      .id(UUID.randomUUID())
      .build(),
        OperationChunk.builder()
          .id(UUID.randomUUID())
          .build());

    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any(),
        eq(ids.toArray())))
      .thenReturn(expectedChunks);

    // Act
    var result = service.getChunks(ids);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), ArgumentMatchers.<BeanPropertyRowMapper<OperationChunk>>any(),
        eq(ids.toArray()));
    assertThat(sqlCaptor.getValue()).contains("operation_chunk")
      .contains("id IN");
    assertThat(result).isEqualTo(expectedChunks);
  }

  @Test
  void updateChunkStatus_UpdatesSuccessfully() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var status = OperationStatusType.DATA_MAPPING;

    // Act
    service.updateChunkStatus(ids, status);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(ids), eq(ids.size()), any());
    assertThat(sqlCaptor.getValue()).contains("operation_chunk")
      .contains("status =")
      .contains("id =");
  }

  @Test
  void updateChunkStatus_negative_batchUpdateException() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var status = OperationStatusType.DATA_MAPPING;
    var exceptionMessage = "Batch update failed";
    when(jdbcTemplate.batchUpdate(anyString(), eq(ids), eq(ids.size()), any()))
        .thenThrow(new IllegalArgumentException(exceptionMessage));

    // Act & Assert
    var exception = assertThrows(IllegalStateException.class, () -> service.updateChunkStatus(ids, status));
    assertThat(exception.getMessage()).contains(exceptionMessage);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(ids), eq(ids.size()), any());
    assertThat(sqlCaptor.getValue()).contains("operation_chunk").contains("status =").contains("id =");
  }

  @Test
  void updateChunkStatus_negative_nullIds() {
    // Act
    service.updateChunkStatus(null, OperationStatusType.DATA_MAPPING);

    // Assert
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void updateChunkStatus_negative_emptyIds() {
    // Act
    service.updateChunkStatus(List.of(), OperationStatusType.DATA_MAPPING);

    // Assert
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void getNumberOfRecords_ReturnsExpectedCount() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var expectedCount = 42;

    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ids.toArray()))).thenReturn(expectedCount);

    // Act
    var result = service.getNumberOfRecords(ids);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Integer.class), eq(ids.toArray()));
    assertThat(sqlCaptor.getValue()).contains("operation_chunk")
      .contains("SUM(num_of_records)");
    assertThat(result).isEqualTo(expectedCount);
  }

  @Test
  void getNumberOfRecords_ReturnsZeroForEmptyIds() {
    // Act
    var result = service.getNumberOfRecords(List.of());

    // Assert
    assertThat(result).isZero();
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void getNumberOfRecords_ReturnsZeroForNullResult() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ids.toArray())))
        .thenReturn(null);

    // Act
    var result = service.getNumberOfRecords(ids);

    // Assert
    assertThat(result).isZero();
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Integer.class), eq(ids.toArray()));
    assertThat(sqlCaptor.getValue()).contains("operation_chunk").contains("SUM(num_of_records");
  }

  @Test
  void getNumberOfRecords_negative_queryForObjectException() {
    // Arrange
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var exceptionMessage = "Query failed";
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ids.toArray())))
        .thenThrow(new IllegalArgumentException(exceptionMessage));

    // Act & Assert
    var exception = assertThrows(IllegalStateException.class, () -> service.getNumberOfRecords(ids));
    assertThat(exception.getMessage()).contains(exceptionMessage);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Integer.class), eq(ids.toArray()));
    assertThat(sqlCaptor.getValue()).contains("operation_chunk").contains("SUM(num_of_records");
  }
}
