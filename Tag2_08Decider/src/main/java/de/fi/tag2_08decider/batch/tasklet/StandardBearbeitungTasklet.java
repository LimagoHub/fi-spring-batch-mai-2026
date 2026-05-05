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
 * Schritt 2b (nur STANDARD-Pfad): Berechnet 5,99€ Versandkosten.
 *
 * Liest aus dem JobExecutionContext (von Schritt 1 geschrieben):
 *   "bestellungId", "betrag"
 *
 * Schreibt zurück in den JobExecutionContext (für Schritt 3 lesbar):
 *   "versandkosten"    (double)
 *   "endBetrag"        (double)
 *   "verarbeitungsTyp" = "STANDARD"
 */
@Component
public class StandardBearbeitungTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(StandardBearbeitungTasklet.class);

    private static final double VERSANDKOSTEN = 5.99;

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

        // Geschäftslogik: Versandkosten aufschlagen
        double endBetrag = Math.round((betrag + VERSANDKOSTEN) * 100.0) / 100.0;

        log.info("STANDARD-Verarbeitung: Bestellung {} — {}€ + {}€ Versand = {}€",
                bestellungId, betrag, VERSANDKOSTEN, endBetrag);

        jobContext.putDouble("versandkosten", VERSANDKOSTEN);
        jobContext.putDouble("endBetrag", endBetrag);
        jobContext.putString("verarbeitungsTyp", "STANDARD");

        return RepeatStatus.FINISHED;
    }
}
