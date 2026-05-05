# Erklärungen – Human-to-Customer Batch Job

## Überblick

```
tbl_humans (H2 DB)
       │
       ▼
  HumanPartitioner          ← zählt Zeilen, teilt in 5 Partitionen auf
       │
  ┌────┴─────┬────────┬────────┬────────┐
  │          │        │        │        │
  P0         P1       P2       P3       P4     ← parallele Worker-Steps
  │
  JdbcCursorItemReader      ← liest Human-Records per OFFSET/LIMIT
  │
  CompositeItemProcessor
    ├── GenderCountProcessor ← zählt male/female/non-binary im ExecutionContext
    └── HumanToCustomerProcessor ← MapStruct + UUID als PK
  │
  JpaItemWriter              ← schreibt Customer in tbl_customers
```

---

## Warum OFFSET/LIMIT statt ID-Range?

`tbl_humans` hat **keine numerische ID**. Die klassische Partitionierungsstrategie
(z. B. `ColumnRangePartitioner`) teilt per `id >= minValue AND id <= maxValue` auf.
Das geht hier nicht.

**Alternative: Zeilennummer per OFFSET/LIMIT**

Der `HumanPartitioner` zählt die Gesamtzahl der Zeilen und berechnet für jede
Partition ein `offset` und ein `limit`. Der Reader fragt dann:

```sql
SELECT ... FROM tbl_humans ORDER BY last_name, first_name LIMIT ? OFFSET ?
```

`ORDER BY` ist zwingend – ohne feste Sortierung wäre die Aufteilung nicht deterministisch
und Zeilen könnten in unterschiedlichen Läufen unterschiedlichen Partitionen zugeordnet werden.

---

## Aggregator vs. Listener – warum der Aggregator die bessere Wahl ist

Spring Batch bietet zwei Möglichkeiten, Partition-Ergebnisse zusammenzuführen:

| | `StepExecutionAggregator` | `StepExecutionListener.afterStep()` |
|---|---|---|
| Zugriff auf Worker-Executions | bekommt sie als `Collection<StepExecution>` übergeben | muss selbst über `getJobExecution().getStepExecutions()` iterieren und filtern |
| Zweck | genau für Partition-Aggregation entworfen | allgemeiner Vor-/Nachher-Hook |
| Schreibziel | master-`StepExecutionContext` | frei wählbar |

Der `StepExecutionAggregator` ist der semantisch korrekte Baustein: Spring Batch ruft
`aggregate(result, partitions)` intern auf, nachdem alle Worker fertig sind – wir müssen
uns nicht selbst darum kümmern, welche Step-Executions zu unserer Partition gehören.

Weil der Aggregator in den **master-StepContext** schreibt (nicht den JobContext),
wird ein `ExecutionContextPromotionListener` als zweiter Schritt benötigt:
Er kopiert die konfigurierten Keys nach `afterStep()` automatisch in den
`JobExecutionContext`, von wo der nachfolgende `summaryStep` sie lesen kann.

**Ablauf:**
```
GenderCountAggregator.aggregate()           → master-StepExecutionContext befüllen
ExecutionContextPromotionListener.afterStep() → master-StepContext → JobExecutionContext
GenderSummaryTasklet.execute()              → JobExecutionContext lesen + ausgeben
```

---

## Warum @StepScope auf GenderCountProcessor?

Jede Partition läuft in einem **eigenen Thread** mit einer **eigenen StepExecution**.
`@StepScope` sorgt dafür, dass Spring pro StepExecution eine **neue Instanz** des
Processors erstellt. Damit:

- hat jede Partition ihren eigenen Zähler-Stand (`maleCount`, `femaleCount`, `nonBinaryCount`)
- gibt es keine Thread-Safety-Probleme zwischen Partitionen
- kann die `StepExecution` per `@Value("#{stepExecution}")` injiziert werden

Die Zähler werden nach jedem verarbeiteten Item in den `ExecutionContext` geschrieben.
Spring Batch persistiert diesen Context am **Ende jedes Chunks** in der Tabelle
`BATCH_STEP_EXECUTION_CONTEXT` – das ermöglicht Restarts ohne Zähler-Verlust.

**Achtung:** Jede Partition speichert *ihren eigenen Teilzähler*.
Um den Gesamtzähler zu erhalten, müssten nach dem Job alle Partitions-StepExecutions
aggregiert werden (z. B. über `JobExecutionListener`).

---

## Warum @StepScope auch auf CompositeItemProcessor?

Der `CompositeItemProcessor` enthält den step-scoped `GenderCountProcessor` als Delegate.
Damit der Composite selbst bei jedem Partitions-Step neu erzeugt wird und den richtigen
Delegate-Zustand hat, wird er ebenfalls mit `@StepScope` annotiert.

---

## Warum UUID als Primary Key?

`tbl_customers` ist eine neue JPA-Entity-Tabelle, die von Hibernate beim Start angelegt
wird (`spring.jpa.hibernate.ddl-auto=create-drop`). Da `tbl_humans` keine eigene ID hat,
gibt es keinen natürlichen Schlüssel. Eine **UUID** ist:

- global eindeutig (auch bei parallelem Einfügen aus mehreren Threads sicher)
- unabhängig von der Datenbank (kein Auto-Increment nötig)

---

## Spring Batch 6 – Wichtige Package-Änderungen

Spring Boot 4 verwendet Spring Batch **6.0**. Gegenüber Spring Batch 5 (Spring Boot 3)
wurden die Packages **komplett umstrukturiert**:

| Spring Batch 5 (SB3)                              | Spring Batch 6 (SB4)                                        |
|---------------------------------------------------|-------------------------------------------------------------|
| `org.springframework.batch.core.Job`              | `org.springframework.batch.core.job.Job`                    |
| `org.springframework.batch.core.Step`             | `org.springframework.batch.core.step.Step`                  |
| `org.springframework.batch.core.StepExecution`    | `org.springframework.batch.core.step.StepExecution`         |
| `org.springframework.batch.item.ItemProcessor`    | `org.springframework.batch.infrastructure.item.ItemProcessor`|
| `org.springframework.batch.item.ExecutionContext` | `org.springframework.batch.infrastructure.item.ExecutionContext`|
| `org.springframework.batch.item.database.*`       | `org.springframework.batch.infrastructure.item.database.*`  |
| `o.s.b.core.partition.support.Partitioner`        | `org.springframework.batch.core.partition.Partitioner`      |

Außerdem: `spring-batch-core` ist in Spring Boot 4 **nicht automatisch** als compile-Dependency
eingebunden. Es muss explizit in der `pom.xml` ergänzt werden.

---

## Paketstruktur

```
de.limago.uebung
├── entity
│   ├── Human.java             ← record (Eingabe)
│   └── Customer.java          ← JPA Entity (Ausgabe, tbl_customers)
├── mapper
│   └── HumanCustomerMapper.java  ← MapStruct
└── batch
    ├── HumanJobRunner.java       ← CommandLineRunner startet den Job
    ├── config
    │   └── HumanBatchConfig.java ← Job, Steps, Reader, Writer, Composite
    ├── partitioner
    │   └── HumanPartitioner.java ← OFFSET/LIMIT Partitionierung
    └── processor
        ├── GenderCountProcessor.java       ← pass-through, zählt Gender
        └── HumanToCustomerProcessor.java   ← konvertiert + UUID
```
