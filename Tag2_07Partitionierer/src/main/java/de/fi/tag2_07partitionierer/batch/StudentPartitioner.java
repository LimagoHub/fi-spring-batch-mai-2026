package de.fi.tag2_07partitionierer.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class StudentPartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(StudentPartitioner.class);

    // Muss exakt mit dem SpEL-Ausdruck im FlatFileItemReader übereinstimmen
    static final String INPUT_FILE_KEY = "inputFile";

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> map = new HashMap<>();

        for (int i = 1; i <= gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            // Jeder Worker bekommt seinen eigenen Dateipfad in den ExecutionContext
            context.putString(INPUT_FILE_KEY, "work/chunk_" + i + ".csv");
            map.put("partition" + i, context);
        }

        log.info("Partitioner erstellt {} Partitionen", gridSize);
        return map;
    }
}
