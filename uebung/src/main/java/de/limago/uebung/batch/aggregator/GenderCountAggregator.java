package de.limago.uebung.batch.aggregator;

import org.springframework.batch.core.partition.support.DefaultStepExecutionAggregator;
import org.springframework.batch.core.step.StepExecution;

import java.util.Collection;

// StepExecutionAggregator ist der Spring-Batch-native Weg, Partition-Ergebnisse
// zusammenzuführen. aggregate() bekommt alle Worker-StepExecutions als Collection
// übergeben – kein manuelles Filtern über getJobExecution().getStepExecutions() nötig.
//
// Ablauf im PartitionStep:
//   1. Alle Worker-Partitionen laufen (parallel)
//   2. aggregate() wird aufgerufen – Worker-Kontexte → master-StepExecution
//   3. ExecutionContextPromotionListener befördert die Werte in den JobExecutionContext
public class GenderCountAggregator extends DefaultStepExecutionAggregator {

    @Override
    public void aggregate(StepExecution result, Collection<StepExecution> partitions) {
        // Zuerst Standard-Aggregation: readCount, writeCount, exitStatus, ...
        // Das macht DefaultStepExecutionAggregator für uns.
        super.aggregate(result, partitions);

        int totalMale = 0;
        int totalFemale = 0;
        int totalNonBinary = 0;

        for (StepExecution partition : partitions) {
            var ctx = partition.getExecutionContext();
            totalMale      += ctx.getInt("count.male",      0);
            totalFemale    += ctx.getInt("count.female",    0);
            totalNonBinary += ctx.getInt("count.nonBinary", 0);
        }

        // In den ExecutionContext des master-Steps schreiben.
        // Der ExecutionContextPromotionListener befördert diese Werte danach
        // automatisch in den JobExecutionContext.
        var masterCtx = result.getExecutionContext();
        masterCtx.putInt("total.male",      totalMale);
        masterCtx.putInt("total.female",    totalFemale);
        masterCtx.putInt("total.nonBinary", totalNonBinary);
    }
}
