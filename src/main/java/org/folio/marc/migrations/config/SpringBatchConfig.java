package org.folio.marc.migrations.config;

import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.batch.ChunkEntityReader;
import org.folio.marc.migrations.services.batch.FileUploadStepListener;
import org.folio.marc.migrations.services.batch.RecordsWriter;
import org.folio.marc.migrations.services.domain.MappingComposite;
import org.folio.marc.migrations.services.domain.MappingResult;
import org.folio.marc.migrations.services.jdbc.ChunkJdbcService;
import org.folio.spring.scope.FolioExecutionScopeExecutionContextManager;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.batch.item.support.SynchronizedItemReader;
import org.springframework.batch.repeat.RepeatOperations;
import org.springframework.batch.repeat.support.TaskExecutorRepeatTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Log4j2
@Configuration
public class SpringBatchConfig {

  /**
   * Pooled, configurable TaskExecutor to provide concurrency for spring batch job.
   * Decorate calls with FolioExecutionContext.
   * */
  @Bean("chunksProcessingExecutor")
  public TaskExecutor chunksProcessingExecutor(MigrationProperties migrationProperties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadGroupName("chunksProcessing");
    executor.setCorePoolSize(migrationProperties.getChunkProcessingMaxParallelism());
    executor.setMaxPoolSize(migrationProperties.getChunkProcessingMaxParallelism());
    executor.setTaskDecorator(FolioExecutionScopeExecutionContextManager::getRunnableWithCurrentFolioContext);
    executor.afterPropertiesSet();
    return executor;
  }

  /**
   * In order to allow more than default 4 concurrent tasks running - we need to increase throttle limit.
   * As throttle limit setting has been deprecated - custom repeat operation with pooled TaskExecutor need to be
   * provided.
   * */
  @Bean
  public RepeatOperations customThrottler(@Qualifier("chunksProcessingExecutor") TaskExecutor executor) {
    var operations = new TaskExecutorRepeatTemplate();
    operations.setTaskExecutor(executor);
    return operations;
  }

  @Bean
  public Step remapAuthRecordsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   ItemReader<OperationChunk> reader,
                                   ItemProcessor<OperationChunk, MappingComposite<MappingResult>> processor,
                                   RecordsWriter writer, FileUploadStepListener listener,
                                   @Qualifier("chunksProcessingExecutor") TaskExecutor executor,
                                   RepeatOperations customThrottler) {
    return new StepBuilder("remapAuthRecords", jobRepository)
      .<OperationChunk, MappingComposite<MappingResult>>chunk(1, transactionManager)
      .reader(reader)
      .processor(processor)
      .writer(writer)
      .listener(listener)
      .taskExecutor(executor)
      .stepOperations(customThrottler)
      .build();
  }

  @Bean("remappingJob")
  public Job remappingJob(JobRepository jobRepository, Step remapAuthRecordsStep) {
    return new JobBuilder("remapping", jobRepository)
      .incrementer(new RunIdIncrementer())
      .start(remapAuthRecordsStep)
      .build();
  }

  @Bean
  @StepScope
  public ItemProcessor<OperationChunk, MappingComposite<MappingResult>> compositeItemProcessor(
    ItemProcessor<OperationChunk, MappingComposite<MarcRecord>> preparationProcessor,
    ItemProcessor<MappingComposite<MarcRecord>, MappingComposite<MappingResult>> chunkMapper,
    ItemProcessor<MappingComposite<MappingResult>, MappingComposite<MappingResult>> chunkDbUpdater) {

    var compositeItemProcessor = new CompositeItemProcessor<OperationChunk, MappingComposite<MappingResult>>();
    compositeItemProcessor.setDelegates(Arrays.asList(preparationProcessor, chunkMapper, chunkDbUpdater));
    return compositeItemProcessor;
  }

  /**
   * Synchronized reader to provide safety for multithreaded reads.
   * */
  @Bean
  @StepScope
  public ItemReader<OperationChunk> syncReader(@Value("#{jobParameters['operationId']}") String operationId,
                                               MigrationProperties properties, ChunkJdbcService jdbcService) {
    return new SynchronizedItemReader<>(new ChunkEntityReader(operationId, properties, jdbcService));
  }
}
