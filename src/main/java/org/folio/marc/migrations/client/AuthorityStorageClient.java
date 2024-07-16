package org.folio.marc.migrations.client;

import org.folio.marc.migrations.domain.dto.BulkRequest;
import org.folio.marc.migrations.domain.dto.BulkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("authority-storage")
public interface AuthorityStorageClient {

  @PostMapping("/authorities/bulk")
  BulkResponse saveAuthorityBulk(BulkRequest saveRequest);
}
