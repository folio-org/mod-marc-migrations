package org.folio.marc.migrations.client;

import org.folio.marc.migrations.domain.dto.BulkRequest;
import org.folio.marc.migrations.domain.dto.BulkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("instance-storage")
public interface InstanceStorageClient {

  @PostMapping("/instances/bulk")
  BulkResponse saveInstanceBulk(BulkRequest saveRequest);
}
