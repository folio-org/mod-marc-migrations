package org.folio.marc.migrations.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JdbcServiceTest {

  private @Mock JdbcTemplate jdbcTemplate;
  private @Mock FolioModuleMetadata metadata;
  private @Mock FolioExecutionContext context;
  private @InjectMocks JdbcService service;

  @BeforeEach
  void setUp() {
    when(context.getFolioModuleMetadata()).thenReturn(metadata);
  }

  @Test
  void updateStatus_updatesStatus() {
    // Arrange
    var operationId = UUID.randomUUID();
    var operationStatus = OperationStatusType.DATA_MAPPING;

    // Act
    service.updateOperationStatus(operationId, operationStatus);

    // Assert
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).execute(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
      .contains(operationId.toString(), operationStatus.toString());
  }

  @Test
  void getAuthorityIdsChunk_positive() {
    // Arrange
    var id = UUID.randomUUID();
    var limit = 5;
    var chunksMock = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(jdbcTemplate.queryForList(any(), eq(UUID.class))).thenReturn(chunksMock);

    // Act
    var chunks = service.getAuthorityIdsChunk(id, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(UUID.class));
    assertThat(sqlCaptor.getValue())
      .contains(id.toString(), String.valueOf(limit));
  }

  @Test
  void getAuthorityIdsChunk_positive_withoutSeek() {
    // Arrange
    var limit = 5;
    var chunksMock = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(jdbcTemplate.queryForList(any(), eq(UUID.class))).thenReturn(chunksMock);

    // Act
    var chunks = service.getAuthorityIdsChunk(null, limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(UUID.class));
    assertThat(sqlCaptor.getValue())
      .doesNotContain("WHERE");
  }

  @Test
  void getAuthorityIdsChunk_positive_onlyLimit() {
    // Arrange
    var limit = 5;
    var chunksMock = List.of(UUID.randomUUID(), UUID.randomUUID());
    var serviceSpy = spy(service);
    when(serviceSpy.getAuthorityIdsChunk(null, limit)).thenReturn(chunksMock);

    // Act
    var chunks = serviceSpy.getAuthorityIdsChunk(limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    verify(serviceSpy).getAuthorityIdsChunk(null, limit);
  }
}
