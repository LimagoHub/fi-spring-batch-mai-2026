package de.limago.tag1_01simplechunk.runner;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

// CommandLineRunner sorgt dafür, dass run() nach dem vollständigen Start des
// Spring-Contexts aufgerufen wird — so können Abhängigkeiten sicher verwendet werden.
// In application.properties ist spring.batch.job.enabled=false gesetzt, damit Spring
// Boot den Job nicht automatisch beim Start ausführt — der AppRunner übernimmt das manuell.
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
        // Eine zufällige UUID als Job-Parameter garantiert, dass dieser Job bei
        // jedem Start als neue Instanz gilt. Ohne eindeutigen Parameter würde
        // Spring Batch einen bereits COMPLETED-Job nicht erneut starten.
        JobExecution jobExecution = jobLauncher.run(job,
                new JobParametersBuilder()
                        .addString("UUID", UUID.randomUUID().toString())
                        .toJobParameters());
        //JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder().addString("DEMO", "Hello").toJobParameters());
        System.out.println(jobExecution.getStatus());
    }
}
