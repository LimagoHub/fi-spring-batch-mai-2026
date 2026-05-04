package de.fi.first.runner;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AppRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;


    private  final Job job;

    public AppRunner(final JobLauncher jobLauncher, final Job job) {
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    @Override
    public void run(final String... args) throws Exception {
        // UUID als JobParameter macht jeden Start eindeutig — Spring Batch würde sonst
        // einen identisch parametrisierten Job als bereits abgeschlossen zurückweisen
        JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder()
                .addString("UUID", UUID.randomUUID().toString())
                .toJobParameters());
        System.out.println(jobExecution.getStatus());
    }
}
