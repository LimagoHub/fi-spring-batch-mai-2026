package de.limago.uebung.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class GenderSummaryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(GenderSummaryTasklet.class);

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // JobExecutionContext lesen – dort hat GenderAggregationListener die Summen abgelegt
        var jobCtx = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        int male      = jobCtx.getInt("total.male",      0);
        int female    = jobCtx.getInt("total.female",    0);
        int nonBinary = jobCtx.getInt("total.nonBinary", 0);
        int total     = male + female + nonBinary;

        log.info("╔══════════════════════════╗");
        log.info("║      Gender Summary      ║");
        log.info("╠══════════════════════════╣");
        log.info(String.format("║  Male:       %8d    ║", male));
        log.info(String.format("║  Female:     %8d    ║", female));
        log.info(String.format("║  Non-Binary: %8d    ║", nonBinary));
        log.info("╠══════════════════════════╣");
        log.info(String.format("║  Total:      %8d    ║", total));
        log.info("╚══════════════════════════╝");

        return RepeatStatus.FINISHED;
    }
}
