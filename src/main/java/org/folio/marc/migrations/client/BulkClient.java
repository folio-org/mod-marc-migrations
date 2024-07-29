package org.folio.marc.migrations.client;

import java.net.URI;
import java.util.Locale;
import lombok.Getter;
import org.folio.marc.migrations.domain.dto.BulkRequest;
import org.folio.marc.migrations.domain.dto.BulkResponse;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.util.UriComponentsBuilder;

@FeignClient("bulk-client")
public interface BulkClient {

  /**
   * Save bulk by given URI and {@link BulkRequest} object.
   *
   * @param uri   - uri address to request for as {@link URI} object
   * @param saveRequest - record file name as {@link BulkRequest} object
   * @return {@link  BulkResponse}
   */
  @PostMapping
  BulkResponse saveBulk(URI uri, BulkRequest saveRequest);

  @Getter
  enum EntityBulkType {
    AUTHORITY("http://authority-storage/authorities/bulk"),
    INSTANCE("http://instance-storage/instances/bulk");

    /**
     * Request URI for feign client.
     */
    private final URI uri;

    /**
     * Required args constructor.
     *
     * @param uriString - string value to create URI from
     */
    EntityBulkType(String uriString) {
      this.uri = UriComponentsBuilder.fromUriString(uriString).build().toUri();
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static URI mapUri(EntityType entityType) {
      return EntityBulkType.valueOf(entityType.name()).getUri();
    }
  }
}
