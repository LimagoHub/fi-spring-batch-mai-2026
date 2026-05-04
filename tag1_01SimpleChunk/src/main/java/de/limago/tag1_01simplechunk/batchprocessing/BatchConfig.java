package de.limago.tag1_01simplechunk.batchprocessing;


import de.limago.tag1_01simplechunk.entity.Person;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;


import javax.sql.DataSource;

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
                      JdbcBatchItemWriter<Person> writer) {

        return new StepBuilder("step1", jobRepository)
                // Chunk-orientierte Verarbeitung: Spring Batch liest, verarbeitet
                // und schreibt immer in Blöcken (Chunks) der Größe 10.
                // Erst wenn 10 Datensätze gelesen und verarbeitet wurden, schreibt
                // der Writer sie in einer einzigen Transaktion in die DB.
                // Das reduziert die Anzahl der Datenbank-Commits erheblich.
                .<Person, Person>chunk(10, transactionManager)

                .reader(reader)


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
    public Job importUserJob(JobRepository jobRepository, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                // RunIdIncrementer sorgt dafür, dass derselbe Job mehrfach gestartet
                // werden kann. Ohne Incrementer würde Spring Batch einen bereits
                // abgeschlossenen Job nicht erneut starten, da die Parameter identisch wären.
                // Der Incrementer fügt automatisch eine eindeutige run.id hinzu.
                .incrementer(new RunIdIncrementer())


                // Definiert den Ablauf des Jobs. .flow() leitet zu step1 weiter;
                // .end() beendet den Flow-Zweig.
                .flow(step1)
                .end()
                .build();
    }

}
