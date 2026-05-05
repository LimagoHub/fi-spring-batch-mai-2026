package de.fi.tag2_07partitionierer.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileFailureListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FileFailureListener.class);

    // afterJob() wird immer aufgerufen — egal ob Erfolg oder Fehler.
    // Der CleanupTasklet übernimmt den Erfolgsfall; hier nur den Fehlerfall behandeln.
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            Path source = Paths.get("inbox/student.csv");
            Path failedDir = Paths.get("failed");
            try {
                if (Files.exists(source)) {
                    Files.createDirectories(failedDir);
                    Files.move(source, failedDir.resolve("student.csv"),
                            StandardCopyOption.REPLACE_EXISTING);
                    log.warn("Job fehlgeschlagen — students.csv nach failed/ verschoben");
                }
            } catch (Exception e) {
                log.error("Fehler beim Verschieben in failed/", e);
            }
        }
    }
}
