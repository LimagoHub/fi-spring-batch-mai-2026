package de.fi.fifth.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.*;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.List;

@Configuration
public class SplitFlowJobConfiguration {

    /**
     * Tasklet: Zeigt Text mehrmals im Kommandozeilenfenster.
     * Thread.sleep simuliert Verarbeitungszeit, damit die verschränkte Ausgabe
     * der parallelen Steps in der Konsole sichtbar wird.
     */
    public static class PrintTextMehrmalsTasklet implements Tasklet {
        final String text;
        final int anzahl;

        public PrintTextMehrmalsTasklet(String text, int anzahl) {
            this.text = text;
            this.anzahl = anzahl;
        }

        @Override
        public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
            for (int i = 0; i < anzahl; i++) {
                System.out.print(text);
                // Ohne Pause wäre die Ausgabe zu schnell — die Verschränkung der Threads
                // würde im Log nicht sichtbar, weil ein Thread vor dem nächsten Start fertig wäre
                Thread.sleep(200);
            }
            System.out.print(" ");
            return RepeatStatus.FINISHED;
        }
    }

    // Spring Batch 5: JobRepository und TransactionManager müssen explizit injiziert werden,
    // weil die Auto-Konfiguration über @EnableBatchProcessing nicht mehr implizit greift
    @Bean
    public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step1", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("1 ", 5), transactionManager)
                .build();
    }

    @Bean
    public Step step2(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step2", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("2 ", 5), transactionManager)
                .build();
    }

    @Bean
    public Step step3(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step3", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("3 ", 4), transactionManager)
                .build();
    }

    @Bean
    public Step step4(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step4", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("4 ", 6), transactionManager)
                .build();
    }

    @Bean
    public Step step5(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step5", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("5 ", 8), transactionManager)
                .build();
    }

    @Bean
    public Step step6(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step6", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("6 ", 5), transactionManager)
                .build();
    }

    @Bean
    public Step step7(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step7", jobRepository)
                .tasklet(new PrintTextMehrmalsTasklet("7 ", 5), transactionManager)
                .build();
    }

    @Bean
    public Job meinSplitFlowJob(JobRepository jobRepository,
                                Step step1, Step step2, Step step3,
                                Step step4, Step step5, Step step6, Step step7) {

        // Split arbeitet mit Flow-Objekten, nicht direkt mit Steps —
        // jeder parallele Ast muss in einen eigenen Flow verpackt werden
        Flow flow3 = new FlowBuilder<SimpleFlow>("flow3").from(step3).end();
        Flow flow4 = new FlowBuilder<SimpleFlow>("flow4").from(step4).end();
        Flow flow5 = new FlowBuilder<SimpleFlow>("flow5").from(step5).end();

        // flow3 ist der "Start"-Flow im Split, flow4 und flow5 werden parallel dazu gestartet.
        // Alle drei laufen gleichzeitig in eigenen Threads.
        // SimpleAsyncTaskExecutor: keine Thread-Pool-Begrenzung — für die Schulung genug,
        // in Produktion besser ThreadPoolTaskExecutor mit definiertem Maximum verwenden
        Flow splitFlow345 = new FlowBuilder<SimpleFlow>("splitFlow345")
                .start(flow3)
                .split(new SimpleAsyncTaskExecutor())
                .add(flow4, flow5)
                .build();

        // Gesamtablauf: step1 → step2 → [step3 || step4 || step5] → step6 → step7
        // Spring Batch wartet nach dem Split automatisch, bis alle parallelen Flows
        // abgeschlossen sind, bevor step6 startet (implizite Synchronisierungsbarriere)
        return new JobBuilder("meinSplitFlowJob", jobRepository)
                .incrementer(new RunIdIncrementer())

                .flow(step1)
                .next(step2)
                .next(splitFlow345)
                .next(step6)
                .next(step7)
                .end()
                .build();
    }
}