package org.folio.marc.migrations.client;

import java.net.URI;
import java.util.Locale;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.util.UriComponentsBuilder;

@HttpExchange
public interface BulkClient {

  /**
   * Save bulk by given URI and {@link BulkRequest} object.
   *
   * @param uri         - uri address to request for as {@link URI} object
   * @param saveRequest - record file name as {@link BulkRequest} object
   * @return {@link  BulkResponse}
   */
  @PostExchange
  BulkResponse saveBulk(URI uri, @RequestBody BulkRequest saveRequest);

  @Getter
  enum EntityBulkType {
    AUTHORITY("authority-storage/authorities/bulk"),
    INSTANCE("instance-storage/instances/bulk");

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

    public static URI mapUri(EntityType entityType) {
      return EntityBulkType.valueOf(entityType.name()).getUri();
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  @Data
  @RequiredArgsConstructor
  class BulkRequest {

    private final String recordsFileName;
    private Boolean publishEvents = true;
  }

  @Data
  class BulkResponse {

    private String errorRecordsFileName;

    private String errorsFileName;

    private Integer errorsNumber;
  }
}
