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
 * Schritt 2c (Fehler-Pfad): Protokolliert unbekannte oder ungültige Kundentypen.
 * Wird vom Wildcard-Zweig "*" im Job-Flow erreicht — also immer dann, wenn weder
 * PREMIUM noch STANDARD erkannt wurde.
 *
 * Liest aus dem JobExecutionContext:
 *   "bestellungId"
 *
 * Schreibt zurück in den JobExecutionContext (für Schritt 3 lesbar):
 *   "fehlerGrund"      (String)
 *   "verarbeitungsTyp" = "FEHLER"
 */
@Component
public class FehlerProtokollTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FehlerProtokollTasklet.class);

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        ExecutionContext jobContext = chunkContext
                .getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        String bestellungId = jobContext.getString("bestellungId");
        // JobParameter direkt aus dem StepContext lesen — kein Umweg über ExecutionContext nötig
        String kundenTyp = (String) chunkContext.getStepContext()
                .getJobParameters().get("kundenTyp");

        String fehlerGrund = "Unbekannter Kundentyp: '" + kundenTyp + "'";

        log.warn("FEHLER-Behandlung: Bestellung {} — {}", bestellungId, fehlerGrund);

        jobContext.putString("fehlerGrund", fehlerGrund);
        jobContext.putString("verarbeitungsTyp", "FEHLER");

        return RepeatStatus.FINISHED;
    }
}
