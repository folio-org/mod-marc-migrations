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
    * Provides the maximum number of chunk IDs for retrying.
   * */
  @Min(1)
  private int chunkRetryingMaxIdsCount = 1000;
  /**
   * Provides the S3 local sub path for Authority and Marc bib files during migration.
   */
  private String s3LocalSubPath = "mod-marc-migrations";
  /**
   * Provides the S3 sub path for files storage.
   */
  private String s3SubPath = "mod-marc-migrations";
  /**
   * Amount of days to keep migration related records in the database.
   * */
  @Min(1)
  private int jobRetentionDays = 7;
}
