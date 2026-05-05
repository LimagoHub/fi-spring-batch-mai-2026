package de.fi.tag2_08decider.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.stereotype.Component;

/**
 * Entscheidet, welcher Step als nächstes läuft.
 *
 * Entscheidungsgrundlage:
 *   1. Job-Parameter 'kundenTyp' (von außen übergeben)
 *   2. 'betrag' aus dem JobExecutionContext (vom vorherigen Step geschrieben)
 *
 * Mögliche Ausgaben (FlowExecutionStatus):
 *   "PREMIUM"  → premiumRabattStep
 *   "STANDARD" → standardBearbeitungStep
 *   "*"        → fehlerBehandlungStep  (Wildcard-Fallback)
 */
@Component
public class KundenTypDecider implements JobExecutionDecider {

    private static final Logger log = LoggerFactory.getLogger(KundenTypDecider.class);

    // Konstanten statt Magic Strings: Tippfehler werden vom Compiler gefunden
    public static final String PREMIUM  = "PREMIUM";
    public static final String STANDARD = "STANDARD";
    // FEHLER wird nicht als Konstante benötigt — im Job wird "*" als Wildcard verwendet

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {

        // JobParameter lesen — wird beim Starten des Jobs mitgegeben
        String kundenTyp = jobExecution.getJobParameters().getString("kundenTyp", "");

        // Wert aus dem JobExecutionContext lesen — wurde von BestellungErfassenTasklet gesetzt
        Double betrag = (Double) jobExecution.getExecutionContext().get("betrag");

        log.info("Decider wertet aus: kundenTyp='{}', betrag={}", kundenTyp, betrag);

        // Vorprüfung: Betrag muss vorhanden und positiv sein
        if (betrag == null || betrag <= 0) {
            log.warn("Ungültiger Betrag {} — leite in Fehler-Pfad um", betrag);
            return new FlowExecutionStatus("FEHLER");
        }

        return switch (kundenTyp.toUpperCase()) {
            case "PREMIUM" -> {
                log.info("Premium-Kunde erkannt → Pfad: PREMIUM");
                yield new FlowExecutionStatus(PREMIUM);
            }
            case "STANDARD" -> {
                log.info("Standard-Kunde erkannt → Pfad: STANDARD");
                yield new FlowExecutionStatus(STANDARD);
            }
            default -> {
                log.warn("Unbekannter Kundentyp '{}' → Pfad: FEHLER (Wildcard)", kundenTyp);
                yield new FlowExecutionStatus("FEHLER");
            }
        };
    }
}
