package de.fi.testdemo;

import de.fi.testdemo.entity.Person;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JsonFileItemWriter;
import org.springframework.batch.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.io.IOException;

@Configuration
public class JobConfig {
    @Autowired
    private JobRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // --- Einfacher Tasklet-Job: zeigt das Grundprinzip ohne Reader/Processor/Writer ---

    @Bean
    public Step meinEinsStep()
    {
        // Beide Steps tragen absichtlich denselben Step-Namen "meinLeerzeilenStep":
        // Spring Batch speichert Steps per Name in der Job-Repository-DB —
        // gleiche Namen bedeuten, dass nur ein Step-Eintrag geschrieben wird.
        // In der Praxis muss jeder Step einen eindeutigen Namen bekommen.
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet((contribution, chunkContext) -> {
            System.out.println( "Eins" );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }

    @Bean
    public Step meinZweiStep()
    {
        // Gleicher Name wie meinEinsStep: demonstriert absichtlich den Name-Konflikt
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet((contribution, chunkContext) -> {
            System.out.println( "Zwei" );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }

    @Bean
    @Qualifier("tasklet")
    public Job meinTaskletJob(final JobRepository repository, final PlatformTransactionManager transactionManager) throws Exception
    {
        // RunIdIncrementer: erzeugt bei jedem Start eine neue Job-Instanz,
        // damit Spring Batch den Job nicht als "bereits abgeschlossen" ablehnt
        return new JobBuilder("meinTaskletJob", repository).incrementer( new RunIdIncrementer() )
                .start( meinEinsStep())
                .next(  meinZweiStep() )
                .build();
    }

    // --- Chunk-orientierter Job: CSV lesen → transformieren → JSON schreiben ---

    @Bean
    @StepScope
    // @Profile("production"): der echte Reader soll im Test NICHT aktiv sein,
    // damit @MockitoBean in TestTest.java den Slot ungestört besetzen kann
    @Profile("production")
    public FlatFileItemReader<Person> reader() {
        var flatFileItemReader = new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data.csv"))
                .delimited()
                .names("firstName", "lastName", "age")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();
        //flatFileItemReader.setLinesToSkip(1);  // falls die CSV eine Kopfzeile hat
        return flatFileItemReader;
    }

    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    // JdbcBatchItemWriter bleibt auskommentiert als Vergleich zum JsonFileItemWriter:
    // beide schreiben dieselbe Person-Liste, aber in verschiedene Zielsysteme (DB vs. Datei)
    @Bean
    public JdbcBatchItemWriter<Person> writer(final DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      ItemReader<Person> reader,
                      PersonItemProcessor processor
                      //, JdbcBatchItemWriter<Person> writer   // Alternative: Schreiben in DB
            , JsonFileItemWriter<Person> writer
    ) {

        return new StepBuilder("step1", jobRepository)
                // chunk(10): nach je 10 Items eine Transaktion committen — Kompromiss zwischen
                // Durchsatz (große Chunks) und Fehlertoleranz (kleine Chunks bei Rollback)
                .<Person, Person>chunk(10, transactionManager)
                .reader(reader)
                .faultTolerant()
                // skipLimit(2): nach mehr als 2 übersprungenen Items bricht der Job ab,
                // damit stille Datenverluste nicht unbemerkt bleiben
                .skipLimit(2)
                .skip(FlatFileParseException.class)      // ungültige CSV-Zeilen überspringen
                .noSkip(FileNotFoundException.class)     // fehlende Datei ist ein Konfigurationsfehler → sofort abbrechen
                //.skipPolicy(createSkipPolicy())
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    // @Qualifier("chunk"): zwei Jobs in derselben Config (tasklet + chunk) benötigen
    // Qualifier, damit @Autowired-Injection in Tests eindeutig bleibt
    @Qualifier("chunk")
    public Job importUserJob(
            JobRepository jobRepository,
            JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)   // afterJob prüft, was in der DB gelandet ist
                .flow(step1)
                .end()
                .build();
    }

    @Bean
    // @StepScope + @Value SpEL: der Ausgabepfad kommt aus den JobParametern — so kann
    // jede Job-Instanz in eine andere Datei schreiben, ohne die Config zu ändern
    @StepScope
    public JsonFileItemWriter<Person> jsonItemWriter(
            @Value("#{jobParameters['file.output']}") String output) throws IOException {
        JsonFileItemWriterBuilder<Person> builder = new JsonFileItemWriterBuilder<>();
        JacksonJsonObjectMarshaller<Person> marshaller = new JacksonJsonObjectMarshaller<>();
        return builder
                .name("bookItemWriter")
                .jsonObjectMarshaller(marshaller)
                .resource(new FileSystemResource(output))
                .build();
    }
}
