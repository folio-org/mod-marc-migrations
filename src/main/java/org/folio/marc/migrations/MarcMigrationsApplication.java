package org.folio.marc.migrations;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@EnableCaching
@EnableFeignClients(basePackages = "org.folio.marc.migrations.client")
@EnableBatchProcessing(isolationLevelForCreate = "ISOLATION_READ_COMMITTED")
@SpringBootApplication
public class MarcMigrationsApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarcMigrationsApplication.class, args);
  }
}
