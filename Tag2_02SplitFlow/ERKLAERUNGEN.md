# Tag2_02SplitFlow — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Das Projekt zeigt, wie Spring Batch Steps **parallel** ausführt — ohne Partitionierung,
sondern über einen **Split Flow**: mehrere Flows laufen gleichzeitig in eigenen Threads,
während vor und nach dem Split sequentielle Steps den Ablauf rahmen.

Der Test beweist die echte Parallelität mathematisch: die **Summe der Step-Dauern ist
größer als die Gesamtdauer des Jobs** — das ist nur möglich, wenn Steps gleichzeitig liefen.

---

## Konzept 1: Split Flow vs. Partitionierung

| | Split Flow | Partitionierung |
|---|---|---|
| Einheit der Parallelität | ganzer Flow (mehrere Steps) | einzelner Step, viele Daten-Partitionen |
| Typischer Einsatz | unabhängige Verarbeitungsstränge | Aufteilen einer großen Datenmenge |
| Synchronisierung | implizit nach dem Split | nach dem Master-Step |
| Konfiguration | `FlowBuilder` + `split()` | `Partitioner` + `.partitioner()` |

Split Flow eignet sich, wenn zwei unabhängige Aufgaben gleichzeitig erledigt werden sollen —
z.B. E-Mail versenden **und** Statistik schreiben, während Daten importiert werden.

---

## Konzept 2: Warum Steps in Flows verpacken?

```java
Flow flow3 = new FlowBuilder<SimpleFlow>("flow3").from(step3).end();
Flow flow4 = new FlowBuilder<SimpleFlow>("flow4").from(step4).end();
```

`split()` erwartet `Flow`-Objekte, keine `Step`-Objekte direkt.
Ein Flow kann auch mehrere Steps hintereinander enthalten — so kann jeder
parallele Ast eine eigene Step-Sequenz haben.

```
splitFlow345
  ├─► flow3  →  step3  (→ könnte step3a, step3b enthalten)
  ├─► flow4  →  step4
  └─► flow5  →  step5
```

---

## Konzept 3: Die Synchronisierungsbarriere nach dem Split

```java
.next(splitFlow345)
.next(step6)          // wartet, bis ALLE parallelen Flows fertig sind
.next(step7)
```

Spring Batch garantiert: `step6` startet erst, wenn `step3`, `step4` **und** `step5`
alle abgeschlossen sind. Diese implizite Barriere ist in den meisten Fällen das gewünschte Verhalten.

Würde `step6` z.B. eine Zusammenfassung aller Ergebnisse schreiben, müsste es wirklich
auf alle drei warten — die Barriere stellt das automatisch sicher.

---

## Konzept 4: SimpleAsyncTaskExecutor in der Schulung

```java
.split(new SimpleAsyncTaskExecutor())
```

`SimpleAsyncTaskExecutor` hat **kein Thread-Pool-Limit**: für jeden parallelen Flow
wird ein neuer Thread erzeugt. Bei 3 Flows: 3 neue Threads.

Für die Schulung ist das ideal, weil kein zusätzlicher Pool konfiguriert werden muss.
Für die Produktion ist `ThreadPoolTaskExecutor` die richtige Wahl:

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(4);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(25);
executor.afterPropertiesSet();
```

Mit einem Pool werden Ressourcen kontrolliert und ein Überlaufen bei vielen parallelen
Flows verhindert.

---

## Konzept 5: Gesamtablauf des Jobs

```
step1  (sequentiell)
  │
step2  (sequentiell)
  │
  ├─► step3  ─┐
  ├─► step4  ─┤  gleichzeitig (3 Threads)
  └─► step5  ─┘
               │
              step6  (wartet auf alle drei)
               │
              step7  (sequentiell)
```

`step3`, `step4` und `step5` haben unterschiedlich viele Iterationen (4, 6, 8).
Dadurch enden sie zu verschiedenen Zeitpunkten — die verschränkte Ausgabe in der
Konsole macht das sichtbar.

---

## Konzept 6: Der Parallelitätsbeweis im Test

```java
assertThat(dauerSum).isGreaterThan(dauerJob);
```

Bei rein sequentieller Ausführung gilt: `Gesamtdauer ≈ Summe der Step-Dauern`.
Bei echter Parallelität läuft der langsamste parallele Flow die Zeit vor,
die anderen laufen "kostenlos" daneben:

```
Sequentiell:   |--step3--|--step4--|--step5--|  Gesamt = Summe
Parallel:      |--step3--|
               |---step4----|         Gesamt ≈ max(step3, step4, step5)
               |-----step5------|
```

Die Assertion `dauerSum > dauerJob` schlägt fehl, wenn die Steps doch sequentiell liefen —
z.B. weil der TaskExecutor nicht korrekt konfiguriert wurde.

---

## Was im Log zu sehen ist

```
1 1 2 1 3 2 1 4 3 2 1 5 4 3 2  ...
```

Die Ziffern 3, 4, 5 erscheinen durcheinander — das ist der visuelle Beweis der Parallelität.
1 und 2 erscheinen davor gebündelt (sequentiell), 6 und 7 danach gebündelt.
