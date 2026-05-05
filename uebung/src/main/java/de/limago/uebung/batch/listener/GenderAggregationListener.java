package de.limago.uebung.batch.listener;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

// ERSETZT durch GenderCountAggregator + ExecutionContextPromotionListener.
// Hier als Referenz-Implementierung belassen: zeigt den Listener-Ansatz,
// bei dem man selbst über getStepExecutions() iterieren muss.
public class GenderAggregationListener implements StepExecutionListener {

    @Override
    public ExitStatus afterStep(StepExecution masterStepExecution) {
        int totalMale = 0;
        int totalFemale = 0;
        int totalNonBinary = 0;

        // getStepExecutions() liefert ALLE StepExecutions des Jobs – also den
        // masterStep selbst UND alle Partition-Worker.
        // Die Worker heißen "workerStep:partition0", "workerStep:partition1" usw.
        for (StepExecution se : masterStepExecution.getJobExecution().getStepExecutions()) {
            if (se.getStepName().startsWith("workerStep:")) {
                var ctx = se.getExecutionContext();
                totalMale      += ctx.getInt("count.male",      0);
                totalFemale    += ctx.getInt("count.female",    0);
                totalNonBinary += ctx.getInt("count.nonBinary", 0);
            }
        }

        // Aggregierte Summen in den Job-ExecutionContext schreiben,
        // damit der folgende summaryStep darauf zugreifen kann.
        var jobCtx = masterStepExecution.getJobExecution().getExecutionContext();
        jobCtx.putInt("total.male",      totalMale);
        jobCtx.putInt("total.female",    totalFemale);
        jobCtx.putInt("total.nonBinary", totalNonBinary);

        return masterStepExecution.getExitStatus(); // ExitStatus unveraendert weitergeben
    }
}
