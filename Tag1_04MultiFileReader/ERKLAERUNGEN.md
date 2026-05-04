# Tag1_04MultiFileReader — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Dieses Projekt zeigt, wie Spring Batch **mehrere CSV-Dateien** in einem einzigen
Job-Step verarbeiten kann — ohne den Step mehrfach zu definieren. Der
`MultiResourceItemReader` iteriert automatisch über alle gefundenen Dateien und
reicht jede Zeile an den `FlatFileItemReader` (delegate) weiter.

Es ist der erste Schritt zum späteren Partitionierungskonzept: hier noch
**sequentiell** (Datei für Datei), in späteren Projekten dann parallel.

---

## Welche Spring-Batch-Konzepte werden gezeigt?

### Chunk-orientierter Step

Das Herzstück ist der klassische Dreiklang:

```
FlatFileItemReader  →  ItemProcessor  →  ItemWriter
(lesen)               (verarbeiten)      (schreiben)
```

Verarbeitung passiert in **Chunks** (Paketen): Spring Batch liest `chunk`-viele
Items, übergibt sie als Liste an den Writer, committed die Transaktion — und
beginnt von vorne.

```java
.<Person, Person>chunk(2, transactionManager)
```

Ein Chunk-Size von 2 bedeutet: nach je 2 gelesenen Personen wird geschrieben und
committet. Im Log ist dadurch das Chunk-Verhalten gut sichtbar.

### MultiResourceItemReader — ein Reader für viele Dateien

```
classpath:input/*.csv
        │
        ├─► sample-data.csv   ─┐
        ├─► sample-data1.csv  ─┤──► FlatFileItemReader (delegate) → Person
        └─► sample2-data.csv  ─┘
```

Der `MultiResourceItemReader` übernimmt die Datei-Navigation. Der delegate-Reader
(`FlatFileItemReader`) weiß nichts davon — er bekommt immer nur eine Zeile und
verarbeitet sie. Wenn eine Datei erschöpft ist, setzt der `MultiResourceItemReader`
die nächste Datei als Ressource.

### FlatFileItemReader mit BeanWrapperFieldSetMapper

```java
.names("firstName", "lastName", "age")
.fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
    setTargetType(Person.class);
}})
```

Der `BeanWrapperFieldSetMapper` nutzt Java-Reflection, um CSV-Spalten anhand
der Setter-Namen auf die Person-Felder zu mappen. `firstName` → `setFirstName()`,
`lastName` → `setLastName()` usw. Die Spaltennamen in `.names()` müssen exakt
zu den Setter-Namen passen.

---

## Warum `classpath:input/*.csv` und nicht `file:`?

```java
@Value("classpath:input/*.csv") Resource[] inputResources
```

`classpath:` sucht Dateien, die beim Maven-Build in den Classpath eingebaut wurden
(d.h. sie lagen zur Build-Zeit unter `src/main/resources/input/`). Das reicht hier,
weil die CSV-Dateien **feste Testdaten** sind, die versioniert im Projekt liegen.

**Einschränkung:** Zur Laufzeit neu erzeugte oder kopierte Dateien werden mit
`classpath:` **nicht** gefunden — dafür braucht man `file:` (wie in Uebung1).

---

## Warum `@StepScope` auf dem Reader?

```java
@Bean
@StepScope
public FlatFileItemReader<Person> reader() { ... }
```

Ein `FlatFileItemReader` hält intern einen Lese-Cursor (aktuelle Zeile, aktueller
Puffer). Würde Spring eine einzige Reader-Instanz für alle Step-Ausführungen
teilen (Singleton), kämen sich gleichzeitige oder aufeinanderfolgende Läufe
in die Quere. `@StepScope` erzeugt pro Step-Ausführung eine eigene Instanz.

---

## Warum hat der `MultiResourceItemReader` kein `@StepScope`?

Im Gegensatz zum Uebung1-Projekt (wo `@StepScope` nötig ist, weil die Dateien
erst zur Laufzeit existieren) sind hier die Ressourcen beim Start bereits bekannt.
Der `MultiResourceItemReader` kann daher als normale Bean leben.

Allerdings: in Partitionierungs-Szenarien (spätere Projekte) muss auch der
`MultiResourceItemReader` `@StepScope` tragen, damit jeder Worker-Thread seinen
eigenen Reader bekommt.

---

## Dummy Processor und Writer — warum keine `@Bean`-Annotation?

```java
public ItemProcessor<Person, Person> processor() { ... }
public ItemWriter<Person> writer() { ... }
```

Processor und Writer sind bewusst **einfache Methoden ohne `@Bean`**, weil sie
nur als Lambdas für Demo-Zwecke existieren (Konsolen-Ausgabe). Sie werden direkt
in der Step-Definition aufgerufen — Spring verwaltet sie nicht als Beans.

In einem echten Projekt würden sie als `@Bean` definiert (z.B. ein `JpaItemWriter`)
und per Dependency-Injection eingebunden.

---

## Chunk-Verhalten im Log sichtbar machen

Mit Chunk-Size 2 und 5 Personen pro Datei (3 Dateien = 15 Personen total)
sieht man im Log klar, wann Chunks beginnen und enden:

```
Verarbeite: Jill Doe          ← Chunk 1 Start
Verarbeite: Joe Doe
Schreibe: Person{...}         ← Chunk 1 Commit
Schreibe: Person{...}
Verarbeite: Justin Time       ← Chunk 2 Start
...
```

Ein größerer Chunk-Wert (z.B. 100) wäre in Produktion effizienter —
weniger Transaktionen, weniger DB-Overhead.

---

## Ablauf im Überblick

```
Programmstart (CommandLineRunner)
        │
        ▼
AppRunner.run()  — UUID-Parameter für Eindeutigkeit
        │
        ▼
step1: MultiResourceItemReader
        │
        ├─► öffnet sample-data.csv
        │    ├─► liest Zeile → FlatFileItemReader → Person
        │    └─► ... (chunk(2) → commit)
        │
        ├─► öffnet sample-data1.csv
        │    └─► ...
        │
        └─► öffnet sample2-data.csv
             └─► ... → COMPLETED
```
