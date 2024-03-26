package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    when(jdbcTemplate.query(any(String.class), any(BeanPropertyRowMapper.class))).thenReturn(chunksMock);

    // Act
    var chunks = service.getChunks(operationId, idFrom, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), any(BeanPropertyRowMapper.class));
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
    when(jdbcTemplate.query(any(String.class), any(BeanPropertyRowMapper.class))).thenReturn(chunksMock);

    // Act
    var chunks = service.getChunks(operationId, null, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), any(BeanPropertyRowMapper.class));
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
}
