package org.folio.marc.migrations.services.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.MarcRecord;
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
class AuthorityJdbcServiceTest extends JdbcServiceTestBase {

  private @Mock BeanPropertyRowMapper<MarcRecord> recordsMapper;
  private @InjectMocks AuthorityJdbcService service;

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
      .contains(id.toString(), String.valueOf(limit), TENANT_ID);
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
    assertThat(sqlCaptor.getValue())
      .contains(String.valueOf(limit), TENANT_ID);
  }

  @Test
  void getAuthorityIdsChunk_positive_onlyLimit() {
    // Arrange
    var limit = 5;
    var chunksMock = List.of(UUID.randomUUID(), UUID.randomUUID());
    var serviceSpy = spy(service);
    when(serviceSpy.getAuthorityIdsChunk(isNull(), eq(limit))).thenReturn(chunksMock);

    // Act
    var chunks = serviceSpy.getAuthorityIdsChunk(limit);

    // Assert
    assertThat(chunks).isEqualTo(chunksMock);
    verify(serviceSpy).getAuthorityIdsChunk(null, limit);
  }

  @Test
  void getAuthoritiesChunk_positive() {
    // Arrange
    var idFrom = UUID.randomUUID();
    var idTo = UUID.randomUUID();
    var chunkMock = List.of(new MarcRecord(UUID.randomUUID(), null, null, null, null),
      new MarcRecord(UUID.randomUUID(), null, null, null, null));
    when(jdbcTemplate.query(any(String.class), ArgumentMatchers.<BeanPropertyRowMapper<MarcRecord>>any()))
      .thenReturn(chunkMock);

    // Act
    var chunk = service.getAuthoritiesChunk(idFrom, idTo);

    // Assert
    assertThat(chunk).isEqualTo(chunkMock);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sqlCaptor.capture(), ArgumentMatchers.<BeanPropertyRowMapper<MarcRecord>>any());
    assertThat(sqlCaptor.getValue())
      .contains(idFrom.toString(), idTo.toString(), TENANT_ID);
  }
}
