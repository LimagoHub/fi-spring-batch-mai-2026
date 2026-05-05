package de.fi.tag2_08decider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startet den Job beim Anwendungsstart.
 * Der Kundentyp wird per Kommandozeilen-Argument übergeben:
 *   --kundenTyp=PREMIUM   → Premium-Pfad
 *   --kundenTyp=STANDARD  → Standard-Pfad (Default)
 *   --kundenTyp=FEHLER    → Fehler-Pfad
 */
@Component
public class JobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job bestellungsJob;

    public JobRunner(JobLauncher jobLauncher, Job bestellungsJob) {
        this.jobLauncher = jobLauncher;
        this.bestellungsJob = bestellungsJob;
    }

    @Override
    public void run(String... args) throws Exception {
        String kundenTyp = "STANDARD";
        for (String arg : args) {
            if (arg.startsWith("--kundenTyp=")) {
                kundenTyp = arg.substring("--kundenTyp=".length()).toUpperCase();
            }
        }

        JobParameters params = new JobParametersBuilder()
                .addString("kundenTyp", kundenTyp)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        log.info("Starte bestellungsJob mit kundenTyp='{}'", kundenTyp);
        JobExecution execution = jobLauncher.run(bestellungsJob, params);
        log.info("Job beendet — BatchStatus: {}, ExitCode: {}",
                execution.getStatus(), execution.getExitStatus().getExitCode());
    }
}
