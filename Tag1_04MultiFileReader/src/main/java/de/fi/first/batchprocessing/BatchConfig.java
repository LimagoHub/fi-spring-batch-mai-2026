package de.fi.first.batchprocessing;

import de.fi.first.business.BlacklistService;
import de.fi.first.entity.Person;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {





    @Bean
    @StepScope // Eigene Reader-Instanz pro Step-Ausführung: der Reader hält intern einen Datei-Cursor
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                // kein .resource() hier — der MultiResourceItemReader setzt die aktuelle
                // Datei nacheinander auf jeden delegate-Reader (eine Datei nach der anderen)
                .delimited()
                // Spaltennamen müssen exakt den Setter-Namen der Person-Klasse entsprechen,
                // damit BeanWrapperFieldSetMapper die Werte automatisch einsetzen kann
                .names("firstName", "lastName", "age")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();

    }


    /*

    // "file:" statt "classpath:" ist entscheidend: der SystemCommandTasklet (step1) kopiert Dateien
            // erst zur Laufzeit ins input-Verzeichnis — classpath: würde nur beim Build eingefrorene
            // Ressourcen sehen und die neu kopierten Dateien ignorieren
            @Value("file:src/main/resources/input/*.csv") Resource[] inputResources)

     */

    @Bean
    public MultiResourceItemReader<Person> multiResourceReader(
            // classpath:input/*.csv — alle CSV-Dateien im classpath-Ordner werden per Wildcard gefunden;
            // funktioniert nur für Dateien, die beim Build im Classpath lagen (keine zur Laufzeit erstellten)
            @Value("classpath:input/*.csv") Resource[] inputResources ) {
        return new MultiResourceItemReaderBuilder<Person>()
                .name("multiResourceReader")
                // delegate ist der eigentliche Zeilen-Reader; MultiResourceItemReader wechselt
                // die Ressource nach jeder vollständig gelesenen Datei
                .delegate(reader())
                .resources(inputResources)
                .build();
    }

    @Bean
    public ItemProcessor<Person, Person> filter(BlacklistService  blacklistService) {
        return person -> {
           if(blacklistService.isBlacklisted(person)) return null;
            return person;
        };
    }

    // Dummy Processor — keine @Bean, da er nur inline als Lambda benötigt wird
    public ItemProcessor<Person, Person> ageProcessor() {
        return person -> {
            System.out.println("Verarbeite: " + person.getFirstName() + " " + person.getLastName());
            person.setAge(person.getAge() + 10);
            return person;
        };
    }

    // Dummy Processor — keine @Bean, da er nur inline als Lambda benötigt wird
    public ItemProcessor<Person, Person> toUpperProcessor() {
        return person -> {
            System.out.println("Verarbeite: " + person.getFirstName() + " " + person.getLastName());
            person.setFirstName(person.getFirstName().toUpperCase());
            return person;
        };
    }

    public ItemProcessor<Person, Person> compositeProcessor() {
        CompositeItemProcessor<Person, Person> compositeProcessor = new CompositeItemProcessor<>(ageProcessor(), toUpperProcessor());
        return compositeProcessor;
    }

    // Dummy Writer — gibt Items nur auf der Konsole aus statt sie zu persistieren
    public ItemWriter<Person> writer() {
        return items -> {
            for (Person p : items) {
                System.out.println("Schreibe: " + p);
            }
        };
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      MultiResourceItemReader<Person> multiResourceReader) {
        return new StepBuilder("step1", jobRepository)
                // chunk(2): nach je 2 gelesenen Items wird eine Transaktion committed;
                // kleiner Wert hier, um das Chunk-Verhalten im Log deutlich sichtbar zu machen
                .<Person, Person>chunk(2, transactionManager)
                .reader(multiResourceReader)
                .processor(compositeProcessor())// Bad practice
                .writer(writer())
                .build();
    }

    @Bean
    public Job importUserJob(JobRepository jobRepository, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .start(step1)
                // kein .incrementer() — der Job wird einmalig gestartet; UUID im AppRunner übernimmt die Eindeutigkeit
                .build();
    }





}
