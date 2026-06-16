package org.folio.marc.migrations;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.Isolation;

@EnableRetry
@EnableCaching
@EnableBatchProcessing
@EnableJdbcJobRepository(isolationLevelForCreate = Isolation.SERIALIZABLE)
@SpringBootApplication
public class MarcMigrationsApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarcMigrationsApplication.class, args);
  }
}
