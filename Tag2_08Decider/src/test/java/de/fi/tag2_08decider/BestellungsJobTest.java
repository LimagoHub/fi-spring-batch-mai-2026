package de.fi.tag2_08decider;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrations-Tests für den bestellungsJob.
 *
 * @SpringBatchTest registriert JobLauncherTestUtils im Application-Context.
 * @SpringBootTest lädt den vollständigen Spring-Context (inkl. H2, JobRepository).
 * @MockitoBean JobRunner verhindert, dass der CommandLineRunner beim Teststart
 *   den Job ein zweites Mal ausführt — die Tests steuern die Ausführung selbst.
 */
@SpringBatchTest
@SpringBootTest
class BestellungsJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    // CommandLineRunner mocken, damit er beim Hochfahren des Contexts nicht läuft
    @MockitoBean
    private JobRunner jobRunner;

    @Test
    void premiumPfadWirdKorrektDurchlaufen() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("kundenTyp", "PREMIUM")
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Genau der Premium-Step muss gelaufen sein
        assertThat(schrittAusgefuehrt(execution, "premiumRabattStep")).isTrue();
        assertThat(schrittAusgefuehrt(execution, "standardBearbeitungStep")).isFalse();
        assertThat(schrittAusgefuehrt(execution, "fehlerBehandlungStep")).isFalse();
        // Abschluss-Step läuft immer
        assertThat(schrittAusgefuehrt(execution, "abschlussQuittierungStep")).isTrue();

        // JobExecutionContext muss Premium-spezifische Werte enthalten
        ExecutionContext ctx = execution.getExecutionContext();
        assertThat(ctx.getString("verarbeitungsTyp")).isEqualTo("PREMIUM");
        assertThat(ctx.containsKey("rabatt")).isTrue();
        assertThat(ctx.containsKey("endBetrag")).isTrue();
        // Standard-Schlüssel darf nicht gesetzt sein
        assertThat(ctx.containsKey("versandkosten")).isFalse();
    }

    @Test
    void standardPfadWirdKorrektDurchlaufen() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("kundenTyp", "STANDARD")
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(schrittAusgefuehrt(execution, "standardBearbeitungStep")).isTrue();
        assertThat(schrittAusgefuehrt(execution, "premiumRabattStep")).isFalse();
        assertThat(schrittAusgefuehrt(execution, "fehlerBehandlungStep")).isFalse();
        assertThat(schrittAusgefuehrt(execution, "abschlussQuittierungStep")).isTrue();

        ExecutionContext ctx = execution.getExecutionContext();
        assertThat(ctx.getString("verarbeitungsTyp")).isEqualTo("STANDARD");
        assertThat(ctx.containsKey("versandkosten")).isTrue();
        assertThat(ctx.containsKey("endBetrag")).isTrue();
        assertThat(ctx.containsKey("rabatt")).isFalse();
    }

    @Test
    void unbekannterKundenTypRoutetInFehlerPfad() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("kundenTyp", "UNBEKANNT")
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(schrittAusgefuehrt(execution, "fehlerBehandlungStep")).isTrue();
        assertThat(schrittAusgefuehrt(execution, "premiumRabattStep")).isFalse();
        assertThat(schrittAusgefuehrt(execution, "standardBearbeitungStep")).isFalse();
        assertThat(schrittAusgefuehrt(execution, "abschlussQuittierungStep")).isTrue();

        ExecutionContext ctx = execution.getExecutionContext();
        assertThat(ctx.getString("verarbeitungsTyp")).isEqualTo("FEHLER");
        assertThat(ctx.containsKey("fehlerGrund")).isTrue();
    }

    @Test
    void werteWerdenKorrektZwischenStepsUebertragen() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("kundenTyp", "PREMIUM")
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        ExecutionContext ctx = execution.getExecutionContext();

        // Werte aus Schritt 1 müssen im Context stehen
        assertThat(ctx.containsKey("bestellungId")).isTrue();
        assertThat(ctx.containsKey("artikel")).isTrue();
        assertThat(ctx.getDouble("betrag")).isEqualTo(89.99);

        // Endbetrag = 89.99 − 20% = 71.99
        assertThat(ctx.getDouble("endBetrag")).isEqualTo(71.99);
        assertThat(ctx.getDouble("rabatt")).isEqualTo(18.0);
    }

    private boolean schrittAusgefuehrt(JobExecution execution, String stepName) {
        return execution.getStepExecutions().stream()
                .anyMatch(s -> s.getStepName().equals(stepName));
    }
}
