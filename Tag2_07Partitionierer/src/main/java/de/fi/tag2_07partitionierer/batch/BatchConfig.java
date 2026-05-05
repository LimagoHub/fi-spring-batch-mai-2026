package de.fi.tag2_07partitionierer.batch;

import de.fi.tag2_07partitionierer.batch.tasklet.CleanupTasklet;
import de.fi.tag2_07partitionierer.batch.tasklet.SplitTasklet;
import de.fi.tag2_07partitionierer.db.entity.StudentEntity;
import de.fi.tag2_07partitionierer.model.Student;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    // Anzahl Datensätze pro Transaktion innerhalb eines Worker-Threads
    static final int CHUNK_SIZE = 50;
    // Muss mit der Anzahl der chunk_X.csv-Dateien übereinstimmen, die SplitTasklet erzeugt
    static final int GRID_SIZE  = 4;

    @Bean
    public StudentPartitioner studentPartitioner() {
        return new StudentPartitioner();
    }

    // @StepScope: JpaItemWriter öffnet intern einen EntityManager pro Transaktion.
    // Mit @StepScope bekommt jeder Worker-Thread eine eigene Instanz — verhindert
    // gegenseitiges Blockieren bei parallelen Schreibzugriffen.
    @Bean
    @StepScope
    public JpaItemWriter<StudentEntity> jpaItemWriter(EntityManagerFactory entityManagerFactory) {
        JpaItemWriter<StudentEntity> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }

    // ── Step 1: splitStep ────────────────────────────────────────────────────────
    // Läuft genau einmal, sequentiell, bevor die parallele Verarbeitung startet.
    @Bean
    public Step splitStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("splitStep", jobRepository)
                .tasklet(new SplitTasklet(), txManager)
                .build();
    }

    // ── Step 2a: workerStep ──────────────────────────────────────────────────────
    // Die eigentliche Chunk-Verarbeitung: lesen → mappen → schreiben.
    // Dieser Step wird vom masterStep 4x parallel gestartet — je einmal pro Partition.
    // Er selbst weiß nichts von Parallelität; das steuert ausschließlich der masterStep.
    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager txManager,
                           FlatFileItemReader<Student> studentItemReader,
                           StudentItemProcessor studentItemProcessor,
                           JpaItemWriter<StudentEntity> jpaItemWriter) {
        return new StepBuilder("workerStep", jobRepository)
                .<Student, StudentEntity>chunk(CHUNK_SIZE, txManager)
                .reader(studentItemReader)
                .processor(studentItemProcessor)
                .writer(jpaItemWriter)
                .build();
    }

    // ── Step 2b: masterStep ──────────────────────────────────────────────────────
    // Orchestriert die parallele Ausführung: ruft den Partitioner auf, verteilt
    // die ExecutionContexts auf Worker-Threads und wartet bis alle fertig sind.
    @Bean
    public Step masterStep(JobRepository jobRepository,
                           PlatformTransactionManager txManager,
                           Step workerStep,
                           StudentPartitioner studentPartitioner) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner(workerStep.getName(), studentPartitioner)
                .step(workerStep)
                .gridSize(GRID_SIZE)
                // SimpleAsyncTaskExecutor startet für jede Partition einen neuen Thread.
                // Für Produktion: ThreadPoolTaskExecutor mit definiertem Pool-Limit verwenden.
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .build();
    }

    // ── Step 3: cleanupStep ──────────────────────────────────────────────────────
    // Läuft nur bei erfolgreichem masterStep. Fehlerfall → FileFailureListener.
    @Bean
    public Step cleanupStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("cleanupStep", jobRepository)
                .tasklet(new CleanupTasklet(), txManager)
                .build();
    }

    // ── Job ──────────────────────────────────────────────────────────────────────
    @Bean
    public Job partitionJob(JobRepository jobRepository,
                            Step splitStep,
                            Step masterStep,
                            Step cleanupStep) {
        return new JobBuilder("partitionJob", jobRepository)
                .listener(new FileFailureListener())
                .start(splitStep)
                .next(masterStep)
                .next(cleanupStep)
                .build();
    }
}
