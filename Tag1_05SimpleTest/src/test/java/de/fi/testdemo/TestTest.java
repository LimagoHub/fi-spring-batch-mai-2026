package de.fi.testdemo;

import de.fi.testdemo.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// @ActiveProfiles("test"): schaltet auf H2-In-Memory-Datenbank um,
// damit der Test keine echte DB benötigt und auf jedem Rechner läuft
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@SpringBootTest
// @SpringBatchTest: registriert JobLauncherTestUtils und JobRepositoryTestUtils als Beans —
// das erspart manuelles Konfigurieren von JobLauncher und JobExplorer im Test
@SpringBatchTest
// @Sql: baut Tabellen und Testdaten vor jedem Test neu auf, damit Tests unabhängig bleiben
@Sql({"/create.sql", "/insert.sql"})
class TestTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    private JdbcTemplate jdbcTemplate;

    @Autowired private JobExplorer jobExplorer;
    @Autowired
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // @MockitoBean: ersetzt den echten FlatFileItemReader (nur im Profil "production" aktiv)
    // durch einen Mock — so kann der Test kontrollierte Testdaten liefern, ohne eine CSV-Datei
    @MockitoBean

    private ItemReader<Person> flatFileItemReaderMock;

    @Test
    void firsttest() throws Exception {
        // Smoke-Test: prüft nur, ob die Datenbankverbindung im Testkontext verfügbar ist
        System.out.println(jdbcTemplate.getDataSource().getConnection());
    }

    @Test
    void testJob(@Autowired  @Qualifier("chunk") Job job) throws Exception {

        // Mock liefert 2 Datensätze, dann null (= Ende der Eingabe)
        Mockito.when(flatFileItemReaderMock.read())
                .thenReturn(Person.builder().firstName("Max").lastName("Mustermann").age(18).build())
                .thenReturn(Person.builder().firstName("Erika").lastName("Mustermann").age(21).build())
                .thenReturn(null);

        // file.output als JobParameter: der JsonFileItemWriter braucht den Pfad zur Laufzeit
        JobParameters param = new JobParametersBuilder().addString("file.output", "output.json").toJobParameters();

        this.jobLauncherTestUtils.setJob(job);
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(param);
        printStepExecutions(jobExecution);
        // Golden-File-Vergleich: input.json ist die erwartete Ausgabe (manuell erstellt),
        // output.json ist das tatsächliche Ergebnis des Jobs — so fallen Regressionsfehler auf
        assertThat(new File("input.json")).hasSameTextualContentAs(new File("output.json"));
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

    }

    /** Zeige die StepExecutions, die JobExecution und den Job-ExecutionContext */
    private static void printStepExecutions( JobExecution je )
    {
        for( StepExecution se : je.getStepExecutions() ) {
            String s = se.getExitStatus().getExitDescription();
            if( s != null && s.length() > 0 ) { s = ", ExitDescription = " + s; }
            System.out.println( "StepExecution " + se.getId() + ": CommitCount = " + se.getCommitCount() +
                    ", Status = " + se.getStatus() + ", ExitStatus = " + se.getExitStatus().getExitCode() +
                    ", StepName = " + se.getStepName() + s );
        }
        String s = je.getExitStatus().getExitDescription();
        if( s != null && s.length() > 0 ) { s = ", ExitDescription = " + s; }
        System.out.println( "JobExecution  " + je.getId() + ": JobId = " + je.getJobId() +
                ",       Status = " + je.getStatus() + ", ExitStatus = " + je.getExitStatus().getExitCode() + s );
        ExecutionContext ec = je.getExecutionContext();
        if( ec != null && !ec.isEmpty() ) {
            for( Map.Entry<String, Object> e : ec.entrySet() ) {
                System.out.println( "Job-ExecutionContext: " + e.getKey() + ": " + e.getValue() );
            }
        }
    }

}
