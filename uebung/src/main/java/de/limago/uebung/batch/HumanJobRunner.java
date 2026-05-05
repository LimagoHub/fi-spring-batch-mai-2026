package de.limago.uebung.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class HumanJobRunner implements CommandLineRunner {

    private final Job humanJob;
    private final JobLauncher jobLauncher;

    public HumanJobRunner(Job humanJob, JobLauncher jobLauncher) {
        this.humanJob = humanJob;
        this.jobLauncher = jobLauncher;
    }

    @Override
    public void run(String... args) throws Exception {
        // run.id stellt sicher, dass der Job bei jedem Start als neuer Job-Instance gilt
        jobLauncher.run(humanJob, new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters());
    }
}
