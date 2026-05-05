package de.fi.tag2_08decider.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Schritt 1 (immer): Simuliert das Laden einer Bestellung.
 *
 * Schreibt Bestelldaten in den JobExecutionContext — der einzige Weg,
 * wie Werte step-übergreifend weitergegeben werden können.
 *
 * Geschriebene Schlüssel:
 *   "bestellungId" (String)
 *   "artikel"      (String)
 *   "betrag"       (double)
 */
@Component
public class BestellungErfassenTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(BestellungErfassenTasklet.class);

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        // Simulierte Bestelldaten — in der Praxis aus DB oder Datei geladen
        String bestellungId = UUID.randomUUID().toString();
        String artikel      = "Spring-Batch-Fachbuch";
        double betrag       = 89.99;

        log.info("Bestellung erfasst: id={}, artikel='{}', betrag={}€",
                bestellungId, artikel, betrag);

        // JobExecutionContext holen — NICHT den StepExecutionContext!
        // Der JobExecutionContext überlebt den kompletten Job-Lebenszyklus und ist
        // für alle Steps sichtbar. Der StepExecutionContext gilt nur innerhalb
        // eines einzigen Steps.
        ExecutionContext jobContext = chunkContext
                .getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        jobContext.putString("bestellungId", bestellungId);
        jobContext.putString("artikel", artikel);
        jobContext.putDouble("betrag", betrag);

        log.debug("JobExecutionContext befüllt: bestellungId='{}', artikel='{}', betrag={}",
                bestellungId, artikel, betrag);

        return RepeatStatus.FINISHED;
    }
}
