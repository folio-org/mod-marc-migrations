package org.folio.marc.migrations.services.batch.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.marc.migrations.client.AuthorityStorageClient;
import org.folio.marc.migrations.client.InstanceStorageClient;
import org.folio.marc.migrations.domain.dto.BulkResponse;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.services.BulkStorageService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class BulkStorageServiceTest {
  private static final String FILE_NAME = "test";

  private @Mock AuthorityStorageClient authorityStorageClient;
  private @Mock InstanceStorageClient instanceStorageClient;
  private @InjectMocks BulkStorageService bulkStorageService;

  @Test
  void shouldSaveAuthorityBulk() {
    BulkResponse bulkResponse = new BulkResponse();
    bulkResponse.errorsNumber(0);

    when(authorityStorageClient.saveAuthorityBulk(any()))
        .thenReturn(bulkResponse);

    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.AUTHORITY);

    verify(authorityStorageClient)
        .saveAuthorityBulk(argThat(request -> request.getRecordsFileName().equals(FILE_NAME)));
    assertThat(response).isEqualTo(bulkResponse);
  }

  @Test
  void shouldSaveInstanceBulk() {
    BulkResponse bulkResponse = new BulkResponse();
    bulkResponse.errorsNumber(0);

    when(instanceStorageClient.saveInstanceBulk(any()))
        .thenReturn(bulkResponse);

    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.INSTANCE);

    verify(instanceStorageClient).saveInstanceBulk(argThat(request -> request.getRecordsFileName().equals(FILE_NAME)));
    assertThat(response).isEqualTo(bulkResponse);
  }

  @Test
  void shouldReturnNullIfFileNameBlank() {
    var response = bulkStorageService.saveEntities("", EntityType.AUTHORITY);
    assertThat(response).isNull();
  }

  @Test
  void shouldReturnNullIfErrorDuringBulkSave() {
    when(authorityStorageClient.saveAuthorityBulk(any()))
        .thenThrow(new RuntimeException("Test"));

    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.AUTHORITY);
    assertThat(response).isNull();
  }
}
