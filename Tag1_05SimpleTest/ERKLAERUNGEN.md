# Tag2_01SimpleTest — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Das Projekt zeigt zwei grundlegende Spring-Batch-Muster nebeneinander:

1. **Tasklet-Job** (`meinTaskletJob`): minimale Konfiguration, zwei Steps ohne Reader/Processor/Writer.
2. **Chunk-orientierter Job** (`importUserJob`): vollständiger Lese-Transformations-Schreib-Zyklus mit CSV-Eingabe und JSON-Ausgabe.

Außerdem demonstriert das Projekt, wie Spring Batch Jobs **mit Spring-Boot-Test** getestet werden —
inklusive gemockter Reader-Bean, Golden-File-Vergleich und profil-basierter Bean-Trennung.

---

## Konzept 1: Tasklet vs. Chunk

| | Tasklet | Chunk |
|---|---|---|
| Wann | einmalige Aktion (Datei verschieben, SQL ausführen) | viele gleichartige Items verarbeiten |
| Transaktion | eine Transaktion pro Tasklet-Aufruf | eine Transaktion pro Chunk |
| Restart | bei Fehler: Tasklet läuft komplett neu | bei Fehler: ab dem letzten Commit-Punkt |

### Der doppelte Step-Name — ein bewusster Schulungsfehler

`meinEinsStep` und `meinZweiStep` tragen beide den Namen `"meinLeerzeilenStep"`.
Spring Batch legt Steps anhand ihres **Namens** in der Job-Repository-Datenbank ab.
Gleiche Namen bedeuten: beide Steps schreiben in dieselbe Zeile der `BATCH_STEP_EXECUTION`-Tabelle.
In der Praxis führt das zu unerwartetem Neustart-Verhalten und muss vermieden werden.

---

## Konzept 2: @Profile("production") + @MockitoBean

Der `FlatFileItemReader` ist mit `@Profile("production")` annotiert.
Im Test-Profil (`@ActiveProfiles("test")`) ist diese Bean **nicht aktiv**.
Der Test ersetzt sie durch `@MockitoBean private ItemReader<Person> flatFileItemReaderMock`.

```
Produktiv:          FlatFileItemReader liest sample-data.csv
Test (kein "production"-Profil):  @MockitoBean liefert kontrollierte Testdaten
```

Ohne `@Profile("production")` würde Spring beim Start des Tests versuchen, den echten
`FlatFileItemReader` zu erzeugen — und dabei den SpEL-Ausdruck
`#{jobParameters['file.output']}` auflösen, der im Testkontext noch nicht existiert.

---

## Konzept 3: @StepScope und SpEL für JobParameter

```java
@Bean
@StepScope
public JsonFileItemWriter<Person> jsonItemWriter(
        @Value("#{jobParameters['file.output']}") String output)
```

`@StepScope` bedeutet: die Bean wird **nicht** beim Anwendungsstart erzeugt, sondern erst,
wenn der Step tatsächlich läuft. Erst dann sind die `JobParameters` im Spring-Kontext verfügbar
und der SpEL-Ausdruck kann aufgelöst werden.

Vorteil: Jeder Job-Start kann einen anderen Ausgabepfad verwenden, ohne die Konfiguration zu ändern.

---

## Konzept 4: FaultTolerance — Skip-Konfiguration

```java
.faultTolerant()
.skipLimit(2)
.skip(FlatFileParseException.class)
.noSkip(FileNotFoundException.class)
```

- **skipLimit(2)**: maximal 2 fehlerhafte Items überspringen. Danach bricht der Job ab.
  Ohne Limit könnten alle Items still übersprungen werden — das wäre ein unbemerkte Datenverlust.
- **skip(FlatFileParseException)**: eine kaputte CSV-Zeile ist ein bekannter, tolerierbarer Fehler.
- **noSkip(FileNotFoundException)**: fehlt die Eingabedatei komplett, liegt ein Konfigurationsfehler vor.
  Kein Skip ist sinnvoll — es gibt nichts zu verarbeiten.

---

## Konzept 5: JobCompletionNotificationListener

Der Listener implementiert `JobExecutionListener.afterJob()`.
Er wird aufgerufen, **nachdem** der Job abgeschlossen ist — unabhängig vom Status.
Die Prüfung `if (status == COMPLETED)` stellt sicher, dass nur bei echtem Erfolg
eine Kontrollabfrage auf die Datenbank ausgeführt wird (bei FAILED wären die Daten unvollständig).

---

## Konzept 6: Spring Batch Test mit @SpringBatchTest

`@SpringBatchTest` registriert zwei Hilfsbeans automatisch:

| Bean | Zweck |
|---|---|
| `JobLauncherTestUtils` | startet Jobs und Steps aus dem Test heraus |
| `JobRepositoryTestUtils` | bereinigt Job-Instanzen zwischen Tests |

`@Sql({"/create.sql", "/insert.sql"})` baut die Testdatenbank **vor jedem Test** neu auf,
damit Tests in beliebiger Reihenfolge und unabhängig voneinander laufen können.

### Golden-File-Test

```java
assertThat(new File("input.json")).hasSameTextualContentAs(new File("output.json"));
```

`input.json` ist die **erwartete** Ausgabe (manuell gepflegt im Projektverzeichnis).
`output.json` ist die **tatsächliche** Ausgabe des Jobs.
Ändert sich das Transformationsverhalten, schlägt der Test fehl — das ist gewollt.

---

## Zusammenspiel der Klassen

```
TestTest
  │── @MockitoBean ItemReader<Person>   (liefert Max + Erika Mustermann)
  │
  └── launchJob("importUserJob")
         │
         └── step1
               ├── reader   (Mock)
               ├── processor (PersonItemProcessor: toUpperCase)
               └── writer   (JsonFileItemWriter → output.json)
                                  │
                              Vergleich mit input.json (Golden File)
```

---

## Was im Log zu sehen ist

```
INFO  PersonItemProcessor - Converting (Person[Max, Mustermann, 18]) into (Person[MAX, MUSTERMANN, 10])
INFO  PersonItemProcessor - Converting (Person[Erika, Mustermann, 21]) into (Person[ERIKA, MUSTERMANN, 10])
INFO  JobCompletionNotificationListener - !!! JOB FINISHED! Time to verify the results
INFO  JobCompletionNotificationListener - Found <Person[MAX, MUSTERMANN, 10]> in the database.
INFO  JobCompletionNotificationListener - Found <Person[ERIKA, MUSTERMANN, 10]> in the database.
```
