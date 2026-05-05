package de.fi.tag2_03conditional_flow_verzweigung.springbatchdemo;

import de.fi.tag2_03conditional_flow_verzweigung.Tag203ConditionalFlowVerzweigungApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

// spring.batch.job.enabled=true: Spring Boot soll den Job beim Start wirklich starten —
// im Testprofil ist das manchmal deaktiviert, damit Tests den Job selbst steuern können.
// OK_ODER_FEHLER=ok: erzwingt den Erfolgs-Pfad, weil der Zufalls-Modus den Test
// bei 50%-Wahrscheinlichkeit zufällig fehlschlagen lassen würde
@SpringBootTest(
        classes = Tag203ConditionalFlowVerzweigungApplication.class,
        properties = {
                "spring.batch.job.name=jobAbc",
                "OK_ODER_FEHLER=ok",
                "spring.batch.job.enabled=true"
        }
)
class ConditionalFlowJobIntegrationTest {

    @Autowired
    private JobExplorer jobExplorer;

    @Test
    void testJobLiefErfolgreich() {
        // Awaitility: reaktives Warten statt Thread.sleep — der Test blockiert nur so lange,
        // bis die Bedingung erfüllt ist, und schlägt sofort fehl wenn das Timeout abläuft
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                jobExplorer.getLastJobInstance("jobAbc") != null
        );

        JobInstance lastInstance = jobExplorer.getLastJobInstance("jobAbc");

        // Zweistufig warten: erst auf Existenz der Instanz, dann auf Ende der Ausführung —
        // ohne zweites await() könnte der Job noch laufen und der Status wäre STARTED, nicht COMPLETED
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            JobExecution je = jobExplorer.getJobExecution(lastInstance.getInstanceId());
            return je != null && !je.isRunning();
        });

        JobExecution lastExecution = jobExplorer.getJobExecution(lastInstance.getInstanceId());

        System.out.println("Gefundener Job-Status: " + lastExecution.getStatus());
        Assertions.assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
    }
}