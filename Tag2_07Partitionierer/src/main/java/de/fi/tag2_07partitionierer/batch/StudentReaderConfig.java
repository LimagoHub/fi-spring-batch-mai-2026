package de.fi.tag2_07partitionierer.batch;

import de.fi.tag2_07partitionierer.model.Student;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

@Configuration
public class StudentReaderConfig {

    // @StepScope: Spring erstellt diese Bean erst wenn der Worker-Step startet,
    // nicht beim Hochfahren der Anwendung. Nur dann existiert der ExecutionContext
    // mit dem inputFile-Wert, den der StudentPartitioner hinterlegt hat.
    @Bean
    @StepScope
    public FlatFileItemReader<Student> studentItemReader(
            @Value("#{stepExecutionContext['inputFile']}") String inputFile) {

        return new FlatFileItemReaderBuilder<Student>()
                .name("studentReader")
                .resource(new FileSystemResource(inputFile))
                .delimited()
                .delimiter(",")
                .names("id", "vorname", "nachname")
                // Erste Zeile ist der Header — überspringen, nicht als Datensatz lesen
                .linesToSkip(1)
                .fieldSetMapper(fieldSet -> new Student(
                        fieldSet.readString("id"),
                        fieldSet.readString("vorname"),
                        fieldSet.readString("nachname")))
                .build();
    }
}
