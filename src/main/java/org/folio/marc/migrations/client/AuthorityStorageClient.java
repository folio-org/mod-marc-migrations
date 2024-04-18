package org.folio.marc.migrations.client;

import org.folio.marc.migrations.domain.dto.AuthorityBulkRequest;
import org.folio.marc.migrations.domain.dto.AuthorityBulkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("authority-storage")
public interface AuthorityStorageClient {

  @PostMapping("/authorities/bulk")
  AuthorityBulkResponse saveAuthorityBulk(AuthorityBulkRequest saveRequest);
}
