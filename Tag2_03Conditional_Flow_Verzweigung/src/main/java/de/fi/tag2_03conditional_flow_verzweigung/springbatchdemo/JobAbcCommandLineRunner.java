package de.fi.tag2_03conditional_flow_verzweigung.springbatchdemo;


import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
// Aufruf mit Parameter: mvn spring-boot:run -Dspring-boot.run.arguments=--okOderFehler=ok
// CommandLineRunner wird von Spring Boot beim Start aufgerufen — ersetzt hier den
// automatischen Job-Start, damit die JobParameter manuell übergeben werden können
@Component
public class JobAbcCommandLineRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job meinConditionalFlowJobAbc;

    // Konstruktor-Injection: der Compiler erzwingt, dass beide Dependencies vorhanden sind —
    // @Autowired auf dem Feld wäre erst zur Laufzeit eine NullPointerException
    public JobAbcCommandLineRunner(
            JobLauncher jobLauncher,
            Job meinConditionalFlowJobAbc
    ) {
        this.jobLauncher = jobLauncher;
        this.meinConditionalFlowJobAbc = meinConditionalFlowJobAbc;
    }

    @Override
    public void run(String... args) throws Exception {

        String okOderFehler = null;

        // Manuelles Parsen: Spring @Value funktioniert nicht in run(String... args),
        // weil die args-Array die rohen Kommandozeilenargumente enthält, kein Spring-Environment
        for (String arg : args) {
            if (arg.startsWith("--okOderFehler=")) {
                okOderFehler = arg.substring("--okOderFehler=".length());
            }
        }

        // Zeitstempel als Pflicht-Parameter: Spring Batch verwirft einen Start, wenn
        // eine Job-Instanz mit identischen Parametern bereits COMPLETED ist
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("ts", System.currentTimeMillis());

        if (okOderFehler != null) {
            builder.addString(
                    ConditionalFlowJobConfiguration.OK_ODER_FEHLER,
                    okOderFehler
            );
        }

        JobParameters parameters = builder.toJobParameters();

        System.out.println("\n=== Starte JobAbc mit Parametern: " + parameters + " ===");

        JobExecution execution =
                jobLauncher.run(meinConditionalFlowJobAbc, parameters);

        System.out.println("=== Job beendet ===");
        System.out.println("Status:     " + execution.getStatus());
        System.out.println("ExitStatus: " + execution.getExitStatus());
    }
}