package de.fi.tag2_03conditional_flow_verzweigung.springbatchdemo;


import org.springframework.batch.core.*;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;

import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
// @EnableBatchProcessing fehlt hier bewusst: Spring Boot 3 / Spring Batch 5 konfiguriert
// JobRepository, JobLauncher und TransactionManager automatisch. @EnableBatchProcessing
// würde diese Auto-Konfiguration deaktivieren und erfordert manuelle Setup-Arbeit.
public class ConditionalFlowJobConfiguration {

    // Konstante statt magischem String: Tippfehler beim Parameter-Namen fallen sofort
    // im Compiler auf, nicht erst zur Laufzeit bei falschem Routing
    public static final String OK_ODER_FEHLER = "OK_ODER_FEHLER";


    /*
    // --- ALTERNATIVE: DECIDER-VARIANTE (auskommentiert zum Vergleich) ---
    // JobExecutionDecider ermöglicht beliebige Entscheidungslogik außerhalb des ExitStatus.
    // Hier wird stattdessen der ExitStatus des Steps direkt für die Verzweigung genutzt.
    public static class MeinEntscheider implements JobExecutionDecider {
        @Override
        public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
            String parameter = (String) jobExecution.getJobParameters().getParameters().get(OK_ODER_FEHLER).getValue();

            if ("ok".equalsIgnoreCase(parameter)) {
//                return new FlowExecutionStatus("GEHE_ZU_OK");
            } else {
                return new FlowExecutionStatus("GEHE_ZU_FEHLER");
            }
        }
    }

    @Bean
    public JobExecutionDecider decider() {
        return new MeinEntscheider();
    }

    // --- JOB MIT DECIDER ---
    @Bean
    public Job meinConditionalFlowJobAbc(JobRepository jobRepository,
                                         Step arbeitsStep,
                                         JobExecutionDecider decider,
                                         Step fehlerbehandlungsStep,
                                         Step okStep,
                                         Step abschliessenderStep) {
        return new JobBuilder("jobAbc", jobRepository)
                .start(arbeitsStep)
                .next(decider) // Nach dem Arbeitsstep kommt der Entscheider
                .on("GEHE_ZU_OK").to(okStep)
                .from(decider).on("GEHE_ZU_FEHLER").to(fehlerbehandlungsStep)
                .from(okStep).next(abschliessenderStep)
                .from(fehlerbehandlungsStep).next(abschliessenderStep)
                .end()
                .build();
    }
}

     */


    /**
     * Einfacher Tasklet: gibt Text auf der Konsole aus.
     * Als Basisklasse wiederverwendbar, um Vererbung im Tasklet-Kontext zu zeigen.
     */
    public static class PrintTextTasklet implements Tasklet {
        final String text;

        public PrintTextTasklet(String text) {
            this.text = text;
        }

        @Override
        public RepeatStatus execute(StepContribution sc, org.springframework.batch.core.scope.context.ChunkContext cc) throws Exception {
            System.out.println(text);
            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Simuliert einen echten Geschäftsprozess: schlägt fehl oder läuft durch,
     * gesteuert per JobParameter. Kein Parameter → zufälliges Ergebnis (Demo-Modus).
     */
    public static class ArbeitsTasklet extends PrintTextTasklet {
        public ArbeitsTasklet(String text) {
            super(text);
        }

        @Override
        public RepeatStatus execute(StepContribution sc, org.springframework.batch.core.scope.context.ChunkContext cc) throws Exception {
            var stepContext = cc.getStepContext();
            System.out.println("\n---- Job: " + stepContext.getJobName() + ", mit JobParametern: " + stepContext.getJobParameters());

            String okOderFehler = (String) stepContext.getJobParameters().get(OK_ODER_FEHLER);

            // Zufallsmodus ohne Parameter: macht beide Codepfade ohne Befehlszeilen-Argument
            // direkt in der IDE sichtbar — nur für Demo-Zwecke, nie in Produktion verwenden
            if ((okOderFehler != null) ? !okOderFehler.equalsIgnoreCase("ok") : (Math.random() < 0.5)) {
                System.out.println(this.text + ": mit Fehler");
                // Exception löst ExitStatus FAILED aus → Spring Batch nimmt den FAILED-Ast
                // im Conditional Flow, ohne den Job direkt abzubrechen
                throw new Exception("-- Dieser Fehler ist korrekt! --");
            }
            System.out.println(this.text + ": ok");
            return RepeatStatus.FINISHED;
        }
    }

    @Bean
    public Step arbeitsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("arbeitsStep", jobRepository)
                .tasklet(new ArbeitsTasklet("ArbeitsStep"), transactionManager)
                .build();
    }

    @Bean
    public Step fehlerbehandlungsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("fehlerbehandlungsStep", jobRepository)
                .tasklet(new PrintTextTasklet("FehlerbehandlungsStep"), transactionManager)
                .build();
    }

    @Bean
    public Step okStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("okStep", jobRepository)
                .tasklet(new PrintTextTasklet("OkStep"), transactionManager)
                .build();
    }

    @Bean
    public Step abschliessenderStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("abschliessenderStep", jobRepository)
                .tasklet(new PrintTextTasklet("AbschliessenderStep"), transactionManager)
                .build();
    }

    /**
     * Job mit Conditional Flow: der ExitStatus des arbeitsStep bestimmt den weiteren Pfad.
     * FAILED → fehlerbehandlungsStep → abschliessenderStep
     * sonst  → okStep               → abschliessenderStep
     */
    @Bean
    public Job meinConditionalFlowJobAbc(JobRepository jobRepository,
                                         Step arbeitsStep,
                                         Step fehlerbehandlungsStep,
                                         Step okStep,
                                         Step abschliessenderStep) {
        return new JobBuilder("jobAbc", jobRepository)
                .incrementer(new RunIdIncrementer())
                .flow(arbeitsStep)
                // FAILED explizit zuerst: spezifischere Muster müssen vor dem Wildcard stehen,
                // sonst würde "*" auch FAILED treffen und die Fehlerbehandlung nie aufgerufen
                .on(ExitStatus.FAILED.getExitCode()).to(fehlerbehandlungsStep).next(abschliessenderStep)
                // "*" als Fallback: trifft COMPLETED, STOPPED und alle unbekannten ExitStatus
                .from(arbeitsStep).on("*").to(okStep).next(abschliessenderStep)
                .end()
                .build();
    }
}