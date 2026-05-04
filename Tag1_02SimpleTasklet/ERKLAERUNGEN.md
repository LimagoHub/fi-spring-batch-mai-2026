# Tag1_02SimpleTasklet — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Dieses Projekt zeigt das **Tasklet-Modell** als Alternative zur Chunk-orientierten
Verarbeitung. Ausserdem werden zwei weitere Konzepte eingeführt:

1. **CONTINUABLE vs. FINISHED** — ein Tasklet, das sich selbst mehrfach wiederholt
2. **ExecutionContext** — Datenweitergabe zwischen Steps innerhalb eines Jobs

---

## Chunk vs. Tasklet — wann welches Modell?

| | Chunk-Modell | Tasklet-Modell |
|---|---|---|
| Typischer Einsatz | Grosse Datenmengen lesen/transformieren/schreiben | Einmalige Aktionen (Datei kopieren, Verzeichnis anlegen, HTTP-Aufruf) |
| Spring-Infrastruktur | Reader, Processor, Writer, Cursor | Nur `execute()`-Methode |
| Transaktionen | Pro Chunk eine Transaktion | Pro Ausführung eine Transaktion |
| Wiederholung | Implizit (Spring liest bis EOF) | Explizit per `CONTINUABLE` |

Ein Tasklet ist im Grunde ein Lambda: Spring Batch ruft `execute()` auf, wartet auf
`RepeatStatus.FINISHED` und geht dann zum nächsten Step. Gibt `execute()` stattdessen
`CONTINUABLE` zurück, wird es **sofort in einer neuen Transaktion erneut aufgerufen**.

---

## TaskletJobConfiguration.java — die drei Steps im Detail

### Job-Ablauf

```
meinTaskletJob
    │
    ├─► meinLeerzeilenStep   (gibt eine Leerzeile aus, FINISHED)
    │
    ├─► meinArbeitsStep      (wiederholt sich N-mal per CONTINUABLE)
    │
    └─► meinFinishStep       (liest Ergebnis aus ExecutionContext, FINISHED)
```

### meinLeerzeilenStep — einfachstes Tasklet-Beispiel

Nur eine Leerzeile auf der Konsole, dann `FINISHED`. Zeigt, dass ein Tasklet
keine Verarbeitungslogik braucht — er kann reine Infrastrukturaufgaben erledigen
(z. B. Verzeichnis anlegen, Lock-Datei setzen).

### meinArbeitsStep — Wiederholung per CONTINUABLE

```
commitCount = 0  →  CONTINUABLE  (nächste Runde)
commitCount = 1  →  CONTINUABLE
commitCount = 2  →  CONTINUABLE
...
commitCount = N-1 →  FINISHED
```

`commitCount` ist der eingebaute Zähler von Spring Batch für abgeschlossene
Transaktionen innerhalb eines Steps. Er steigt bei jedem `FINISHED`- oder
`CONTINUABLE`-Durchlauf. Man braucht keine eigene Instanzvariable als Zähler —
Spring Batch hält den Stand persistent im `JobRepository`.

**Warum `commitCount < anzahlDerDurchzufuerendenSteps - 1`?**
Beim letzten Durchlauf (commitCount = N-1) soll `FINISHED` zurückgegeben werden.
Der `-1` Offset liegt daran, dass beim allerletzten Aufruf `commitCount` noch
den Wert der vorigen abgeschlossenen Runde zeigt, bevor er inkrementiert wird.

### Datenweitergabe über den ExecutionContext

```
meinArbeitsStep
    └─► jobExecution.getExecutionContext().put("myKey", N)

meinFinishStep
    └─► jobExecution.getExecutionContext().get("myKey")  →  gibt N aus
```

Es gibt zwei ExecutionContexts:

| | StepExecutionContext | JobExecutionContext |
|---|---|---|
| Sichtbarkeit | Nur im aktuellen Step | Im gesamten Job |
| Persistiert | Ja (für Restart) | Ja |
| Datenweitergabe zwischen Steps | Nein | Ja |

Deshalb wird `jobExecution.getExecutionContext()` und nicht
`stepExecution.getExecutionContext()` verwendet — nur der Job-Context ist in
`meinFinishStep` sichtbar.

### ANZAHLSTEPS_KEY als Konstante

Der Schlüsselname `"AnzahlSteps"` ist als `public static final String` in
`TaskletJobConfiguration` definiert. Sowohl `meinArbeitsStep` (lesen) als auch
`AppRunner` (schreiben beim Job-Start) benutzen dieselbe Konstante — so werden
Tippfehler zur Compile-Zeit erkannt statt erst zur Laufzeit.

---

## AppRunner.java — normaler Job-Start

```java
jobLauncher.run(job, new JobParametersBuilder()
    .addString("AnzahlSteps", "7")
    .toJobParameters());
```

`JobParametersBuilder` kennt keinen `int`-Typ — alle Werte werden als String,
Long, Double oder Date übergeben. Die Konvertierung (`Integer.parseInt()`) übernimmt
`meinArbeitsStep` selbst.

Nach dem Job-Start wertet der AppRunner die `StepExecutions` aus:
`CommitCount` pro Step zeigt, wie oft jedes Tasklet ausgeführt wurde.

---

## AppRunner2.java — Fehlerfall demonstrieren

AppRunner2 ist mit `// @Component` auskommentiert, damit er nicht zusammen mit
`AppRunner` läuft. Er dient als Lern-Demo für zwei Szenarien:

### Szenario 1: ungültiger Parameter

```java
.addString("AnzahlSteps", "xx")
```

`Integer.parseInt("xx")` wirft eine `NumberFormatException`. Spring Batch fängt
diese im Tasklet, markiert den Step als `FAILED` und den Job als `FAILED`.
Die Anwendung läuft weiter — keine unkontrollierte Exception auf der Konsole,
kein unkontrollierter Abbruch. Das zeigt, dass Spring Batch alle Exceptions im
Step-Kontext abfängt und im `JobRepository` protokolliert.

### Szenario 2: asynchroner Job-Start (Referenzcode)

```java
TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
```

Dieser lokal erstellte `launcher` wird im Code **nicht verwendet** (es wird der
injizierte `jobLauncher` aufgerufen). Der Block dient als auskommentiertes
Beispiel: ein `TaskExecutorJobLauncher` mit `SimpleAsyncTaskExecutor` würde den
Job asynchron starten — `jobLauncher.run()` kehrt sofort zurück, der Job läuft
im Hintergrund. Das ist nützlich, wenn mehrere Jobs parallel gestartet werden sollen.

---

## application.properties

Identisch mit Tag1_01. Wichtig:

```properties
spring.batch.job.enabled=false
```

Ohne diese Einstellung würde Spring Boot beim Start automatisch **alle** registrierten
Jobs ausführen. Da AppRunner und AppRunner2 jeweils eigene Starts mit eigenen
Parametern durchführen, ist die automatische Ausführung deaktiviert.

---

## Was im Log zu sehen ist (AppRunner, anzahlSteps=7)

```
Joblauf mit Job-Parameter anzahlSteps=7:

 (Leerzeile von meinLeerzeilenStep)

Hallo Spring Batch mit Tasklet, Tasklet-Execution 0
Hallo Spring Batch mit Tasklet, Tasklet-Execution 1
Hallo Spring Batch mit Tasklet, Tasklet-Execution 2
Hallo Spring Batch mit Tasklet, Tasklet-Execution 3
Hallo Spring Batch mit Tasklet, Tasklet-Execution 4
Hallo Spring Batch mit Tasklet, Tasklet-Execution 5
Hallo Spring Batch mit Tasklet, Tasklet-Execution 6
7

StepExecution 1: StepName = meinLeerzeilenStep, CommitCount = 1
StepExecution 2: StepName = meinArbeitsStep,    CommitCount = 7
StepExecution 3: StepName = meinFinishStep,     CommitCount = 1
```

`meinArbeitsStep` zeigt `CommitCount = 7` — das Tasklet wurde 7-mal ausgeführt,
jedes Mal mit einer eigenen Transaktion. `meinFinishStep` gibt `7` aus (den im
`JobExecutionContext` gespeicherten Wert). `meinLeerzeilenStep` hat `CommitCount = 1`,
weil er nur einmal läuft.

---

## Erweiterungsmöglichkeiten

| Idee | Mechanismus |
|---|---|
| Tasklet als eigene Klasse (testbar) | Interface `Tasklet` implementieren, als `@Bean` registrieren |
| Fehlerbehandlung im Tasklet | Exception werfen → Step FAILED; oder per `contribution.setExitStatus()` feingranular |
| Tasklet mit Retry | `.tasklet(...).faultTolerant().retry(...)` — auch Tasklets können retry-fähig sein |
| Tasklet nur bei Bedingung ausführen | `JobExecutionDecider` implementieren und in den Flow einbauen |
| Tasklet-Ergebnis als Job-Exit-Code | `contribution.setExitStatus(new ExitStatus("CUSTOM"))` |
