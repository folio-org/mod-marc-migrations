package org.folio.marc.migrations.config;

import org.folio.marc.migrations.domain.entities.ChunkStep;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

@Configuration
public class ApplicationConfig {

  /**
   * Provides bean to map database records to OperationChunk entities using JdbcTemplate.
   * */
  @Bean
  public BeanPropertyRowMapper<OperationChunk> operationChunkMapper() {
    return new BeanPropertyRowMapper<>(OperationChunk.class);
  }

  /**
   * Provides bean to map database records to {@link Operation} entities using JdbcTemplate.
   * */
  @Bean
  public BeanPropertyRowMapper<Operation> operationMapper() {
    return new BeanPropertyRowMapper<>(Operation.class);
  }

  /**
   * Provides bean to map database records to OperationChunk entities using JdbcTemplate.
   * */
  @Bean
  public BeanPropertyRowMapper<ChunkStep> operationChunkStepMapper() {
    return new BeanPropertyRowMapper<>(ChunkStep.class);
  }
}
