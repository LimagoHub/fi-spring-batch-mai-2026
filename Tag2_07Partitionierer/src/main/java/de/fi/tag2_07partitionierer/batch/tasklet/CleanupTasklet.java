package de.fi.tag2_07partitionierer.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class CleanupTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CleanupTasklet.class);

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) throws Exception {

        // work/-Verzeichnis leeren — die Chunk-Dateien werden nicht mehr gebraucht
        Path workDir = Paths.get("work");
        if (Files.exists(workDir)) {
            try (Stream<Path> files = Files.list(workDir)) {
                files.filter(Files::isRegularFile).forEach(f -> {
                    try {
                        Files.delete(f);
                        log.debug("Geloescht: {}", f.getFileName());
                    } catch (Exception e) {
                        log.warn("Konnte nicht loeschen: {}", f, e);
                    }
                });
            }
        }

        // Original-CSV nach processed/ verschieben — als Nachweis erfolgreicher Verarbeitung
        Path source = Paths.get("inbox/student.csv");
        Path processedDir = Paths.get("processed");
        Files.createDirectories(processedDir);
        Files.move(source, processedDir.resolve("student.csv"), StandardCopyOption.REPLACE_EXISTING);

        log.info("Cleanup abgeschlossen: work/ geleert, students.csv nach processed/ verschoben");
        return RepeatStatus.FINISHED;
    }
}
