package de.fi.tag2_08decider.batch;

import de.fi.tag2_08decider.batch.tasklet.AbschlussQuittierungTasklet;
import de.fi.tag2_08decider.batch.tasklet.BestellungErfassenTasklet;
import de.fi.tag2_08decider.batch.tasklet.FehlerProtokollTasklet;
import de.fi.tag2_08decider.batch.tasklet.PremiumRabattTasklet;
import de.fi.tag2_08decider.batch.tasklet.StandardBearbeitungTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Zentrale Spring-Batch-Konfiguration.
 *
 * Job-Flow (Übersicht):
 *
 *   bestellungErfassenStep
 *          │
 *     KundenTypDecider
 *       ├─ "PREMIUM"  → premiumRabattStep  ──────┐
 *       ├─ "STANDARD" → standardBearbeitungStep ─┼──→ abschlussQuittierungStep
 *       └─ "*"        → fehlerBehandlungStep ────┘
 *
 * Schritt 1 und Schritt 3 laufen immer.
 * Schritt 2 wird durch den Decider gesteuert — nur einer der drei Pfade läuft.
 *
 * Hinweis: @EnableBatchProcessing fehlt bewusst.
 * Spring Boot 3 / Spring Batch 5 konfiguriert JobRepository, JobLauncher
 * und TransactionManager automatisch. @EnableBatchProcessing würde diese
 * Autokonfiguration deaktivieren und manuelles Setup erfordern.
 */
@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BestellungErfassenTasklet bestellungErfassenTasklet;
    private final PremiumRabattTasklet premiumRabattTasklet;
    private final StandardBearbeitungTasklet standardBearbeitungTasklet;
    private final FehlerProtokollTasklet fehlerProtokollTasklet;
    private final AbschlussQuittierungTasklet abschlussQuittierungTasklet;
    private final KundenTypDecider kundenTypDecider;

    public BatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            BestellungErfassenTasklet bestellungErfassenTasklet,
            PremiumRabattTasklet premiumRabattTasklet,
            StandardBearbeitungTasklet standardBearbeitungTasklet,
            FehlerProtokollTasklet fehlerProtokollTasklet,
            AbschlussQuittierungTasklet abschlussQuittierungTasklet,
            KundenTypDecider kundenTypDecider) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.bestellungErfassenTasklet = bestellungErfassenTasklet;
        this.premiumRabattTasklet = premiumRabattTasklet;
        this.standardBearbeitungTasklet = standardBearbeitungTasklet;
        this.fehlerProtokollTasklet = fehlerProtokollTasklet;
        this.abschlussQuittierungTasklet = abschlussQuittierungTasklet;
        this.kundenTypDecider = kundenTypDecider;
    }

    @Bean
    public Step bestellungErfassenStep() {
        return new StepBuilder("bestellungErfassenStep", jobRepository)
                .tasklet(bestellungErfassenTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step premiumRabattStep() {
        return new StepBuilder("premiumRabattStep", jobRepository)
                .tasklet(premiumRabattTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step standardBearbeitungStep() {
        return new StepBuilder("standardBearbeitungStep", jobRepository)
                .tasklet(standardBearbeitungTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step fehlerBehandlungStep() {
        return new StepBuilder("fehlerBehandlungStep", jobRepository)
                .tasklet(fehlerProtokollTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step abschlussQuittierungStep() {
        return new StepBuilder("abschlussQuittierungStep", jobRepository)
                .tasklet(abschlussQuittierungTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job bestellungsJob() {
        return new JobBuilder("bestellungsJob", jobRepository)

                // Schritt 1: immer ausführen
                .start(bestellungErfassenStep())

                // Decider: entscheidet nach Schritt 1 über den weiteren Pfad
                // .next(decider) leitet die Kontrolle an den Decider weiter.
                // Der Decider gibt einen FlowExecutionStatus zurück, der die
                // folgende .on()-Kette auflöst.
                .next(kundenTypDecider)
                    .on(KundenTypDecider.PREMIUM).to(premiumRabattStep())

                // .from(decider) für jeden weiteren Ausgang des Deciders
                .from(kundenTypDecider)
                    .on(KundenTypDecider.STANDARD).to(standardBearbeitungStep())

                // Wildcard-Fallback: trifft alles, was nicht PREMIUM oder STANDARD ist
                .from(kundenTypDecider)
                    .on("*").to(fehlerBehandlungStep())

                // Alle drei Pfade münden im Abschluss-Step
                .from(premiumRabattStep()).on("*").to(abschlussQuittierungStep())
                .from(standardBearbeitungStep()).on("*").to(abschlussQuittierungStep())
                .from(fehlerBehandlungStep()).on("*").to(abschlussQuittierungStep())

                .end()
                .build();
    }
}
