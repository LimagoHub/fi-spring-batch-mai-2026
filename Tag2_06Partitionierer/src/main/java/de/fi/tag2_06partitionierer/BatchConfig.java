package de.fi.tag2_06partitionierer;




import de.fi.tag2_06partitionierer.db.StudentRepository;
import de.fi.tag2_06partitionierer.db.entity.StudentEntity;
import de.fi.tag2_06partitionierer.processors.MyCompositeProcessor;
import de.fi.tag2_06partitionierer.processors.PersonItemToUpperProcessor;
import de.fi.tag2_06partitionierer.processors.PersonToStudentProcessor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.H2PagingQueryProvider;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class BatchConfig {

    // Chunk-Größe: wie viele Datensätze pro Transaktion gelesen/verarbeitet/geschrieben werden.
    // Bei einem Fehler wird nur der aktuelle Chunk zurückgerollt, nicht der gesamte Step.
    static final int processing_size = 100;

    @Bean
    public ColumnRangePartitioner partitioner(JdbcOperations jdbcTemplate,@Value("tbl_personen") String tableName, @Value("id") String column ) {
        return new ColumnRangePartitioner(jdbcTemplate, tableName, column);
    }

    // @StepScope ist zwingend, weil minValue/maxValue erst zur Laufzeit des Steps aus dem
    // ExecutionContext der jeweiligen Partition stammen — beim Application-Start existieren sie noch nicht.
    // Spring erzeugt daher einen Proxy, der die echten Werte erst beim ersten Zugriff injiziert.
    @Bean
    @StepScope
    public JdbcPagingItemReader<Person> reader(DataSource dataSource,
                                               @Value("#{stepExecutionContext['minValue']}") Long minValue,
                                               @Value("#{stepExecutionContext['maxValue']}") Long maxValue) {

        JdbcPagingItemReader<Person> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setFetchSize(processing_size);
        reader.setRowMapper((rs, i) -> new Person(rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name")));

        H2PagingQueryProvider queryProvider = new H2PagingQueryProvider();
        queryProvider.setSelectClause("id, first_name, last_name");
        queryProvider.setFromClause("tbl_personen");
        // WHERE-Klausel begrenzt jede Partition auf ihren zugewiesenen ID-Bereich
        queryProvider.setWhereClause("id >= " + minValue + " AND id <= " + maxValue);
        queryProvider.setSortKeys(Map.of("id", Order.ASCENDING));

        reader.setQueryProvider(queryProvider);
        return reader;
    }

    //@Bean
    //@StepScope
    // Ursprüngliche Variante: Ausgabe in partitionierte CSV-Dateien je Worker.
    // Ersetzt durch RepositoryItemWriter, da die Daten direkt in die DB persistiert werden sollen.
    /*public FlatFileItemWriter<Person> writer(@Value("#{stepExecutionContext['minValue']}") String min) {
        FlatFileItemWriter<Person> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource("outputs/persons_part_" + min + ".csv"));
        writer.setAppendAllowed(false);
        writer.setLineAggregator(new DelimitedLineAggregator<>() {{
            setDelimiter(",");
            setFieldExtractor(new BeanWrapperFieldExtractor<>() {{
                setNames(new String[]{"id", "firstName", "lastName"});
            }});
        }});
        return writer;
    }
    */

    // @StepScope stellt sicher, dass jeder parallele Worker-Thread eine eigene Writer-Instanz bekommt
    // und sich die Threads nicht gegenseitig beim Schreiben blockieren.
    @Bean
    @StepScope
    public RepositoryItemWriter<StudentEntity> repositoryItemWriter(StudentRepository studentRepository) {
        RepositoryItemWriter<StudentEntity> writer = new RepositoryItemWriter<>();
        writer.setRepository(studentRepository);
        writer.setMethodName("save");
        return writer;
    }
    //@Bean
    public ItemProcessor<Person, Person> identityProcessor() {
        return item -> item;   // Identity
    }

    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager txManager, DataSource ds, MyCompositeProcessor myCompositeProcessor, RepositoryItemWriter<StudentEntity> writer ) {
        // reader(ds, null, null): Die null-Werte sind Platzhalter für die Bean-Definition.
        // Zur Laufzeit ersetzt Spring Batch sie durch die echten Partitionsgrenzen aus dem ExecutionContext.
        return new StepBuilder("workerStep", jobRepository)
                .<Person, StudentEntity>chunk(processing_size, txManager)
                .reader(reader(ds, null, null))
                .processor(myCompositeProcessor)
                .writer(writer)
                .build();
    }

    //@Bean
    /*public ItemProcessor<Person,Person> createPersonItemProcessor() {
        return new ItemProcessor<Person, Person>(){
            @Override
            public Person process(Person person) throws Exception {
                return new Person(person.id(), person.firstName().toUpperCase(), person.lastName().toUpperCase());
            }
        }
    }*/

    @Bean
    public Step masterStep(JobRepository jobRepository, PlatformTransactionManager txManager, Step workerStep, ColumnRangePartitioner partitioner) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner(workerStep.getName(), partitioner)
                .step(workerStep)
                // gridSize bestimmt, in wie viele Teilbereiche der ID-Bereich aufgeteilt wird.
                // Der SimpleAsyncTaskExecutor startet für jede Partition einen eigenen Thread (kein Pool-Limit!).
                .gridSize(4)
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .build();
    }



    @Bean
    public Step mergeStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("mergeStep", jobRepository)
                .tasklet(new FileMergingTasklet(), txManager)
                .build();
    }

    @Bean
    public Job partitionJob(JobRepository jobRepository, Step masterStep, Step mergeStep) {
        return new JobBuilder("partitionJob", jobRepository)
                // mergeStep ist auskommentiert, weil der Writer auf DB umgestellt wurde —
                // ein Zusammenführen von CSV-Dateien ist daher nicht mehr nötig.
                .start(masterStep)
                //.next(mergeStep)
                .build();
    }
/*
    @Bean
    public Job partitionJob(JobRepository jobRepository, Step masterStep) {
        return new JobBuilder("partitionJob", jobRepository)
                .start(masterStep)
                .build();
    }
    */

}