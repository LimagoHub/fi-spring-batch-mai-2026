package de.fi.tag2_07partitionierer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class JobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job partitionJob;

    public JobRunner(JobLauncher jobLauncher, Job partitionJob) {
        this.jobLauncher = jobLauncher;
        this.partitionJob = partitionJob;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Starte Partitionierungs-Job ===");

        // Zeitstempel als Parameter: verhindert, dass Spring Batch den Job
        // als "bereits ausgeführt" ablehnt, wenn er ein zweites Mal gestartet wird.
        JobParameters params = new JobParametersBuilder()
                .addLong("zeitpunkt", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(partitionJob, params);

        log.info("=== Job beendet mit Status: {} ===", execution.getStatus());
    }
}
