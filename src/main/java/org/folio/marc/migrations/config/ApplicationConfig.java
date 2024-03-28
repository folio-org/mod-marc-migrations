package org.folio.marc.migrations.config;

import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

@Configuration
public class ApplicationConfig {

  /**
   * Provides bean to map database records to OperationChunk entities using JdbcTemplate
   * */
  @Bean
  public BeanPropertyRowMapper<OperationChunk> operationChunkMapper() {
    return new BeanPropertyRowMapper<>(OperationChunk.class);
  }
}
