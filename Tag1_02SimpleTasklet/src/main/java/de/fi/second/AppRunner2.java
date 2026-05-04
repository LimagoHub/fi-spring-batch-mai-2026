package de.fi.second;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/*

@Autowired
private JobExplorer jobExplorer;

public boolean isJobRunning(String jobName) {
    // Holen Sie alle laufenden Job-Instanzen für einen bestimmten Job
    List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, Integer.MAX_VALUE);

    for (JobInstance instance : instances) {
        // Holen Sie alle Ausführungen für jede Instanz
        List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(instance);
        for (JobExecution jobExecution : jobExecutions) {
            // Überprüfen Sie, ob der Status der Ausführung anzeigt, dass der Job noch läuft
            if (jobExecution.isRunning() || jobExecution.getStatus() == BatchStatus.STARTED || jobExecution.getStatus() == BatchStatus.STARTING) {
                return true;
            }
        }
    }
    return false;
}
 */


// @Component ist auskommentiert, damit AppRunner2 nicht zusammen mit AppRunner
// ausgeführt wird — beide implementieren CommandLineRunner und würden sonst gleichzeitig
// starten. Zur Demo den Kommentar aktivieren und bei AppRunner entfernen.
//@Component

public class AppRunner2 implements CommandLineRunner {
    public AppRunner2(final JobLauncher jobLauncher, final Job job) {
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    private final JobLauncher jobLauncher;
    private final Job job;

    @Override
    public void run(final String... args) throws Exception {

        // TaskExecutorJobLauncher mit SimpleAsyncTaskExecutor: der Job würde asynchron
        // gestartet — jobLauncher.run() kehrt sofort zurück, bevor der Job fertig ist.
        // Hier wird der lokal erstellte launcher jedoch nicht verwendet (jobLauncher
        // wird injiziert); dieser Block dient als Referenz für asynchronen Start.
        // Für Produktion: ThreadPoolTaskExecutor bevorzugen (begrenzte Thread-Anzahl).
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());


        System.out.println( "\nJoblauf mit fehlerhaftem Job-Parameter (Text statt Zahl):" );
        // Absichtlich ungültiger Parameter "xx" statt einer Zahl — demonstriert,
        // wie Spring Batch mit NumberFormatException im Tasklet umgeht (Step FAILED,
        // Job FAILED, aber keine unkontrollierte Exception in der Anwendung).
        JobExecution je = jobLauncher.run( job, new JobParametersBuilder().addString(
                TaskletJobConfiguration.ANZAHLSTEPS_KEY, "xx" ).toJobParameters() );
        // ExitStatus und BatchStatus werden beide ausgegeben, weil sie sich unterscheiden können:
        // BatchStatus ist der interne Spring-Batch-Zustand, ExitStatus ist der nach aussen
        // sichtbare Rückgabewert, der z. B. von einem Scheduler ausgewertet wird.
       for( StepExecution se : je.getStepExecutions() ) {
            System.out.println( "StepExecution " + se.getId() + ": StepName = " + se.getStepName() +
                    ", CommitCount = " + se.getCommitCount() +
                    ", BatchStatus = " + se.getStatus() + ", ExitStatus = " + se.getExitStatus().getExitCode() );
        }
    }
}
