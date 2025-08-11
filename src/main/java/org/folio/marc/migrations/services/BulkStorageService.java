package org.folio.marc.migrations.services;

import static org.folio.marc.migrations.client.BulkClient.EntityBulkType;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.marc.migrations.client.BulkClient;
import org.folio.marc.migrations.client.BulkClient.BulkRequest;
import org.folio.marc.migrations.client.BulkClient.BulkResponse;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class BulkStorageService {
  private final BulkClient bulkClient;

  public BulkResponse saveEntities(String remoteRecordsFileName, EntityType entityType, Boolean publishEventsFlag) {
    if (StringUtils.isBlank(remoteRecordsFileName)) {
      return null;
    }

    var bulkRequest = new BulkRequest(remoteRecordsFileName);
    bulkRequest.setPublishEvents(publishEventsFlag);

    try {
      return bulkClient.saveBulk(EntityBulkType.mapUri(entityType), bulkRequest);
    } catch (Exception ex) {
      log.warn("Failed to save entities with type {} specified with file path: {}",
          entityType, remoteRecordsFileName, ex);
      return null;
    }
  }
}
