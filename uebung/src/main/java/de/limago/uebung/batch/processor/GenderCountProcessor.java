package de.limago.uebung.batch.processor;

import de.limago.uebung.entity.Human;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// @StepScope ist zwingend, weil:
// 1. Jede Partition hat ihre eigene StepExecution – nur so erhaelt jede Partition
//    ihren eigenen Zaehler-Stand, ohne Konflikte mit anderen Partitionen.
// 2. Die StepExecution kann erst zur Laufzeit des Steps per SpEL injiziert werden.
//    Spring erzeugt deshalb einen Proxy, der beim ersten Zugriff aufgeloest wird.
@Component
@StepScope
public class GenderCountProcessor implements ItemProcessor<Human, Human> {

    private final StepExecution stepExecution;

    private int maleCount = 0;
    private int femaleCount = 0;
    private int nonBinaryCount = 0;

    public GenderCountProcessor(@Value("#{stepExecution}") StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public Human process(Human human) {
        switch (human.gender().toLowerCase()) {
            case "male"   -> maleCount++;
            case "female" -> femaleCount++;
            default       -> nonBinaryCount++;  // Bigender, Agender, Polygender, Non-binary usw.
        }

        // Werte nach jedem Item in den ExecutionContext schreiben.
        // Spring Batch persistiert den Context am Ende jedes Chunks in BATCH_STEP_EXECUTION_CONTEXT,
        // sodass bei einem Neustart (Restart) der richtige Zaehlerstand wiederhergestellt werden kann.
        ExecutionContext ctx = stepExecution.getExecutionContext();
        ctx.putInt("count.male", maleCount);
        ctx.putInt("count.female", femaleCount);
        ctx.putInt("count.nonBinary", nonBinaryCount);

        return human;
    }
}
