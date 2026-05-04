package de.fi.second;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// CommandLineRunner sorgt dafür, dass run() nach dem vollständigen Start des
// Spring-Contexts aufgerufen wird — alle Beans sind zu diesem Zeitpunkt verfügbar.
@Component

public class AppRunner implements CommandLineRunner {

    public AppRunner(final JobLauncher jobLauncher, final Job job) {
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    private final JobLauncher jobLauncher;
    private final Job job;

    @Override
    public void run(final String... args) throws Exception {
        final int anzahlSteps = 4; // Anzahl der Tasklet-Wiederholungen in meinArbeitsStep

        System.out.println( "\nJoblauf mit Job-Parameter anzahlSteps=" + anzahlSteps + ":" );
        JobExecution je = jobLauncher.run( job,
                new JobParametersBuilder()
                        // Job-Parameter werden als String übergeben, weil JobParametersBuilder
                        // keinen int-Typen kennt — die Konvertierung übernimmt meinArbeitsStep.
                        .addString(
                            TaskletJobConfiguration.ANZAHLSTEPS_KEY, "" + anzahlSteps )
                        .toJobParameters() );

        // Auswertung der Step-Ergebnisse: CommitCount zeigt, wie oft das Tasklet
        // ausgeführt wurde (= Anzahl CONTINUABLE-Runden + 1 für FINISHED).
        for( StepExecution se : je.getStepExecutions() ) {
            System.out.println("StepExecution " + se.getId() + ": StepName = " + se.getStepName() +
                    ", CommitCount = " + se.getCommitCount());
        }
    }
}
