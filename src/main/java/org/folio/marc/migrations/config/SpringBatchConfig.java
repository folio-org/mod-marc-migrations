package org.folio.marc.migrations.config;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.batch.mapping.MappingChunkEntityReader;
import org.folio.marc.migrations.services.batch.mapping.MappingChunksRetryEntityReader;
import org.folio.marc.migrations.services.batch.mapping.MappingRecordsFileUploadStepListener;
import org.folio.marc.migrations.services.batch.mapping.MappingRecordsWriter;
import org.folio.marc.migrations.services.batch.saving.SavingRecordsChunkProcessor;
import org.folio.marc.migrations.services.batch.saving.SavingRecordsStepListener;
import org.folio.marc.migrations.services.batch.saving.SavingRecordsWriter;
import org.folio.marc.migrations.services.batch.saving.SavingRetryRecordsChunkProcessor;
import org.folio.marc.migrations.services.domain.DataSavingResult;
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
    executor.setVirtualThreads(true);
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

  @Bean("remapAuthRecordsStep")
  public Step remapAuthRecordsStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   @Qualifier("syncReader")
                                   ItemReader<OperationChunk> reader,
                                   @Qualifier("remappingStepProcessor")
                                     ItemProcessor<OperationChunk, MappingComposite<MappingResult>> processor,
                                   MappingRecordsWriter writer,
                                   MappingRecordsFileUploadStepListener listener,
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
  public Job remappingJob(JobRepository jobRepository,
                          @Qualifier("remapAuthRecordsStep") Step remapAuthRecordsStep) {
    return new JobBuilder("remapping", jobRepository)
      .incrementer(new RunIdIncrementer())
      .start(remapAuthRecordsStep)
      .build();
  }

  @Bean(name = "remapSaveAuthRecordsStep")
  public Step remapSaveAuthRecordsStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       @Qualifier("syncReader")
                                       ItemReader<OperationChunk> reader,
                                       SavingRecordsChunkProcessor processor,
                                       SavingRecordsWriter writer,
                                       SavingRecordsStepListener listener,
                                       @Qualifier("chunksProcessingExecutor") TaskExecutor executor,
                                       RepeatOperations customThrottler) {
    return new StepBuilder("remapSaveAuthRecords", jobRepository)
        .<OperationChunk, DataSavingResult>chunk(1, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(listener)
        .taskExecutor(executor)
        .stepOperations(customThrottler)
        .build();
  }

  @Bean("remappingSaveJob")
  public Job remappingSaveJob(JobRepository jobRepository,
                              @Qualifier("remapSaveAuthRecordsStep") Step remapAuthRecordsStep) {
    return new JobBuilder("remappingSave", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(remapAuthRecordsStep)
        .build();
  }

  @Bean("remappingStepProcessor")
  @StepScope
  public ItemProcessor<OperationChunk, MappingComposite<MappingResult>> remappingCompositeItemProcessor(
    @Qualifier("mappingRecordsStepPreProcessor")
    ItemProcessor<OperationChunk, MappingComposite<MarcRecord>> preparationProcessor,
    @Qualifier("mappingRecordsStepProcessor")
    ItemProcessor<MappingComposite<MarcRecord>, MappingComposite<MappingResult>> chunkMapper) {

    var compositeItemProcessor = new CompositeItemProcessor<OperationChunk, MappingComposite<MappingResult>>();
    compositeItemProcessor.setDelegates(Arrays.asList(preparationProcessor, chunkMapper));
    return compositeItemProcessor;
  }

  /**
   * Synchronized reader to provide safety for multithreaded reads.
   * */
  @Bean("syncReader")
  @StepScope
  public ItemReader<OperationChunk> syncReader(@Value("#{jobParameters['operationId']}") String operationId,
                                               MigrationProperties properties, ChunkJdbcService jdbcService) {
    return new SynchronizedItemReader<>(new MappingChunkEntityReader(operationId, properties, jdbcService));
  }

  @Bean("retryingSyncReader")
  @StepScope
  public ItemReader<OperationChunk> retryingSyncReader(ChunkJdbcService jdbcService,
      @Value("#{jobParameters['chunkIds']}") List<UUID> chunkIds) {
    return new SynchronizedItemReader<>(new MappingChunksRetryEntityReader(jdbcService, chunkIds));
  }

  @Bean("remappingRetryJob")
  public Job remappingRetryJob(JobRepository jobRepository,
                               @Qualifier("remapAuthRetryRecordsStep") Step remapAuthRetryRecordsStep) {
    return new JobBuilder("remappingRetry", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(remapAuthRetryRecordsStep)
        .build();
  }

  @Bean("remapAuthRetryRecordsStep")
  public Step remapAuthRetryRecordsStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        @Qualifier("retryingSyncReader")
                                        ItemReader<OperationChunk> reader,
                                        @Qualifier("remappingStepProcessor")
                                        ItemProcessor<OperationChunk, MappingComposite<MappingResult>> processor,
                                        MappingRecordsWriter writer,
                                        MappingRecordsFileUploadStepListener listener,
                                        @Qualifier("chunksProcessingExecutor") TaskExecutor executor,
                                        RepeatOperations customThrottler) {
    return new StepBuilder("remapAuthRetryRecords", jobRepository)
        .<OperationChunk, MappingComposite<MappingResult>>chunk(1, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(listener)
        .taskExecutor(executor)
        .stepOperations(customThrottler)
        .build();
  }

  @Bean("remappingRetrySaveJob")
  public Job remappingRetrySaveJob(JobRepository jobRepository,
                              @Qualifier("remapRetrySaveRecordsStep") Step remapRetrySaveRecordsStep) {
    return new JobBuilder("remappingRetrySave", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(remapRetrySaveRecordsStep)
        .build();
  }

  @Bean(name = "remapRetrySaveRecordsStep")
  public Step remapRetrySaveRecordsStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       @Qualifier("retryingSyncReader")
                                       ItemReader<OperationChunk> reader,
                                       SavingRetryRecordsChunkProcessor processor,
                                       SavingRecordsWriter writer,
                                       SavingRecordsStepListener listener,
                                       @Qualifier("chunksProcessingExecutor") TaskExecutor executor,
                                       RepeatOperations customThrottler) {
    return new StepBuilder("remapRetrySaveRecords", jobRepository)
        .<OperationChunk, DataSavingResult>chunk(1, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(listener)
        .taskExecutor(executor)
        .stepOperations(customThrottler)
        .build();
  }
}
