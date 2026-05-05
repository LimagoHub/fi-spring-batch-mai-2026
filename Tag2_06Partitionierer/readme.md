# Spring Batch Partitionierer -- Detaillierte Dokumentation

## 1. Grundidee des Partitionierers

Ein **Partitionierer** in Spring Batch teilt eine große Datenmenge in
mehrere logische Bereiche (Partitionen) auf.\
Jede Partition wird anschließend parallel von einem eigenen Worker-Step
verarbeitet.

Ziel: - Parallele Verarbeitung großer Datenmengen - Bessere
Skalierbarkeit - Reduzierung der Laufzeit bei großen Tabellen

In diesem Projekt werden: - 1000 Datensätze aus `tbl_personen` - in 4
ID-Bereiche aufgeteilt - parallel als CSV-Dateien exportiert -
anschließend wieder in einer Gesamtdatei zusammengeführt

------------------------------------------------------------------------

## 2. Abgrenzung: Partitioning vs. Multithreading in Spring Batch

### Multithreaded Step

-   Ein einzelner Step
-   Mehrere Threads bearbeiten denselben Reader/Writer
-   Reader muss thread-safe sein
-   Keine logische Datenaufteilung

Nachteil: - Nicht jeder Reader ist thread-safe - Schwieriger zu
kontrollieren

------------------------------------------------------------------------

### Partitioning (hier verwendet)

-   Master-Step erzeugt mehrere Partitionen
-   Jede Partition hat eigenen ExecutionContext
-   Jeder Worker-Step bekommt eigene Parameter (z. B. ID-Bereich)
-   Saubere logische Trennung

Vorteile: - Klare Datenaufteilung - Kein thread-sicherer Reader
notwendig - Sehr gut skalierbar (auch remote möglich)

Partitioning ist robuster und besser kontrollierbar als reines
Multithreading.

------------------------------------------------------------------------

## 3. ColumnRangePartitioner

``` java
public Map<String, ExecutionContext> partition(int gridSize)
```

gridSize = Anzahl paralleler Partitionen (hier 4)

Berechnung: - min = 1 - max = 1000 - targetSize = (max - min) /
gridSize + 1

Beispiel bei gridSize = 4: - Partition 1 → 1--250 - Partition 2 →
251--500 - Partition 3 → 501--750 - Partition 4 → 751--1000

Für jede Partition wird ein eigener ExecutionContext erzeugt:

``` java
value.putInt("minValue", start);
value.putInt("maxValue", end);
```

Diese Werte werden später im Reader verwendet.

------------------------------------------------------------------------

## 4. Der Reader -- Warum werden dort `null` Parameter übergeben?

WICHTIGER PUNKT:

Im Worker-Step wird folgendes aufgerufen:

``` java
.reader(reader(ds, null, null))
.writer(writer(null))
```

Warum `null`?

Der Reader ist mit `@StepScope` annotiert:

``` java
@Bean
@StepScope
public JdbcPagingItemReader<Person> reader(...)
```

### Was bedeutet @StepScope?

-   Spring erzeugt beim Application-Start **nicht sofort das echte
    Objekt**
-   Stattdessen wird ein **Proxy-Objekt** erzeugt
-   Das echte Objekt wird erst zur Laufzeit erstellt
-   Zu diesem Zeitpunkt existiert der StepExecutionContext bereits

Das bedeutet:

Die Werte für

``` java
@Value("#{stepExecutionContext['minValue']}")
@Value("#{stepExecutionContext['maxValue']}")
```

werden **nicht beim Methodenaufruf gesetzt**, sondern erst beim Start
der jeweiligen Partition.

Deshalb sind die `null` Parameter nur Platzhalter.

Spring ersetzt sie zur Laufzeit mit den Werten aus dem ExecutionContext.

Ohne @StepScope würde: - der Reader sofort instanziiert -
minValue/maxValue wären null - die Partitionierung würde nicht
funktionieren

Merksatz:

`@StepScope + Proxy + spätere Parameterauflösung = korrektes Partitioning`

------------------------------------------------------------------------

## 5. JdbcPagingItemReader

Warum Paging statt Cursor?

-   Speicherschonender
-   Bessere Kontrolle über Sortierung
-   Stabil bei großen Datenmengen

H2PagingQueryProvider erzeugt SQL wie:

``` sql
SELECT id, first_name, last_name
FROM tbl_personen
WHERE id >= X AND id <= Y
ORDER BY id ASC
LIMIT ...
```

Jede Partition bekommt ihre eigene WHERE-Clause.

------------------------------------------------------------------------

## 6. Writer

Der Writer ist ebenfalls @StepScope.

Dateiname basiert auf minValue:

``` java
persons_part_<minValue>.csv
```

Dadurch entsteht pro Partition eine eigene Datei.

------------------------------------------------------------------------

## 7. Master-Step

``` java
.partitioner(workerStep.getName(), partitioner())
.gridSize(4)
.taskExecutor(new SimpleAsyncTaskExecutor())
```

Ablauf: 1. Master-Step erzeugt 4 ExecutionContexts 2. Für jede Partition
wird Worker-Step gestartet 3. Worker-Step bekommt eigene Parameter 4.
Verarbeitung läuft parallel

------------------------------------------------------------------------

## 8. Merge-Step

FileMergingTasklet:

-   Sucht alle Dateien im Ordner outputs
-   Sortiert sie
-   Fügt sie in all_persons_final.csv zusammen

Dies geschieht sequenziell nach Abschluss aller Partitionen.

------------------------------------------------------------------------

## 9. Gesamtablauf des Jobs

partitionJob:

1.  masterStep (parallel)
2.  mergeStep (seriell)

------------------------------------------------------------------------

## 10. Architekturübersicht

Job ├── MasterStep │ ├── Partition 1 → WorkerStep (1--250) │ ├──
Partition 2 → WorkerStep (251--500) │ ├── Partition 3 → WorkerStep
(501--750) │ └── Partition 4 → WorkerStep (751--1000) └── MergeStep

------------------------------------------------------------------------

## 11. Warum ist die Lösung korrekt?

✔ Saubere Partitionierung\
✔ Parallele Verarbeitung\
✔ Keine Thread-Safety-Probleme\
✔ Klare Verantwortlichkeiten\
✔ StepScope korrekt verwendet\
✔ ExecutionContext korrekt genutzt

------------------------------------------------------------------------

## 12. Wichtigstes Lernziel

Das entscheidende Verständnis:

Die Parameter werden NICHT beim Methodenaufruf gesetzt, sondern beim
tatsächlichen Step-Start durch Spring.

Deshalb funktionieren die `null`-Parameter korrekt.

Ohne dieses Verständnis wirkt der Code fehlerhaft -- ist aber exakt so
gewollt.

------------------------------------------------------------------------

Ende der Dokumentation
