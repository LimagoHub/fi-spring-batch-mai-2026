package de.fi.tag2_06partitionierer;



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
public class PartitionJobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PartitionJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job partitionJob;

    // Konstruktor-Injection (statt @RequiredArgsConstructor)
    public PartitionJobRunner(JobLauncher jobLauncher, Job partitionJob) {
        this.jobLauncher = jobLauncher;
        this.partitionJob = partitionJob;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("--- Starte Partitionierungs-Job ---");

        // "zeitpunkt" macht jeden Start eindeutig: Spring Batch würde einen erneuten Start
        // mit identischen Parametern ablehnen, weil der Job als bereits erfolgreich gilt.
        JobParameters params = new JobParametersBuilder()
                .addLong("zeitpunkt", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(partitionJob, params);

        log.info("--- Job beendet mit Status: {} ---", execution.getStatus());
    }
}