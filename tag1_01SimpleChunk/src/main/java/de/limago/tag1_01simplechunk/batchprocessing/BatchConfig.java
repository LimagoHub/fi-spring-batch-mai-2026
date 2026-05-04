package de.limago.tag1_01simplechunk.batchprocessing;


import de.limago.tag1_01simplechunk.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;


import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class BatchConfig {


    @Bean
    @StepScope
    public FlatFileItemReader<Person> reader() {

        var flatFileItemReader = new FlatFileItemReaderBuilder<Person>()

        // Eindeutiger Name des Readers – wird im JobRepository zur
        // Wiederaufnahme (Restart) verwendet, um den Lesefortschritt
        // (letzte verarbeitete Zeile) wiederherzustellen.
                .name("personItemReader")
                // Quelle: sample-data.csv liegt im Classpath (src/main/resources).
                // ClassPathResource sucht die Datei relativ zum Classpath-Wurzelverzeichnis.
                .resource(new ClassPathResource("sample-data.csv"))
                // Das CSV ist kommagetrennt (Standard-Delimiter).
                // .delimited() aktiviert den DelimitedLineTokenizer.
                .delimited()
                // Spaltennamen in der Reihenfolge der CSV-Felder.
                // Diese Namen müssen den Feldern der Ziel-Klasse Person entsprechen.
                .names("firstName", "lastName", "age")
                // BeanWrapperFieldSetMapper mappt die CSV-Felder automatisch
                // auf die gleichnamigen Setter-Methoden der Person-Klasse
                // (z. B. "firstName" → setFirstName(...)).
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }}).build();

        // Optional: Kopfzeile überspringen (z. B. wenn die CSV eine Header-Zeile hat).
        // Hier auskommentiert, weil sample-data.csv keine Kopfzeile besitzt.
        //flatFileItemReader.setLinesToSkip(1);

        return flatFileItemReader;
    }

    // -------------------------------------------------------------------------
    // PROCESSOR
    // -------------------------------------------------------------------------

    // Der Processor liegt in einer eigenen Klasse (PersonItemProcessor).
    // Er empfängt jedes vom Reader gelesene Person-Objekt, transformiert es
    // (z. B. Namen in Großbuchstaben) und gibt das veränderte Objekt zurück.
    // Gibt der Processor null zurück, wird der Datensatz gefiltert (nicht geschrieben).
    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    // Der Writer persistiert die verarbeiteten Person-Objekte in die Datenbank.
    // DataSource wird von Spring Boot automatisch aus application.properties konfiguriert
    // und hier per Dependency Injection übergeben.
    @Bean
    public JdbcBatchItemWriter<Person> writer(final DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                // BeanPropertyItemSqlParameterSourceProvider leitet die Named Parameters
                // des SQL-Statements (:firstName, :lastName) automatisch aus den
                // gleichnamigen Getter-Methoden des Person-Objekts ab.
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())

                // Das INSERT-Statement schreibt firstName und lastName in die Tabelle people.
                // Das Feld "age" wird hier bewusst nicht gespeichert (nur transformierte Daten).
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")

                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager, FlatFileItemReader<Person> reader,
                      PersonItemProcessor processor,
                      JdbcBatchItemWriter<Person> writer, SkipPolicy skip) {

        return new StepBuilder("step1", jobRepository)
                // Chunk-orientierte Verarbeitung: Spring Batch liest, verarbeitet
                // und schreibt immer in Blöcken (Chunks) der Größe 10.
                // Erst wenn 10 Datensätze gelesen und verarbeitet wurden, schreibt
                // der Writer sie in einer einzigen Transaktion in die DB.
                // Das reduziert die Anzahl der Datenbank-Commits erheblich.
                .<Person, Person>chunk(10, transactionManager)

                .reader(reader)
                // faultTolerant() schaltet die Fehlertoleranz-Infrastruktur ein.
                // Erst danach können .skipPolicy(), .skipLimit(), .retry() etc.
                // konfiguriert werden. Ohne diesen Aufruf sind diese Methoden
                // nicht verfügbar.
                .faultTolerant()
                // Alternative zu skipPolicy: feste Obergrenze für überspringbare Fehler.
                // Wird skipLimit überschritten, bricht der Step mit Fehler ab.
                //.skipLimit(2)

                // Alternative ohne eigene SkipPolicy: einzelne Exception-Typen
                // direkt angeben, die übersprungen werden dürfen.
                //.skip(FlatFileParseException.class)

                // Bestimmte Exceptions vom Skipping ausschließen (haben Vorrang).
                //.noSkip(FileNotFoundException.class)

                .skipPolicy(skip)

                .processor(processor)
                .writer(writer)
                .build();
    }

    // -------------------------------------------------------------------------
    // JOB
    // -------------------------------------------------------------------------

    // Ein Job fasst einen oder mehrere Steps zu einem zusammenhängenden
    // Batch-Prozess zusammen.
    @Bean
    public Job importUserJob(JobRepository jobRepository, JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                // RunIdIncrementer sorgt dafür, dass derselbe Job mehrfach gestartet
                // werden kann. Ohne Incrementer würde Spring Batch einen bereits
                // abgeschlossenen Job nicht erneut starten, da die Parameter identisch wären.
                // Der Incrementer fügt automatisch eine eindeutige run.id hinzu.
                .incrementer(new RunIdIncrementer())
                // Der Listener (JobCompletionNotificationListener) reagiert auf
                // Job-Lifecycle-Ereignisse wie COMPLETED oder FAILED.
                .listener(listener)

                // Definiert den Ablauf des Jobs. .flow() leitet zu step1 weiter;
                // .end() beendet den Flow-Zweig.
                .flow(step1)
                .end()
                .build();
    }

    // -------------------------------------------------------------------------
    // SKIP POLICY
    // -------------------------------------------------------------------------

    // Eine SkipPolicy entscheidet für jede auftretende Exception, ob der
    // fehlerhafte Datensatz übersprungen werden soll (return true) oder
    // ob der Step abbrechen soll (return false / Exception werfen).
    @Bean
    SkipPolicy createSkipPolicy() {

        // Anonyme Implementierung des SkipPolicy-Interfaces.
        // In einem echten Projekt würde man diese in eine eigene Klasse auslagern.
        return new SkipPolicy() {

            // Eigener Logger für fehlerhafte Datensätze, damit diese separat
            // protokolliert werden können (z. B. in eine eigene Log-Datei).
            private final Logger logger = LoggerFactory.getLogger("badRecordLogger");

            // Diese Methode wird von Spring Batch für jede Exception aufgerufen,
            // die beim Lesen, Verarbeiten oder Schreiben eines Datensatzes auftritt.
            //
            // exception  – die aufgetretene Exception
            // skipCount  – Anzahl der bisher übersprungenen Datensätze in diesem Step
            //
            // Rückgabe true  → Datensatz überspringen, Verarbeitung fortsetzen
            // Rückgabe false → Step mit Fehler abbrechen
            @Override
            public boolean shouldSkip(final Throwable exception, final long skipCount)
                    throws SkipLimitExceededException {

                if (exception instanceof FileNotFoundException) {
                    // Die Eingabedatei fehlt komplett → kein Überspringen sinnvoll,
                    // da kein einziger Datensatz verarbeitet werden kann.
                    return false;

                } else if (exception instanceof FlatFileParseException && skipCount <= 2) {
                    // Eine einzelne CSV-Zeile konnte nicht geparst werden (z. B. falsches
                    // Format, fehlende Felder). Bis zu 2 solcher Zeilen werden übersprungen.

                    FlatFileParseException ffpe = (FlatFileParseException) exception;

                    // Fehlermeldung zusammenbauen, die den fehlerhaften Datensatz
                    // und seine Zeilennummer in der Datei benennt.
                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("An error occured while processing the ");
                    // getLineNumber() liefert die 1-basierte Zeilennummer in der CSV.
                    errorMessage.append(ffpe.getLineNumber());
                    errorMessage.append(" line of the file '");

                    // Dateinamen aus dem Exception-String per Regex extrahieren.
                    // Das Muster sucht nach einem Nicht-Wort-Zeichen gefolgt von
                    // Wort-Zeichen und der Endung .csv (z. B. /sample-data.csv).
                    Pattern pattern = Pattern.compile(".*(\\W\\w+\\.csv).*");
                    Matcher matcher = pattern.matcher(ffpe.toString());
                    if (matcher.matches())
                        errorMessage.append(matcher.group(1)); // gefundener Dateiname
                    else
                        errorMessage.append("unknown");        // Fallback

                    errorMessage.append("'. Below was the faulty input.");
                    errorMessage.append("\n");
                    // getInput() liefert den Rohtext der fehlerhaften CSV-Zeile.
                    errorMessage.append(ffpe.getInput());
                    errorMessage.append("\n");

                    // Fehlermeldung auf ERROR-Level loggen, damit sie im Monitoring
                    // auffällt und nachvollziehbar bleibt.
                    logger.error("{}", errorMessage.toString());

                    // Datensatz überspringen → Verarbeitung läuft weiter.
                    return true;

                } else {
                    // Alle anderen Exceptions (unbekannte Fehler, skipCount > 2):
                    // Step abbrechen und Fehler propagieren.
                    return false;
                }
            }
        };
    }
}
