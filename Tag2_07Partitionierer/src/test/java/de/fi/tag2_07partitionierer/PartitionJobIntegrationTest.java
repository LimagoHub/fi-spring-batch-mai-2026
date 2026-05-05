package de.fi.tag2_07partitionierer;

import de.fi.tag2_07partitionierer.db.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PartitionJobIntegrationTest {

    // JobRunner ist ein CommandLineRunner — ohne Mock würde er beim Start des
    // Spring-Contexts sofort den Job ausführen, bevor setUp() die CSV bereitgestellt hat.
    @MockitoBean
    JobRunner jobRunner;

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    Job partitionJob;

    @Autowired
    StudentRepository studentRepository;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Paths.get("inbox"));
        // student.csv liegt in src/main/resources/inbox/ und ist damit im Classpath verfügbar.
        // SplitTasklet erwartet sie jedoch im Arbeitsverzeichnis (Projektroot) unter inbox/.
        // Deshalb kopieren wir sie vor jedem Test dorthin.
        try (InputStream is = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("inbox/student.csv"),
                "inbox/student.csv nicht im Classpath gefunden")) {
            Files.copy(is, Paths.get("inbox/student.csv"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get("processed/student.csv"));
        Files.deleteIfExists(Paths.get("failed/student.csv"));

        // work/-Dateien bereinigen falls cleanupStep nicht gelaufen ist (z.B. bei Fehler-Tests)
        Path workDir = Paths.get("work");
        if (Files.exists(workDir)) {
            try (Stream<Path> files = Files.list(workDir)) {
                files.filter(Files::isRegularFile)
                     .forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
            }
        }

        // DB-Daten zurücksetzen — Batch-Metadaten bleiben, stören aber nicht (neue Timestamps)
        studentRepository.deleteAll();
    }

    private JobExecution runJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("zeitpunkt", System.currentTimeMillis())
                .toJobParameters();
        return jobLauncher.run(partitionJob, params);
    }

    @Test
    void jobSollteMitStatusCompletedEnden() throws Exception {
        JobExecution execution = runJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void jobSollteAlleStudentenInDbSchreiben() throws Exception {
        runJob();

        assertThat(studentRepository.count()).isEqualTo(1000L);
    }

    @Test
    void jobSollteCsvNachProcessedVerschieben() throws Exception {
        runJob();

        assertThat(Files.exists(Paths.get("processed/student.csv"))).isTrue();
        assertThat(Files.exists(Paths.get("inbox/student.csv"))).isFalse();
    }
}
