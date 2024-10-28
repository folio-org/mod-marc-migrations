package org.folio.marc.migrations.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("folio.migration")
public class MigrationProperties {
  /**
   * Provides chunk size for records distribution during operation processing.
   * */
  @Min(1)
  private int chunkSize = 50;
  /**
   * Provides number of record ids to fetch per query on chunks preparation phase.
   * */
  @Min(1)
  private int chunkFetchIdsCount = 500;
  /**
   * Represents the amount of chunks to be accumulated before persisting them to database.
   * */
  @Min(1)
  private int chunkPersistCount = 1_000;
  /**
   * Represents the "maximum" thread pool size for chunks processing.
   * */
  @Min(1)
  private int chunkProcessingMaxParallelism = 4;
  /**
   * Provides a local storage path for Authority and Marc bib files during migration.
   * */
  private String localFileStoragePath = "job";
}
