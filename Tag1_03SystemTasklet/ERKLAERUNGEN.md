# Tag1_03SystemTasklet — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Dieses Projekt zeigt, wie Spring Batch einen **Betriebssystem-Befehl** als Step
ausführen kann — ohne eigene Java-Logik zu schreiben. Der `SystemCommandTasklet`
startet einen externen Prozess (hier ein Windows-Bat-Script), wartet auf seinen
Abschluss und meldet Erfolg oder Fehler an Spring Batch zurück.

Das ist der Einstieg in das **Tasklet-Modell**: statt des komplexen
Reader-Processor-Writer-Dreiklangs (Chunk-orientiert) erledigt ein Tasklet
eine einzelne, in sich abgeschlossene Aufgabe.

---

## Welches Spring-Batch-Konzept steht im Mittelpunkt?

### Tasklet-Step vs. Chunk-Step

Spring Batch kennt zwei Arten von Steps:

| Step-Typ | Wann sinnvoll |
|---|---|
| **Chunk-Step** | Große Datenmengen, itemweise lesen/verarbeiten/schreiben |
| **Tasklet-Step** | Einmalige Aktionen: Datei umbenennen, DB leeren, Script starten |

Ein `SystemCommandTasklet` ist ein fertig mitgeliefertes Tasklet, das einen
Betriebssystem-Prozess kapselt. Man muss das Interface `Tasklet` nicht selbst
implementieren.

```
Job: meinTaskJob
 └─► step1  (SystemCommandTasklet)
              └─► cmd /c copy-rename.bat
                        └─► verschiebt *.csv von inbox/ nach input/
```

### Was das Bat-Script tut

```bat
for %%f in (..\inbox\*.csv) do (
    move "%%f" "..\input\%%~nf-data%%~xf"
)
```

Es verschiebt alle CSV-Dateien aus dem `inbox`-Ordner in den `input`-Ordner
und hängt `-data` an den Dateinamen (aus `sample1.csv` wird `sample1-data.csv`).
Das Umbenennen ist wichtig, weil der MultiFileReader im Folge-Projekt gezielt
nach `*-data.csv`-Dateien sucht.

---

## Warum `cmd /c` und nicht direkt der Scriptname?

Java startet externe Prozesse über `Runtime.exec()` bzw. `ProcessBuilder`.
Diese können nur **ausführbare Programme** starten — keine Shell-Befehle wie
`copy`, `move` oder Bat-Scripts, die eine Shell-Umgebung voraussetzen.

`cmd /c` startet die Windows-Command-Shell, die dann das Bat-Script interpretiert:

```java
tasklet.setCommand("cmd", "/c", "copy-rename.bat");
```

Auf Linux würde man entsprechend `"bash", "-c", "myscript.sh"` verwenden.

---

## Warum `@StepScope` auf dem Tasklet?

`SystemCommandTasklet` hält intern ein Prozess-Handle und Status-Informationen —
er ist **zustandsbehaftet**. Würde Spring nur eine einzige Instanz für alle
Job-Ausführungen teilen (Singleton), könnten sich parallele oder aufeinander-
folgende Läufe gegenseitig stören.

Mit `@StepScope` bekommt jede Step-Ausführung ihre eigene Tasklet-Instanz:

```java
@Bean
@StepScope
public SystemCommandTasklet task1() { ... }
```

---

## Warum `@JobScope` auf dem Step?

`@JobScope` stellt sicher, dass der Step erst beim Start des Jobs instanziiert wird.
Das ist nötig, wenn der Step auf `JobParameters` zugreifen würde (z.B. einen
Dateinamen aus dem Parameter lesen). Hier ist es eine vorbereitende Annotation
für den typischen Erweiterungsfall.

---

## Warum `RunIdIncrementer`?

Spring Batch speichert jeden Job-Lauf in der Job-Repository-Datenbank. Ein neuer
Start mit **identischen Parametern** wird als Wiederholung eines bereits
abgeschlossenen Jobs abgelehnt.

Der `RunIdIncrementer` erhöht bei jedem Start automatisch einen `run.id`-Counter,
sodass jeder Lauf als eigenständige Job-Instanz gilt:

```
Lauf 1: run.id=1
Lauf 2: run.id=2
Lauf 3: run.id=3  ← Spring Batch erkennt jeden als neuen Lauf
```

Der `AppRunner` verwendet alternativ eine UUID als Parameter — beide Ansätze
lösen das Problem, `RunIdIncrementer` ist der Spring-Batch-native Weg.

---

## Timeout — warum 5000 ms?

```java
tasklet.setTimeout(5000);
```

Ohne Timeout würde der Job ewig warten, falls das Script hängt (z.B. eine
gesperrte Datei, ein fehlender Ordner oder ein interaktiver Dialog). 5 Sekunden
ist für ein einfaches `move`-Kommando mehr als ausreichend und verhindert,
dass der Job in der Produktion blockiert.

---

## Ablauf im Überblick

```
Programmstart (CommandLineRunner)
        │
        ▼
AppRunner.run()
        │
        ▼
jobLauncher.run(job, params)
        │
        ▼
step1: SystemCommandTasklet
        │
        ├─► Startet:  cmd /c copy-rename.bat
        │             (Working Directory: ./src/main/resources/script)
        │
        ├─► Wartet bis zu 5000 ms
        │
        └─► Exit-Code 0 → Spring Batch: COMPLETED
            Exit-Code ≠ 0 → Spring Batch: FAILED
```
