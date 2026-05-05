# Tag2_06Partitionierer — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Während Projekt 07 eine CSV-Datei erst manuell aufteilt und dann parallel liest,
zeigt **Projekt 06 den Spring-Batch-nativen Weg**: die Quelldaten liegen bereits
in einer Datenbanktabelle. Ein `ColumnRangePartitioner` teilt den ID-Bereich
rechnerisch auf, ohne die Daten zu kopieren — jeder Worker-Thread liest dann
seinen Teilbereich direkt aus der DB per `JdbcPagingItemReader`.

Zusätzlich wird eine **Processor-Pipeline** (`CompositeItemProcessor`) und das
Schreiben in eine separate Zieltabelle per `RepositoryItemWriter` gezeigt.

---

## Gesamtablauf

```
Tabelle tbl_personen (1 000 Zeilen, id 1–1 000)
         │
         ▼
ColumnRangePartitioner.partition(gridSize=4)
  → SELECT MIN(id), MAX(id)  →  1 und 1 000
  → targetSize = (1000 - 1) / 4 + 1 = 250
         │
         ├─► partition0  { minValue:   1, maxValue: 250 }
         ├─► partition1  { minValue: 251, maxValue: 500 }
         ├─► partition2  { minValue: 501, maxValue: 750 }
         └─► partition3  { minValue: 751, maxValue: 1000 }
                 │
                 ▼ (4 parallele Worker-Threads)
    JdbcPagingItemReader   →   MyCompositeProcessor   →   RepositoryItemWriter
    WHERE id >= min            Person → UPPERCASE          StudentEntity in
    AND   id <= max            Person → StudentEntity      tbl_studenten speichern
```

---

## ColumnRangePartitioner

### Was er macht
Implementiert das `Partitioner`-Interface von Spring Batch. Die einzige Methode
`partition(int gridSize)` gibt eine `Map<String, ExecutionContext>` zurück —
ein Eintrag pro Worker-Thread.

```java
// Jeder Eintrag trägt minValue und maxValue für seinen Thread:
context.putInt("minValue", start);
context.putInt("maxValue", end);
```

### Warum MIN/MAX zur Laufzeit abgefragt werden
Die Grenzen werden per JDBC live aus der Tabelle gelesen, nicht hartcodiert.
Das hält den Partitioner universell einsetzbar: er funktioniert auch dann
korrekt, wenn die Tabelle zwischen zwei Job-Läufen gewachsen ist.

### Der +1-Trick bei der Bereichsberechnung
```java
int targetSize = (max - min) / gridSize + 1;
```
Ganzzahldivision schneidet ab. Ohne `+1` würde der letzte Datensatz aus dem
letzten Partition-Segment herausfallen (Off-by-One). Das `if (end > max) end = max`
am Ende korrigiert eine mögliche Überschreitung nach oben.

### Wann dieser Ansatz nicht optimal ist
Der ID-Range-Ansatz setzt voraus, dass IDs einigermaßen gleichmäßig verteilt
sind. Bei vielen Lücken (viele gelöschte Zeilen) kann ein Worker deutlich
weniger Arbeit bekommen als ein anderer. Alternativen:

| Ansatz | Wann sinnvoll |
|---|---|
| ID-Range (`ColumnRangePartitioner`) | Gleichmäßige ID-Verteilung |
| Hash-Partitionierung `MOD(id, gridSize)` | Lücken in ID-Sequenz |
| Status- / Datum-Partitionierung | Täglich neue/geänderte Zeilen |
| `MultiResourcePartitioner` | Quelldateien liegen bereits aufgeteilt vor |
| Remote Partitioning | Millionen Zeilen, mehrere Maschinen nötig |

---

## JdbcPagingItemReader mit @StepScope

### Warum @StepScope zwingend ist
Ohne `@StepScope` versucht Spring beim Hochfahren der Anwendung, den Reader
zu erzeugen. Zu diesem Zeitpunkt existiert kein `ExecutionContext` — die
SpEL-Ausdrücke `#{stepExecutionContext['minValue']}` können nicht aufgelöst
werden, und die Anwendung startet nicht.

Mit `@StepScope` wird eine **neue Bean-Instanz** erst dann erzeugt, wenn der
jeweilige Worker-Step startet. Dann hat Spring Batch den `ExecutionContext`
der Partition bereits befüllt.

### Warum JdbcPagingItemReader statt JdbcCursorItemReader
Der `JdbcCursorItemReader` hält eine einzige DB-Verbindung für den gesamten
Step offen. Bei langen Läufen drohen Connection-Timeouts und Transaktionsprobleme.
Der `JdbcPagingItemReader` paginiert: er führt für je `fetchSize` Zeilen eine
eigene Abfrage aus, gibt die Verbindung dazwischen zurück an den Pool und
unterstützt sauberes Restart bei Fehler.

```java
// Die WHERE-Klausel begrenzt jede Partition auf ihren ID-Bereich:
queryProvider.setWhereClause("id >= " + minValue + " AND id <= " + maxValue);
// sortKey ist Pflicht für Paging — ohne deterministischen Sort würden
// Seiten überlappen oder Zeilen doppelt gelesen werden.
queryProvider.setSortKeys(Map.of("id", Order.ASCENDING));
```

### Auskommentierte FlatFileItemWriter-Variante
Die `BatchConfig` enthält eine auskommentierte `FlatFileItemWriter`-Bean.
Sie zeigt den ursprünglichen Ansatz: partitionierte CSV-Ausgabe je Worker.
Dieser wurde durch den `RepositoryItemWriter` ersetzt, weil die Daten direkt
in die DB persistiert werden sollen — ein anschließender `FileMergingTasklet`
würde dann entfallen.

---

## MyCompositeProcessor — Processor-Pipeline

### Prinzip
`CompositeItemProcessor` leitet die Ausgabe jedes Delegates als Eingabe an
den nächsten weiter. Hier wird eine zweigliedrige Pipeline aufgebaut:

```
Person (Originalschreibweise)
    │
    ▼  PersonItemToUpperProcessor
Person (GROSSBUCHSTABEN)
    │
    ▼  PersonToStudentProcessor
StudentEntity (mit UUID, bereit für JPA)
```

### Warum die Reihenfolge der Delegates kritisch ist
`setDelegates(List.of(personItemToUpperProcessor, personToStudentProcessor))`

Werden die Delegates vertauscht, würde `PersonItemToUpperProcessor` eine
`StudentEntity` als Eingabe erhalten, aber er erwartet `Person` — Laufzeitfehler.
Der Typ-Fluss muss in der Liste von links nach rechts passen.

### Warum kein @StepScope auf den Processors
Beide Processor-Klassen sind **zustandslos** — sie halten keine Instanzvariablen,
die sich zwischen Verarbeitungsaufrufen ändern. Zustandslose Beans können
problemlos von mehreren parallelen Threads geteilt werden.

| Komponente | Zustand | @StepScope nötig? |
|---|---|---|
| `JdbcPagingItemReader` | ja — aktuelle Seite, Cursor | ja |
| `RepositoryItemWriter` | ja — EntityManager-Instanz | ja |
| `PersonItemToUpperProcessor` | nein — reine Funktion | nein |
| `PersonToStudentProcessor` | nein — reine Funktion | nein |

---

## FileMergingTasklet — historisches Artefakt

Der `FileMergingTasklet` existiert noch im Code, wird aber **nicht mehr
im Job-Flow verwendet** (`mergeStep` ist in `partitionJob` auskommentiert):

```java
// mergeStep ist auskommentiert, weil der Writer auf DB umgestellt wurde
.start(masterStep)
//.next(mergeStep)
```

Er zeigt dennoch ein wichtiges Muster: wenn Worker partitionierte Ausgabedateien
erzeugen (statt in DB zu schreiben), müssen diese nach der parallelen Phase
zusammengeführt werden. Der Tasklet:
- erzeugt die Zieldatei idempotent neu (löscht vorherige Version)
- sortiert die Quelldateien alphabetisch für deterministische Reihenfolge
- hängt den Inhalt jeder Partition-Datei sequentiell an

---

## Master/Worker-Prinzip in der BatchConfig

```
Job: partitionJob
 │
 └─► masterStep  (Orchestrierung)
      ├─► ColumnRangePartitioner.partition(4)  →  4 ExecutionContexts
      ├─► SimpleAsyncTaskExecutor              →  1 Thread je Partition
      └─► workerStep wird 4x gestartet, je in eigenem Thread:
           ├─► Thread 1: liest id   1–250  → Uppercase → StudentEntity → H2
           ├─► Thread 2: liest id 251–500  → Uppercase → StudentEntity → H2
           ├─► Thread 3: liest id 501–750  → Uppercase → StudentEntity → H2
           └─► Thread 4: liest id 751–1000 → Uppercase → StudentEntity → H2
```

Der `masterStep` wartet, bis alle Worker-Threads abgeschlossen sind.
Der `workerStep` selbst weiß nichts von Partitionierung — er ist eine
gewöhnliche Chunk-Step-Definition. Die Parallelität wird ausschließlich
durch `.partitioner(...).step(workerStep).taskExecutor(...)` konfiguriert.

### gridSize und SimpleAsyncTaskExecutor
`gridSize(4)` legt fest, in wie viele Partitionen der ID-Bereich aufgeteilt wird.
`SimpleAsyncTaskExecutor` startet für jede Partition einen eigenen Thread
ohne Pool-Limit. Für Produktionsumgebungen ist `ThreadPoolTaskExecutor`
vorzuziehen:

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(4);
executor.setMaxPoolSize(4);
executor.afterPropertiesSet();
```

### reader(ds, null, null) in der workerStep-Definition
```java
.reader(reader(ds, null, null))
```
Die `null`-Werte sind Platzhalter für die Bean-Registrierung bei Spring.
Zur Laufzeit ersetzt Spring Batch sie durch die echten `minValue`/`maxValue`
aus dem `ExecutionContext` der jeweiligen Partition — dank `@StepScope`.

---

## Datenbankschema und Testdaten

### Quelltabelle (data.sql)
`tbl_personen` wird per `data.sql` mit **1 000 Zeilen** (id 1–1 000) gefüllt.
`ddl-auto=update` lässt Hibernate die `tbl_studenten`-Tabelle automatisch anlegen.

### Zieltabelle (StudentEntity → tbl_studenten)
UUID als Primärschlüssel statt Auto-Increment-Integer: Bei paralleler Einfügung
aus vier Threads wäre eine DB-seitige Sequenz ein Engpass. `UUID.randomUUID()`
wird im Processor generiert — keine Koordination zwischen Threads nötig.

### H2 als In-Process-Datenbank
Die Datei-basierte H2-DB (`jdbc:h2:file:c:/tmp/db/fi_part_batch`) bleibt zwischen
Starts erhalten. `AUTO_SERVER=TRUE` erlaubt parallelen Zugriff von mehreren
Verbindungen (wichtig für den gleichzeitigen Lesezugriff der Worker-Threads).
Die H2-Console (`/h2`) ermöglicht es, Ergebnisse direkt im Browser zu prüfen.

---

## Zeitstempel als JobParameter

```java
new JobParametersBuilder()
    .addLong("zeitpunkt", System.currentTimeMillis())
    .toJobParameters();
```

Spring Batch lehnt einen erneuten Start ab, wenn ein Job mit identischen
Parametern bereits erfolgreich gelaufen ist. Der Zeitstempel macht jeden
Start eindeutig — der Job kann so oft wie gewünscht ausgeführt werden.

---

## Was im Log zu sehen ist

```
INFO  PartitionJobRunner   - --- Starte Partitionierungs-Job ---
INFO  Spring Batch         - Executing step: [masterStep]
INFO  PersonItemToUpperProcessor - Converting (Person[id=1, ...]) into (Person[id=1, TABBITHA, POLLAK])  ← Thread 1
INFO  PersonItemToUpperProcessor - Converting (Person[id=251, ...]) into (...)                           ← Thread 2  (parallel!)
INFO  PersonItemToUpperProcessor - Converting (Person[id=501, ...]) into (...)                           ← Thread 3  (parallel!)
INFO  PersonItemToUpperProcessor - Converting (Person[id=751, ...]) into (...)                           ← Thread 4  (parallel!)
...
INFO  PartitionJobRunner   - --- Job beendet mit Status: COMPLETED ---
```

Die Log-Zeilen aus den vier Threads erscheinen durcheinander — das ist gewollt
und zeigt, dass die Partitionen wirklich gleichzeitig verarbeitet werden.
