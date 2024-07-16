package org.folio.marc.migrations.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.marc.migrations.client.AuthorityStorageClient;
import org.folio.marc.migrations.client.InstanceStorageClient;
import org.folio.marc.migrations.domain.dto.BulkRequest;
import org.folio.marc.migrations.domain.dto.BulkResponse;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class BulkStorageService {

  private final AuthorityStorageClient authorityStorageClient;
  private final InstanceStorageClient instanceStorageClient;

  public BulkResponse saveEntities(String remoteRecordsFileName, EntityType entityType) {
    if (StringUtils.isBlank(remoteRecordsFileName)) {
      return null;
    }

    var bulkRequest = new BulkRequest(remoteRecordsFileName);

    try {
      return entityType == EntityType.AUTHORITY
        ? authorityStorageClient.saveAuthorityBulk(bulkRequest) :
          instanceStorageClient.saveInstanceBulk(bulkRequest);
    } catch (Exception ex) {
      log.warn("Failed to save entities with type {} specified with file path: {}", entityType, remoteRecordsFileName);
      return null;
    }
  }

}
