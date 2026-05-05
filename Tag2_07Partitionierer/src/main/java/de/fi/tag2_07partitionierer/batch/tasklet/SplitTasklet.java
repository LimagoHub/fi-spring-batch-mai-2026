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
import java.util.ArrayList;
import java.util.List;

public class SplitTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(SplitTasklet.class);

    private static final int PARTITIONS     = 4;
    private static final int LINES_PER_PART = 250;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) throws Exception {

       // Path inboxFile = Paths.get("C:/Users/JoachimWagner/Documents/git/FinanzInformatik/fi-spring-batch-februar-2026/Tag2_07Partitionierer/src/main/resources/inbox/student.csv");
        Path inboxFile = Paths.get("inbox/student.csv");
        Path workDir   = Paths.get("work");

        // work/-Verzeichnis anlegen falls es noch nicht existiert
        Files.createDirectories(workDir);

        List<String> allLines  = Files.readAllLines(inboxFile);
        String       header    = allLines.get(0);
        // Datenzeilen ohne Header
        List<String> dataLines = allLines.subList(1, allLines.size());

        for (int i = 0; i < PARTITIONS; i++) {
            int from = i * LINES_PER_PART;
            int to   = Math.min(from + LINES_PER_PART, dataLines.size());

            List<String> chunk = new ArrayList<>();
            // Header in jede Teildatei kopieren, damit FlatFileItemReader die Spalten kennt
            chunk.add(header);
            chunk.addAll(dataLines.subList(from, to));

            Path target = workDir.resolve("chunk_" + (i + 1) + ".csv");
            Files.write(target, chunk);
            log.debug("Geschrieben: {} ({} Datenzeilen)", target.getFileName(), chunk.size() - 1);
        }

        log.info("Split abgeschlossen: {} Dateien in {}", PARTITIONS, workDir.toAbsolutePath());
        return RepeatStatus.FINISHED;
    }
}
