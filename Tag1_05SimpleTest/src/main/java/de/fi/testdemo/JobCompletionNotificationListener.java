package de.fi.testdemo;



import de.fi.testdemo.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// JobExecutionListener: Hook-Mechanismus, um vor/nach dem Job eigene Logik einzuklinken.
// Hier wird afterJob genutzt, um das Ergebnis zu verifizieren – typisches Muster für Schulungen.
@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // Nur bei erfolgreichem Abschluss prüfen – bei FAILED wären die Daten unvollständig
        if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("!!! JOB FINISHED! Time to verify the results");

            // Kontrollabfrage nach dem Job: zeigt, was tatsächlich in der DB gelandet ist
            jdbcTemplate.query("SELECT first_name, last_name FROM people",
                    (rs, row) -> new Person(
                            rs.getString(1),
                            rs.getString(2),0)
            ).forEach(person -> log.info("Found <{{}}> in the database.", person));
        }
    }
}