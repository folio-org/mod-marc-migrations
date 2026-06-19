package org.folio.marc.migrations.config;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.folio.marc.migrations.services.batch.MappingRangePartitioner;
import org.folio.marc.migrations.services.batch.RetryListPartitioner;
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
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Log4j2
@Configuration
public class SpringBatchConfig {

  /**
   * Pooled, configurable TaskExecutor for partition-handler concurrency.
   * Decorates each task with the current FolioExecutionContext so tenant-scoped beans
   * are available inside partition worker threads.
   */
  @Bean("chunksProcessingExecutor")
  public AsyncTaskExecutor chunksProcessingExecutor(MigrationProperties migrationProperties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadGroupName("chunksProcessing");
    executor.setCorePoolSize(migrationProperties.getChunkProcessingMaxParallelism());
    executor.setMaxPoolSize(migrationProperties.getChunkProcessingMaxParallelism());
    executor.setTaskDecorator(FolioExecutionScopeExecutionContextManager::getRunnableWithCurrentFolioContext);
    executor.afterPropertiesSet();
    executor.setVirtualThreads(true);
    return executor;
  }

  // ---------------------------------------------------------------------------
  // MAPPING — normal job
  // ---------------------------------------------------------------------------

  /** Worker: single-threaded chunk(1) step, one per partition. No taskExecutor, no listener. */
  @Bean("remapRecordsWorkerStep")
  public Step remapRecordsWorkerStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     @Qualifier("partitionMappingReader") ItemReader<OperationChunk> reader,
                                     @Qualifier("remappingStepProcessor")
                                     ItemProcessor<OperationChunk, MappingComposite<MappingResult>> processor,
                                     MappingRecordsWriter writer) {
    return new StepBuilder("remapRecordsWorker", jobRepository)
      .<OperationChunk, MappingComposite<MappingResult>>chunk(1)
      .transactionManager(transactionManager)
      .reader(reader)
      .processor(processor)
      .writer(writer)
      .build();
  }

  /** Manager: splits by id range, runs worker partitions in parallel, finalizes operation after all partitions. */
  @Bean("remapRecordsStep")
  public Step remapRecordsStep(JobRepository jobRepository,
                               @Qualifier("remapRecordsWorkerStep") Step workerStep,
                               @Qualifier("mappingRangePartitioner") Partitioner partitioner,
                               MappingRecordsFileUploadStepListener listener,
                               MigrationProperties migrationProperties,
                               @Qualifier("chunksProcessingExecutor") AsyncTaskExecutor executor) {
    return new StepBuilder("remapRecords", jobRepository)
      .partitioner("remapRecordsWorker", partitioner)
      .step(workerStep)
      .gridSize(migrationProperties.getChunkProcessingMaxParallelism())
      .taskExecutor(executor)
      .listener(listener)
      .build();
  }

  @Bean("remappingJob")
  public Job remappingJob(JobRepository jobRepository,
                          @Qualifier("remapRecordsStep") Step remapRecordsStep) {
    return new JobBuilder("remapping", jobRepository)
      .start(remapRecordsStep)
      .build();
  }

  // ---------------------------------------------------------------------------
  // SAVING — normal job
  // ---------------------------------------------------------------------------

  @Bean("remapSaveRecordsWorkerStep")
  public Step remapSaveRecordsWorkerStep(JobRepository jobRepository,
                                         PlatformTransactionManager transactionManager,
                                         @Qualifier("partitionMappingReader") ItemReader<OperationChunk> reader,
                                         SavingRecordsChunkProcessor processor,
                                         SavingRecordsWriter writer) {
    return new StepBuilder("remapSaveRecordsWorker", jobRepository)
      .<OperationChunk, DataSavingResult>chunk(1)
      .transactionManager(transactionManager)
      .reader(reader)
      .processor(processor)
      .writer(writer)
      .build();
  }

  @Bean("remapSaveRecordsStep")
  public Step remapSaveRecordsStep(JobRepository jobRepository,
                                   @Qualifier("remapSaveRecordsWorkerStep") Step workerStep,
                                   @Qualifier("mappingRangePartitioner") Partitioner partitioner,
                                   SavingRecordsStepListener listener,
                                   MigrationProperties migrationProperties,
                                   @Qualifier("chunksProcessingExecutor") AsyncTaskExecutor executor) {
    return new StepBuilder("remapSaveRecords", jobRepository)
      .partitioner("remapSaveRecordsWorker", partitioner)
      .step(workerStep)
      .gridSize(migrationProperties.getChunkProcessingMaxParallelism())
      .taskExecutor(executor)
      .listener(listener)
      .build();
  }

  @Bean("remappingSaveJob")
  public Job remappingSaveJob(JobRepository jobRepository,
                              @Qualifier("remapSaveRecordsStep") Step remapSaveRecordsStep) {
    return new JobBuilder("remappingSave", jobRepository)
      .start(remapSaveRecordsStep)
      .build();
  }

  // ---------------------------------------------------------------------------
  // MAPPING — retry job
  // ---------------------------------------------------------------------------

  @Bean("remapRetryRecordsWorkerStep")
  public Step remapRetryRecordsWorkerStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          @Qualifier("partitionRetryReader") ItemReader<OperationChunk> reader,
                                          @Qualifier("remappingStepProcessor")
                                          ItemProcessor<OperationChunk, MappingComposite<MappingResult>> processor,
                                          MappingRecordsWriter writer) {
    return new StepBuilder("remapRetryRecordsWorker", jobRepository)
      .<OperationChunk, MappingComposite<MappingResult>>chunk(1)
      .transactionManager(transactionManager)
      .reader(reader)
      .processor(processor)
      .writer(writer)
      .build();
  }

  @Bean("remapRetryRecordsStep")
  public Step remapRetryRecordsStep(JobRepository jobRepository,
                                    @Qualifier("remapRetryRecordsWorkerStep") Step workerStep,
                                    @Qualifier("retryListPartitioner") Partitioner partitioner,
                                    MappingRecordsFileUploadStepListener listener,
                                    MigrationProperties migrationProperties,
                                    @Qualifier("chunksProcessingExecutor") AsyncTaskExecutor executor) {
    return new StepBuilder("remapRetryRecords", jobRepository)
      .partitioner("remapRetryRecordsWorker", partitioner)
      .step(workerStep)
      .gridSize(migrationProperties.getChunkProcessingMaxParallelism())
      .taskExecutor(executor)
      .listener(listener)
      .build();
  }

  @Bean("remappingRetryJob")
  public Job remappingRetryJob(JobRepository jobRepository,
                               @Qualifier("remapRetryRecordsStep") Step remapRetryRecordsStep) {
    return new JobBuilder("remappingRetry", jobRepository)
      .start(remapRetryRecordsStep)
      .build();
  }

  // ---------------------------------------------------------------------------
  // SAVING — retry job
  // ---------------------------------------------------------------------------

  @Bean("remapRetrySaveRecordsWorkerStep")
  public Step remapRetrySaveRecordsWorkerStep(JobRepository jobRepository,
                                              PlatformTransactionManager transactionManager,
                                              @Qualifier("partitionRetryReader") ItemReader<OperationChunk> reader,
                                              SavingRetryRecordsChunkProcessor processor,
                                              SavingRecordsWriter writer) {
    return new StepBuilder("remapRetrySaveRecordsWorker", jobRepository)
      .<OperationChunk, DataSavingResult>chunk(1)
      .transactionManager(transactionManager)
      .reader(reader)
      .processor(processor)
      .writer(writer)
      .build();
  }

  @Bean("remapRetrySaveRecordsStep")
  public Step remapRetrySaveRecordsStep(JobRepository jobRepository,
                                        @Qualifier("remapRetrySaveRecordsWorkerStep") Step workerStep,
                                        @Qualifier("retryListPartitioner") Partitioner partitioner,
                                        SavingRecordsStepListener listener,
                                        MigrationProperties migrationProperties,
                                        @Qualifier("chunksProcessingExecutor") AsyncTaskExecutor executor) {
    return new StepBuilder("remapRetrySaveRecords", jobRepository)
      .partitioner("remapRetrySaveRecordsWorker", partitioner)
      .step(workerStep)
      .gridSize(migrationProperties.getChunkProcessingMaxParallelism())
      .taskExecutor(executor)
      .listener(listener)
      .build();
  }

  @Bean("remappingRetrySaveJob")
  public Job remappingRetrySaveJob(JobRepository jobRepository,
                                   @Qualifier("remapRetrySaveRecordsStep") Step remapRetrySaveRecordsStep) {
    return new JobBuilder("remappingRetrySave", jobRepository)
      .start(remapRetrySaveRecordsStep)
      .build();
  }

  // ---------------------------------------------------------------------------
  // Shared processor (mapping only)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Partition-scoped readers
  // ---------------------------------------------------------------------------

  /**
   * Reads only the partition's id window {@code (fromId, toId]}, keyset-paged. Used by both mapping and save steps.
   * {@code fromId} is absent (null) for the first partition.
   */
  @Bean("partitionMappingReader")
  @StepScope
  public ItemReader<OperationChunk> partitionMappingReader(
    @Value("#{stepExecutionContext['" + MappingRangePartitioner.FROM_ID_KEY + "']}") String fromIdStr,
    @Value("#{stepExecutionContext['" + MappingRangePartitioner.TO_ID_KEY + "']}") String toIdStr,
    @Value("#{jobParameters['operationId']}") String operationId,
    MigrationProperties properties, ChunkJdbcService jdbcService) {
    var fromId = fromIdStr != null ? UUID.fromString(fromIdStr) : null;
    var toId = UUID.fromString(toIdStr);
    return new MappingChunkEntityReader(operationId, fromId, toId, properties, jdbcService);
  }

  /** Reads the partition's sublist of retry chunk ids. Used by both mapping-retry and save-retry steps. */
  @Bean("partitionRetryReader")
  @StepScope
  public ItemReader<OperationChunk> partitionRetryReader(
    @Value("#{stepExecutionContext['" + RetryListPartitioner.CHUNK_IDS_KEY + "']}") String chunkIdsStr,
    ChunkJdbcService jdbcService) {
    var ids = Arrays.stream(chunkIdsStr.split(","))
      .map(UUID::fromString)
      .toList();
    return new MappingChunksRetryEntityReader(jdbcService, ids);
  }

  // ---------------------------------------------------------------------------
  // Partition-scoped partitioners
  // ---------------------------------------------------------------------------

  /** Splits all chunks for an operation into equal id ranges. Used by normal mapping and save jobs. */
  @Bean("mappingRangePartitioner")
  @StepScope
  public Partitioner mappingRangePartitioner(
    @Value("#{jobParameters['operationId']}") String operationId,
    ChunkJdbcService jdbcService) {
    return new MappingRangePartitioner(operationId, jdbcService);
  }

  /** Splits a retry chunk-id list round-robin across partitions. Used by retry mapping and save jobs. */
  @Bean("retryListPartitioner")
  @StepScope
  public Partitioner retryListPartitioner(
    @Value("#{jobParameters['chunkIds']}") List<UUID> chunkIds) {
    return new RetryListPartitioner(chunkIds);
  }
}
