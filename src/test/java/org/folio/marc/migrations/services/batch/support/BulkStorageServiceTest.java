package org.folio.marc.migrations.services.batch.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.marc.migrations.client.BulkClient;
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
class BulkStorageServiceTest {
  private static final String FILE_NAME = "test";

  private @Mock BulkClient bulkClient;
  private @InjectMocks BulkStorageService bulkStorageService;

  private final BulkResponse bulkResponse = new BulkResponse().errorsNumber(0);

  @Test
  void shouldSaveAuthorityBulk() {
    when(bulkClient.saveBulk(any(), any())).thenReturn(bulkResponse);
    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.AUTHORITY);

    verify(bulkClient)
        .saveBulk(argThat(uri -> uri.equals(BulkClient.EntityBulkType.AUTHORITY.getUri())),
                  argThat(request -> request.getRecordsFileName().equals(FILE_NAME)));

    assertThat(response).isEqualTo(bulkResponse);
  }

  @Test
  void shouldSaveInstanceBulk() {
    when(bulkClient.saveBulk(any(), any())).thenReturn(bulkResponse);
    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.INSTANCE);

    verify(bulkClient)
        .saveBulk(argThat(uri -> uri.equals(BulkClient.EntityBulkType.INSTANCE.getUri())),
                  argThat(request -> request.getRecordsFileName().equals(FILE_NAME)));

    assertThat(response).isEqualTo(bulkResponse);
  }

  @Test
  void shouldReturnNullIfFileNameBlank() {
    var response = bulkStorageService.saveEntities("", EntityType.AUTHORITY);
    assertThat(response).isNull();
  }

  @Test
  void shouldReturnNullIfErrorDuringBulkSave() {
    when(bulkClient.saveBulk(any(), any()))
        .thenThrow(new RuntimeException("Test"));

    var response = bulkStorageService.saveEntities(FILE_NAME, EntityType.AUTHORITY);
    assertThat(response).isNull();
  }
}
