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
 * Schritt 2a (nur PREMIUM-Pfad): Gewährt 20% Rabatt auf den Bestellbetrag.
 *
 * Liest aus dem JobExecutionContext (von Schritt 1 geschrieben):
 *   "bestellungId", "betrag"
 *
 * Schreibt zurück in den JobExecutionContext (für Schritt 3 lesbar):
 *   "rabatt"         (double)
 *   "endBetrag"      (double)
 *   "verarbeitungsTyp" = "PREMIUM"
 */
@Component
public class PremiumRabattTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PremiumRabattTasklet.class);

    private static final double RABATT_PROZENT = 0.20;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        ExecutionContext jobContext = chunkContext
                .getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        // Werte aus Schritt 1 lesen
        String bestellungId = jobContext.getString("bestellungId");
        double betrag       = jobContext.getDouble("betrag");

        // Geschäftslogik: 20% Rabatt für Premium-Kunden
        double rabatt    = Math.round(betrag * RABATT_PROZENT * 100.0) / 100.0;
        double endBetrag = Math.round((betrag - rabatt) * 100.0) / 100.0;

        log.info("PREMIUM-Verarbeitung: Bestellung {} — {}€ − 20% ({}) = {}€",
                bestellungId, betrag, rabatt, endBetrag);

        // Ergebniswerte in den JobExecutionContext schreiben — Schritt 3 liest sie
        jobContext.putDouble("rabatt", rabatt);
        jobContext.putDouble("endBetrag", endBetrag);
        jobContext.putString("verarbeitungsTyp", "PREMIUM");

        return RepeatStatus.FINISHED;
    }
}
