# Tag2_07Partitionierer — Erklärungen und Hintergründe

---

## SplitTasklet — CSV aufteilen

### Was macht er?
Der `SplitTasklet` ist der erste Step im Job. Er läuft **einmal, sequentiell**,
bevor die parallele Verarbeitung beginnt. Er liest `inbox/student.csv`, behält
die Header-Zeile und schreibt 4 Teilstücke nach `work/`:

```
inbox/student.csv  (1000 Zeilen)
        │
        ▼
work/chunk_1.csv  (Header + Zeilen   1–250)
work/chunk_2.csv  (Header + Zeilen 251–500)
work/chunk_3.csv  (Header + Zeilen 501–750)
work/chunk_4.csv  (Header + Zeilen 751–1000)
```

Der Header wird in jede Teilddatei kopiert, damit der `FlatFileItemReader`
im nächsten Step die Spaltennamen kennt.

### Warum überhaupt splitten?
Spring Batch liest eine einzelne CSV immer **sequentiell**. Um 4 Dateien
**gleichzeitig** in parallelen Threads zu verarbeiten, braucht jeder Thread
seine eigene Datei.

### Ist `Files.readAllLines()` performant?
- **Für 1 000 Zeilen (~50 KB): ja**, völlig ausreichend.
- **Für Millionen von Zeilen: nein** — die gesamte Datei landet auf einmal im Heap.

Die skalierbare Alternative ist zeilenweises Streaming mit `BufferedReader` + `BufferedWriter`:

```java
try (BufferedReader reader = Files.newBufferedReader(inboxFile)) {
    String header = reader.readLine();
    // Zeile für Zeile lesen und direkt schreiben — kein großer Zwischenspeicher
}
```

### Gibt es grundsätzlich bessere Ansätze?

| Ansatz | Wann sinnvoll |
|---|---|
| `BufferedReader` streaming | Große Dateien, bleibt in Java |
| OS-Tool (`split`, PowerShell) | Sehr große Dateien, kein JVM-Overhead |
| Gar nicht splitten | Wenn die Quelldateien bereits mehrere sind |
| DB-Ansatz (wie Projekt 06) | Spring-Batch-native, skaliert am besten |

### Spring Batch bringt das schon mit: `MultiResourcePartitioner`
Statt manuell zu splitten kann man den eingebauten `MultiResourcePartitioner`
verwenden — er nimmt ein Array von Dateipfaden und erzeugt automatisch eine
Partition pro Datei. Voraussetzung: die Quelldateien liegen bereits einzeln vor.

```java
// Kein eigener Partitioner nötig, wenn Dateien schon aufgeteilt sind:
MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
partitioner.setResources(new Resource[]{ file1, file2, file3, file4 });
```

Für das Schulungsbeispiel verwenden wir den manuellen `SplitTasklet`,
weil er den Mechanismus transparent macht.

---

## StudentPartitioner — Herzstück der Parallelverarbeitung

### Das Problem ohne Partitionierung
Spring Batch würde `chunk_1.csv` bis `chunk_4.csv` nacheinander lesen —
Datei für Datei, ein Thread. Das ist langsam.

### Was der Partitioner löst
Er teilt die Arbeit **auf**, bevor sie beginnt. Spring Batch fragt ihn:
*„Wie soll ich die Arbeit aufteilen?"* — und er antwortet mit einer Map:
**ein Eintrag pro Worker**.

```
partition1  →  ExecutionContext { inputFile: "work/chunk_1.csv" }
partition2  →  ExecutionContext { inputFile: "work/chunk_2.csv" }
partition3  →  ExecutionContext { inputFile: "work/chunk_3.csv" }
partition4  →  ExecutionContext { inputFile: "work/chunk_4.csv" }
```

Jeder Eintrag ist ein **`ExecutionContext`** — ein kleines Schlüssel-Wert-Paket,
das Spring Batch an den jeweiligen Worker-Thread weiterreicht.

### Ablauf im Überblick

```
MasterStep
    └─► StudentPartitioner.partition(gridSize=4)
            └─► gibt 4 ExecutionContexts zurück
                    │
                    ├─► Worker-Thread 1  liest chunk_1.csv
                    ├─► Worker-Thread 2  liest chunk_2.csv
                    ├─► Worker-Thread 3  liest chunk_3.csv
                    └─► Worker-Thread 4  liest chunk_4.csv
```

### Der Key muss exakt übereinstimmen
Der Schlüssel `"inputFile"` im `ExecutionContext` muss **identisch** mit dem
SpEL-Ausdruck im `FlatFileItemReader` sein:

```java
// Im Partitioner:
context.putString("inputFile", "work/chunk_1.csv");

// Im Reader (@StepScope):
@Value("#{stepExecutionContext['inputFile']}") String inputFile
```

Spring Batch injiziert den Wert erst zur Laufzeit des Steps — nicht beim Start
der Anwendung. Deshalb ist `@StepScope` am Reader zwingend.

---

## FlatFileItemReader mit @StepScope — und warum Option B (DTO + MapStruct)

### Student als DTO vs. direkt StudentEntity lesen

**Option A — direkt in StudentEntity lesen:**
Einfacher, weniger Klassen. Aber: die Entity trägt JPA-Annotationen und eine UUID als ID,
während die CSV eine Integer-ID hat. Der Reader müsste die UUID selbst erzeugen — das
gehört nicht in die Lese-Schicht.

**Option B — Student als DTO, MapStruct zum Mappen:**
Der Reader liest in ein reines Datentransport-Objekt (`Student` record). Der Processor
übernimmt die fachliche Transformation nach `StudentEntity`. Klare Trennung der Schichten:

```
CSV  →  Student (DTO)  →  StudentEntity (JPA)
         Reader              Processor (MapStruct)
```

### Warum @StepScope am Reader zwingend ist

Ohne `@StepScope` versucht Spring, die Bean beim Hochfahren der Anwendung zu erzeugen.
Zu diesem Zeitpunkt existiert kein `ExecutionContext` — der SpEL-Ausdruck
`#{stepExecutionContext['inputFile']}` kann nicht aufgelöst werden und die Anwendung
startet nicht.

Mit `@StepScope` wird die Bean erst instanziiert, wenn der jeweilige Worker-Step startet.
Dann hat Spring Batch den `ExecutionContext` der Partition bereits befüllt.

### MapStruct — wie es funktioniert

MapStruct ist ein **Annotation-Processor**: er liest das Interface zur Compile-Zeit
und generiert daraus eine fertige Implementierungsklasse (`StudentMapperImpl`).
Zur Laufzeit gibt es keinen Reflection-Overhead — es ist einfach generierter Java-Code.

```java
@Mapper(componentModel = "spring")
public interface StudentMapper {

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    StudentEntity toEntity(Student student);
}
```

- `componentModel = "spring"` → `StudentMapperImpl` wird als Spring-Bean registriert
  und kann per `@Autowired` / Konstruktor-Injection verwendet werden.
- `vorname` und `nachname` werden automatisch gemappt, weil die Feldnamen identisch sind.
- `id` wird explizit gesetzt: jede neue `StudentEntity` bekommt eine frische UUID —
  die Integer-ID aus der CSV hat in der Datenbank keine Bedeutung.

### pom.xml — was für MapStruct nötig ist

MapStruct braucht zwei Einträge:
1. **Dependency** (`mapstruct`) — die Annotationen und Interfaces
2. **Annotation Processor** (`mapstruct-processor`) im `maven-compiler-plugin` —
   er generiert die `*Impl`-Klasse beim Build

Ohne den Processor-Eintrag im Compiler-Plugin wird keine Implementierung generiert
und der Start schlägt mit `No qualifying bean` fehl.

---

## StudentItemProcessor — schlank durch MapStruct

Der Processor hat genau eine Aufgabe: den `StudentMapper` aufrufen.
Die gesamte Mapping-Logik (Feldnamen, UUID-Generierung) ist im Interface definiert —
der Processor selbst enthält keine fachliche Logik mehr.

```
Student (DTO)  →  [StudentItemProcessor]  →  StudentEntity
                        │
                        └─► studentMapper.toEntity(student)
                                  │
                                  └─► MapStruct-generierter Code
```

Das ist der Vorteil von MapStruct gegenüber manuellem Mapping im Processor:
Ändert sich die Entity (neues Feld, Umbenennung), ändert man nur das Mapper-Interface —
der Processor bleibt unberührt. Der Compiler weist sofort auf fehlende Mappings hin.

---

## BatchConfig — wie alles zusammenhängt

### Master/Worker-Prinzip

```
Job: partitionJob
 │
 ├─► splitStep       (SplitTasklet — sequentiell, 1x)
 │
 ├─► masterStep      (Orchestrierung der Parallelverarbeitung)
 │    ├─► StudentPartitioner.partition(4) → 4 ExecutionContexts
 │    └─► workerStep wird 4x gestartet, je in eigenem Thread:
 │         ├─► Thread 1: liest chunk_1.csv → mappt → schreibt in H2
 │         ├─► Thread 2: liest chunk_2.csv → mappt → schreibt in H2
 │         ├─► Thread 3: liest chunk_3.csv → mappt → schreibt in H2
 │         └─► Thread 4: liest chunk_4.csv → mappt → schreibt in H2
 │
 └─► cleanupStep     (CleanupTasklet — sequentiell, nur bei Erfolg)
```

Der `masterStep` wartet, bis alle Worker-Threads beendet sind, bevor der Job
mit `cleanupStep` fortfährt.

### workerStep weiß nichts von Partitionierung
Der `workerStep` ist eine ganz normale Chunk-Step-Definition. Er weiß nicht,
dass er parallel läuft. Die Parallelität wird ausschließlich durch
`.partitioner(...).step(workerStep).taskExecutor(...)` im `masterStep` konfiguriert.

### Warum @StepScope auf Reader und Writer, nicht auf Processor?

| Komponente | Zustand | @StepScope nötig? |
|---|---|---|
| `FlatFileItemReader` | ja — Datei-Cursor, aktuelle Zeile | ja |
| `JpaItemWriter` | ja — EntityManager-Instanz | ja |
| `StudentItemProcessor` | nein — reine Funktion | nein |

Zustandsbehaftete Beans bei paralleler Ausführung ohne `@StepScope` würden sich
die Instanz teilen — das führt zu korrupten Ergebnissen oder Exceptions.

### Fehlerbehandlung: Erfolg vs. Fehler

- **Erfolg**: `cleanupStep` läuft — `CleanupTasklet` leert `work/`, verschiebt nach `processed/`
- **Fehler**: `cleanupStep` wird übersprungen — `FileFailureListener.afterJob()` greift
  und verschiebt die Original-CSV nach `failed/`

Spring Batch führt Steps nach einem fehlgeschlagenen Step standardmäßig nicht mehr aus.
Deshalb kann der Fehlerfall nicht im `cleanupStep` behandelt werden — er braucht einen
`JobExecutionListener`, der immer aufgerufen wird.

### SimpleAsyncTaskExecutor vs. ThreadPoolTaskExecutor

| | SimpleAsyncTaskExecutor | ThreadPoolTaskExecutor |
|---|---|---|
| Thread-Limit | keines | konfigurierbar |
| Produktion | nicht empfohlen | empfohlen |
| Schulung | gut sichtbar | mehr Konfiguration |

Für Produktion immer `ThreadPoolTaskExecutor` verwenden:
```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(4);
executor.setMaxPoolSize(4);
executor.afterPropertiesSet();
```

### Zeitstempel als JobParameter
Spring Batch lehnt einen Job-Start ab, wenn er mit identischen Parametern bereits
erfolgreich gelaufen ist. Der Zeitstempel macht jeden Start eindeutig.

---

## Was im Log zu sehen ist

```
INFO  JobRunner          - === Starte Partitionierungs-Job ===
INFO  SplitTasklet       - Split abgeschlossen: 4 Dateien in .../work
INFO  StudentPartitioner - Partitioner erstellt 4 Partitionen
DEBUG StudentItemProcessor - Gemappt: Anna Mueller → 3f2a1b...  ← Thread 1
DEBUG StudentItemProcessor - Gemappt: Ben Schmidt  → 7c4d9e...  ← Thread 3  (parallel!)
DEBUG StudentItemProcessor - Gemappt: Clara Kern   → a1b2c3...  ← Thread 2  (parallel!)
...
INFO  CleanupTasklet     - Cleanup abgeschlossen
INFO  JobRunner          - === Job beendet mit Status: COMPLETED ===
```

Die DEBUG-Zeilen aus dem Processor erscheinen durcheinander — das ist gewollt
und beweist, dass die 4 Threads wirklich gleichzeitig laufen.

---

## Was tun, wenn die Quelle eine große Datenbanktabelle ist?

### Das Grundproblem
Eine große Tabelle kann man nicht "splitten" wie eine CSV. Man muss sie
**logisch partitionieren** — jedem Worker einen bestimmten Zeilenbereich zuweisen,
ohne die Daten vorher zu kopieren. Der SplitTasklet und das work/-Verzeichnis
entfallen komplett.

### Ansatz 1: ID-Range — der Standardweg (Projekt 06)
Der `ColumnRangePartitioner` aus Projekt 06 macht genau das:

```
Tabelle: 1.000.000 Zeilen, id 1–1.000.000
         ↓
Partitioner fragt: SELECT MIN(id), MAX(id)
         ↓
partition1 → ExecutionContext { minValue:       1, maxValue:  250.000 }
partition2 → ExecutionContext { minValue: 250.001, maxValue:  500.000 }
partition3 → ExecutionContext { minValue: 500.001, maxValue:  750.000 }
partition4 → ExecutionContext { minValue: 750.001, maxValue: 1.000.000 }
```

Jeder Worker liest mit `JdbcPagingItemReader` nur seinen Bereich:
```sql
SELECT * FROM tabelle WHERE id >= :minValue AND id <= :maxValue
```

**Problem:** Funktioniert gut nur bei gleichmäßig verteilten IDs.
Bei vielen Lücken (z.B. gelöschte Zeilen) bekommt ein Worker deutlich weniger Arbeit.

### Ansatz 2: Partition per Hash — gleichmäßig bei Lücken
```sql
WHERE MOD(id, :gridSize) = :partitionIndex
```
Egal wie viele Lücken es gibt — die Last ist immer gleichmäßig verteilt.
Leicht teurer, weil die DB für jede Zeile die Modulo-Funktion berechnet.

### Ansatz 3: Partition per Datum oder Status — fachlich sinnvoll
```sql
WHERE created_date BETWEEN :fromDate AND :toDate
-- oder: nur unverarbeitete Zeilen
WHERE status = 'NEU'
```
Typisch für Batch-Jobs, die täglich laufen und nur neue oder geänderte Datensätze verarbeiten.

### Ansatz 4: Cursor-Reader — nur für kleine Mengen
`JdbcCursorItemReader` hält eine DB-Verbindung während des gesamten Steps offen.
Für Millionen Zeilen ist das gefährlich: Connection-Timeout, lange Transaktionen,
kein echter Neustart bei Fehler. Nicht für Partitionierung geeignet.

### Ansatz 5: Remote Partitioning — über mehrere JVMs skalieren
Wenn eine Maschine nicht ausreicht, kann Spring Batch Worker-Steps auf separate JVMs
auslagern — z.B. via RabbitMQ oder Kafka. Der Master schickt ExecutionContexts als
Nachrichten, Worker-Prozesse auf anderen Servern hören zu:

```
Master-JVM                     Worker-JVM 1: Zeilen       1–250.000
    │── ExecutionContext ──►    Worker-JVM 2: Zeilen 250.001–500.000
    │── ExecutionContext ──►    Worker-JVM 3: Zeilen 500.001–750.000
    └── ExecutionContext ──►    Worker-JVM 4: Zeilen 750.001–1.000.000
```

Das ist der Weg für wirklich große Datenmengen — aber deutlich mehr Infrastruktur.

### Entscheidungsbaum

```
Quelle: große Tabelle
        │
        ├─► IDs gleichmäßig verteilt?
        │       ja  → ColumnRangePartitioner (wie Projekt 06)
        │       nein → Hash-Partitionierung
        │
        ├─► Nur neue/geänderte Zeilen verarbeiten?
        │       → Status- oder Datum-Partitionierung
        │
        └─► > 10 Mio. Zeilen, eine Maschine reicht nicht?
                → Remote Partitioning (Spring Batch + Messaging)
```
