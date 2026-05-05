package de.limago.uebung.batch.partitioner;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

// Teilt tbl_humans anhand von OFFSET/LIMIT in gridSize Partitionen auf.
// Da die Tabelle keine numerische ID besitzt, kann keine ID-Range-Strategie
// verwendet werden. Stattdessen erhaelt jede Partition einen Bereich per Zeilennummer.
@Component
public class HumanPartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    public HumanPartitioner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tbl_humans", Integer.class);
        if (total == null || total == 0) {
            return Map.of();
        }

        // Wie viele Zeilen bekommt jede Partition? Aufrunden, damit keine Zeile verloren geht.
        int partitionSize = (int) Math.ceil((double) total / gridSize);

        Map<String, ExecutionContext> partitions = new HashMap<>();
        int offset = 0;
        for (int i = 0; i < gridSize && offset < total; i++) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putInt("offset", offset);
            ctx.putInt("limit", partitionSize);
            partitions.put("partition" + i, ctx);
            offset += partitionSize;
        }
        return partitions;
    }
}
