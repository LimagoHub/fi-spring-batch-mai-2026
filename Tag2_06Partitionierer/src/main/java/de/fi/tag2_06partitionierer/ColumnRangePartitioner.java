package de.fi.tag2_06partitionierer;



import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.HashMap;
import java.util.Map;

public class ColumnRangePartitioner implements Partitioner {


        private final JdbcOperations jdbcTemplate;
        private final String table;
        private final String column;

        public ColumnRangePartitioner(JdbcOperations jdbcTemplate, String table, String column) {
            this.jdbcTemplate = jdbcTemplate;
            this.table = table;
            this.column = column;
        }

        @Override
        public Map<String, ExecutionContext> partition(int gridSize) {
            // MIN/MAX werden zur Laufzeit abgefragt, damit der Partitioner auch bei wachsenden
            // Tabellen korrekt funktioniert — keine hartkodierten Grenzen.
            int min = jdbcTemplate.queryForObject("SELECT MIN(" + column + ") FROM " + table, Integer.class);
            int max = jdbcTemplate.queryForObject("SELECT MAX(" + column + ") FROM " + table, Integer.class);

            // +1 verhindert einen Off-by-One-Fehler: Integer-Division schneidet ab,
            // sodass der letzte ID-Bereich ohne +1 verloren gehen würde.
            int targetSize = (max - min) / gridSize + 1;

        Map<String, ExecutionContext> result = new HashMap<>();
        int number = 0;
        int start = min;
        int end = start + targetSize - 1;

        while (start <= max) {
            ExecutionContext value = new ExecutionContext();
            result.put("partition" + number, value);
            // Letztes Segment auf max kappen, falls die Range nicht glatt durch gridSize teilbar ist
            if (end > max) end = max;
            // minValue/maxValue müssen exakt mit den SpEL-Ausdrücken im @StepScope-Reader übereinstimmen
            value.putInt("minValue", start);
            value.putInt("maxValue", end);
            start += targetSize;
            end += targetSize;
            number++;
        }
        return result;
    }
}