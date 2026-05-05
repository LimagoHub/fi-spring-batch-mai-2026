package de.fi.tag2_08decider.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Schritt 3 (immer): Liest alle im JobExecutionContext angesammelten Werte
 * und druckt eine abschließende Zusammenfassung.
 *
 * Dieser Step demonstriert, wie Daten über den gesamten Job-Lebenszyklus
 * hinweg gesammelt und am Ende ausgewertet werden können — unabhängig davon,
 * welcher Pfad (PREMIUM / STANDARD / FEHLER) vorher durchlaufen wurde.
 *
 * Liest aus dem JobExecutionContext (von Schritten 1 und 2 geschrieben):
 *   "bestellungId", "artikel", "betrag", "verarbeitungsTyp"
 *   + pfadspezifische Werte: "rabatt"/"endBetrag" oder "versandkosten"/"endBetrag" oder "fehlerGrund"
 */
@Component
public class AbschlussQuittierungTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AbschlussQuittierungTasklet.class);

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        ExecutionContext jobContext = chunkContext
                .getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        // Basiswerte — immer vorhanden (von Schritt 1)
        String bestellungId = jobContext.getString("bestellungId");
        String artikel      = jobContext.getString("artikel");
        double betrag       = jobContext.getDouble("betrag");
        String verarbTyp    = jobContext.getString("verarbeitungsTyp");

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║          ABSCHLUSS-QUITTIERUNG               ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Bestellung:    {}", bestellungId);
        log.info("║  Artikel:       {}", artikel);
        log.info("║  Betrag:        {}€", betrag);
        log.info("║  Verarbeitung:  {}", verarbTyp);
        log.info("╠══════════════════════════════════════════════╣");

        // Pfadspezifische Werte — hängen vom Decider-Ergebnis ab
        switch (verarbTyp) {
            case "PREMIUM" -> {
                double rabatt    = jobContext.getDouble("rabatt");
                double endBetrag = jobContext.getDouble("endBetrag");
                log.info("║  Rabatt (20%):  {}€", rabatt);
                log.info("║  Endbetrag:     {}€", endBetrag);
            }
            case "STANDARD" -> {
                double versand   = jobContext.getDouble("versandkosten");
                double endBetrag = jobContext.getDouble("endBetrag");
                log.info("║  Versandkosten: {}€", versand);
                log.info("║  Endbetrag:     {}€", endBetrag);
            }
            case "FEHLER" -> {
                String fehler = jobContext.getString("fehlerGrund");
                log.warn("║  FEHLER:        {}", fehler);
            }
            default -> log.warn("║  Unbekannter Verarbeitungstyp: {}", verarbTyp);
        }

        log.info("╚══════════════════════════════════════════════╝");

        return RepeatStatus.FINISHED;
    }
}
