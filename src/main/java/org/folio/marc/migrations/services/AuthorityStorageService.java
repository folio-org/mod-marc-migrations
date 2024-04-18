package org.folio.marc.migrations.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.marc.migrations.client.AuthorityStorageClient;
import org.folio.marc.migrations.domain.dto.AuthorityBulkRequest;
import org.folio.marc.migrations.domain.dto.AuthorityBulkResponse;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AuthorityStorageService {

  private final AuthorityStorageClient client;

  public AuthorityBulkResponse saveAuthorities(String remoteRecordsFileName) {
    if (StringUtils.isBlank(remoteRecordsFileName)) {
      return null;
    }

    var bulkRequest = new AuthorityBulkRequest(remoteRecordsFileName);

    try {
      return client.saveAuthorityBulk(bulkRequest);
    } catch (Exception ex) {
      log.warn("Failed to save authority records specified with file path: {}", remoteRecordsFileName);
      return null;
    }
  }

}
