package de.limago.uebung.batch.config;

import de.limago.uebung.batch.aggregator.GenderCountAggregator;
import de.limago.uebung.batch.partitioner.HumanPartitioner;
import de.limago.uebung.batch.processor.GenderCountProcessor;
import de.limago.uebung.batch.processor.HumanToCustomerProcessor;
import de.limago.uebung.batch.tasklet.GenderSummaryTasklet;
import de.limago.uebung.entity.Customer;
import de.limago.uebung.entity.Human;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class HumanBatchConfig {

    private static final int GRID_SIZE  = 5;
    private static final int CHUNK_SIZE = 50;

    // -------------------------------------------------------------------------
    // Job
    // -------------------------------------------------------------------------

    @Bean
    public Job humanJob(JobRepository jobRepository, Step masterStep, Step summaryStep) {
        return new JobBuilder("humanJob", jobRepository)
                .start(masterStep)
                .next(summaryStep)
                .build();
    }

    // -------------------------------------------------------------------------
    // Steps
    // -------------------------------------------------------------------------

    // Zwei Verantwortlichkeiten auf dem masterStep:
    //
    // 1. GenderCountAggregator (aggregator):
    //    Wird von Spring Batch intern nach Abschluss ALLER Worker aufgerufen.
    //    Bekommt alle Partition-StepExecutions als Collection übergeben und
    //    schreibt die aufsummierten Gender-Werte in den master-StepExecutionContext.
    //
    // 2. ExecutionContextPromotionListener (listener):
    //    Befördert die konfigurierten Keys nach afterStep() automatisch aus dem
    //    master-StepExecutionContext in den JobExecutionContext, damit der
    //    nachfolgende summaryStep darauf zugreifen kann.
    @Bean
    public Step masterStep(JobRepository jobRepository,
                           Step workerStep,
                           HumanPartitioner partitioner) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(GRID_SIZE)
                .taskExecutor(new SimpleAsyncTaskExecutor("partition-worker-"))
                .aggregator(new GenderCountAggregator())
                .listener(genderPromotionListener())
                .build();
    }

    // Keys, die der PromotionListener vom master-StepContext in den JobContext kopiert.
    @Bean
    public ExecutionContextPromotionListener genderPromotionListener() {
        var listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[]{"total.male", "total.female", "total.nonBinary"});
        return listener;
    }

    // Der Worker-Step enthaelt die eigentliche Verarbeitungslogik.
    // Er wird vom Master-Step fuer jede Partition separat gestartet.
    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           JdbcCursorItemReader<Human> humanReader,
                           CompositeItemProcessor<Human, Customer> compositeProcessor,
                           JpaItemWriter<Customer> customerWriter) {
        return new StepBuilder("workerStep", jobRepository)
                .<Human, Customer>chunk(CHUNK_SIZE, transactionManager)
                .reader(humanReader)
                .processor(compositeProcessor)
                .writer(customerWriter)
                .build();
    }

    // Liest die aggregierten Zähler aus dem JobExecutionContext und gibt sie aus.
    @Bean
    public Step summaryStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager,
                            GenderSummaryTasklet summaryTasklet) {
        return new StepBuilder("summaryStep", jobRepository)
                .tasklet(summaryTasklet, transactionManager)
                .build();
    }

    // -------------------------------------------------------------------------
    // Reader
    // -------------------------------------------------------------------------

    // @StepScope ist zwingend, weil offset/limit erst zur Laufzeit des jeweiligen
    // Partition-Steps aus dem StepExecutionContext bekannt sind.
    @Bean
    @StepScope
    public JdbcCursorItemReader<Human> humanReader(
            DataSource dataSource,
            @Value("#{stepExecutionContext['offset']}") Integer offset,
            @Value("#{stepExecutionContext['limit']}") Integer limit) {

        return new JdbcCursorItemReaderBuilder<Human>()
                .name("humanReader")
                .dataSource(dataSource)
                .sql("SELECT first_name, last_name, email, country, gender " +
                     "FROM tbl_humans " +
                     "ORDER BY last_name, first_name " +
                     "LIMIT ? OFFSET ?")
                .preparedStatementSetter(ps -> {
                    ps.setInt(1, limit);
                    ps.setInt(2, offset);
                })
                .rowMapper((rs, rowNum) -> new Human(
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("gender"),
                        rs.getString("country")
                ))
                .build();
    }

    // -------------------------------------------------------------------------
    // Processor (Composite)
    // -------------------------------------------------------------------------

    @Bean
    @StepScope
    public CompositeItemProcessor<Human, Customer> compositeProcessor(
            GenderCountProcessor genderCountProcessor,
            HumanToCustomerProcessor humanToCustomerProcessor) {

        // Human -> GenderCountProcessor (pass-through) -> HumanToCustomerProcessor -> Customer
        CompositeItemProcessor<Human, Customer> composite = new CompositeItemProcessor<>();
        composite.setDelegates(List.of(genderCountProcessor, humanToCustomerProcessor));
        return composite;
    }

    // -------------------------------------------------------------------------
    // Writer
    // -------------------------------------------------------------------------

    @Bean
    public JpaItemWriter<Customer> customerWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<Customer>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}
