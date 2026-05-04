package de.fi.second;



import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration

public class TaskletJobConfiguration {
    // Konstante für den Job-Parameter-Schlüssel: vermeidet Tippfehler beim Lesen
    // in AppRunner und beim Schreiben in meinArbeitsStep.
    public static final String ANZAHLSTEPS_KEY = "AnzahlSteps";

   private final JobRepository repository;
   private final PlatformTransactionManager transactionManager;

    public TaskletJobConfiguration(final JobRepository repository, final PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Step meinLeerzeilenStep()
    {
        // Tasklet mit Lambda: die kürzeste Form eines Steps ohne Chunk-Verarbeitung.
        // RepeatStatus.FINISHED signalisiert Spring Batch, dass der Step abgeschlossen ist
        // und der Job zum nächsten Step weitergehen soll.
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet((contribution, chunkContext) -> {
            System.out.println( "" );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }

    @Bean
    public Step meinFinishStep(JobRepository repository, PlatformTransactionManager transactionManager)
    {
        return new StepBuilder("meinFinishStep", repository).tasklet((contribution, chunkContext) -> {
            // JobExecutionContext ist der gemeinsame Speicher aller Steps eines Jobs.
            // Hier wird ein Wert ausgelesen, der in meinArbeitsStep abgelegt wurde —
            // so können Steps Daten aneinander weitergeben.
            System.out.println(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().get("myKey") );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }



    @Bean
    public Step meinArbeitsStep()
    {
        return new StepBuilder("meinArbeitsStep", repository).tasklet((contribution, chunkContext) -> {

            // Job-Parameter werden beim Start übergeben und sind unveränderlich.
            // Hier wird die gewünschte Anzahl an Wiederholungen als String-Parameter eingelesen.
            String anz = chunkContext.getStepContext().getStepExecution().getJobParameters().getString( ANZAHLSTEPS_KEY );

            // Fallback auf 4, falls der Parameter nicht angegeben wurde — macht das Beispiel
            // auch ohne expliziten Parameter lauffähig.
            int anzahlDerDurchzufuerendenSteps = ( anz != null ) ? Integer.parseInt( anz ) : 4;

            // commitCount zählt, wie oft das Tasklet bereits abgeschlossen und die
            // Transaktion committed wurde — d. h. wie viele Wiederholungen schon gelaufen sind.
            long commitCount = chunkContext.getStepContext().getStepExecution().getCommitCount();

            // Ergebnis in den JobExecutionContext schreiben, damit meinFinishStep
            // es lesen kann. Der StepExecutionContext wäre hier nicht geeignet,
            // weil er nur innerhalb desselben Steps sichtbar ist.
            chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("myKey", anzahlDerDurchzufuerendenSteps);

            System.out.println( "Hallo Spring Batch mit Tasklet, Tasklet-Execution " + commitCount );

            // CONTINUABLE: Spring Batch führt das Tasklet sofort erneut aus (in einer neuen Transaktion).
            // FINISHED: der Step ist abgeschlossen, der Job geht weiter.
            // So kann ein einzelner Step mehrfach iterieren, ohne einen neuen Step zu definieren.
            return ( commitCount < anzahlDerDurchzufuerendenSteps - 1 ) ? RepeatStatus.CONTINUABLE : RepeatStatus.FINISHED;
        },transactionManager).build();
    }

    @Bean
    public Job meinTaskletJob(final JobRepository repository, final PlatformTransactionManager transactionManager) throws Exception
    {
        return new JobBuilder("meinTaskletJob", repository).incrementer( new RunIdIncrementer() )
                .start( meinLeerzeilenStep())
                .next(  meinArbeitsStep() )
                .next(  meinFinishStep(repository, transactionManager) )

                .build();
    }
}
