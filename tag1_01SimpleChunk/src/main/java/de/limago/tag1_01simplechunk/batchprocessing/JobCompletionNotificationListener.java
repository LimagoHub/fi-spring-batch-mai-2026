package de.limago.tag1_01simplechunk.batchprocessing;


import de.limago.tag1_01simplechunk.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// @Component registriert den Listener als Spring-Bean, damit Spring ihn in
// BatchConfig per Injection an den JobBuilder übergeben kann (.listener(listener)).
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
        // Nur bei erfolgreichem Abschluss prüfen — bei FAILED wäre die Datenlage
        // unvollständig und eine Ausgabe aller Datensätze irreführend.
        if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("!!! JOB FINISHED! Time to verify the results");

            // Kontrollabfrage nach dem Job: Wurden die Daten wirklich geschrieben?
            // Im echten Betrieb würde man stattdessen die WriteCount aus
            // jobExecution.getStepExecutions() auswerten.
            jdbcTemplate.query("SELECT first_name, last_name FROM people",
                    (rs, row) -> new Person(
                            rs.getString(1),
                            rs.getString(2),0)
            ).forEach(person -> log.info("Found <{{}}> in the database.", person));
        }
    }
}