# Tag2_03Conditional_Flow_Verzweigung — Erklärungen und Hintergründe

---

## Was demonstriert dieses Projekt?

Das Projekt zeigt, wie ein Spring-Batch-Job **abhängig vom Ergebnis eines Steps**
unterschiedliche Wege nehmen kann — den sogenannten **Conditional Flow**.

Der zentrale Gedanke: ein fehlgeschlagener Step muss nicht zwingend den ganzen Job abbrechen.
Stattdessen kann ein eigener **Fehlerbehandlungs-Ast** folgen, der z.B. einen Fehler-Report
schreibt oder eine Benachrichtigung sendet — bevor der Job sauber beendet wird.

---

## Konzept 1: ExitStatus als Routing-Grundlage

Spring Batch unterscheidet zwei Status-Konzepte:

| | BatchStatus | ExitStatus |
|---|---|---|
| Zweck | interner Zustand der Ausführung | Ergebnis für die Weiterverarbeitung |
| Werte | STARTING, STARTED, COMPLETED, FAILED, ... | frei wählbar (String) |
| Wer setzt ihn | Spring Batch intern | Step, Listener oder eigener Code |

Für den Conditional Flow zählt der **ExitStatus**: `.on("FAILED")` oder `.on("*")`
reagieren auf den ExitStatus-String, nicht auf den BatchStatus.

Wirft der Step eine Exception, setzt Spring Batch den ExitStatus auf `"FAILED"`.
Läuft er durch, ist der ExitStatus `"COMPLETED"`.

---

## Konzept 2: Reihenfolge der .on()-Regeln ist entscheidend

```java
.flow(arbeitsStep)
.on("FAILED").to(fehlerbehandlungsStep).next(abschliessenderStep)
.from(arbeitsStep).on("*").to(okStep).next(abschliessenderStep)
```

Spring Batch prüft die `.on()`-Regeln **in der Reihenfolge ihrer Definition**.
`"FAILED"` muss **vor** `"*"` stehen, weil `"*"` alle Werte trifft — auch `"FAILED"`.
Wäre die Reihenfolge umgekehrt, würde der Fehlerbehandlungs-Ast nie erreicht.

```
Richtig:   FAILED → fehlerbehandlungsStep    "*" (Fallback) → okStep
Falsch:    "*" (trifft auch FAILED!) → okStep   ← fehlerbehandlungsStep wird nie aufgerufen
```

---

## Konzept 3: Beide Pfade enden beim abschliessenderStep

```
arbeitsStep
  │
  ├── FAILED ──► fehlerbehandlungsStep ──► abschliessenderStep
  │
  └── *      ──► okStep               ──► abschliessenderStep
```

Der `abschliessenderStep` (z.B. Aufräumen, Statistik) läuft immer — unabhängig davon,
welchen Weg der Job genommen hat. Das ist ein häufiges Produktionsmuster.

Wichtig: obwohl der `arbeitsStep` eine Exception geworfen hat (ExitStatus `FAILED`),
**endet der Job mit `COMPLETED`** — weil der Fehler behandelt wurde und alle
nachfolgenden Steps erfolgreich liefen.

---

## Konzept 4: Alternative — JobExecutionDecider (auskommentiert)

Der Code enthält eine auskommentierte Decider-Variante:

```java
public static class MeinEntscheider implements JobExecutionDecider {
    public FlowExecutionStatus decide(JobExecution je, StepExecution se) {
        // beliebige Entscheidungslogik
    }
}
```

**Wann Decider statt ExitStatus?**

- Wenn die Entscheidung nicht vom Step-Ergebnis abhängt, sondern von externen Faktoren
  (Datum, Datenbankinhalt, Konfiguration)
- Wenn mehrere Steps zum gleichen Routing-Ergebnis führen sollen
- Wenn die Routing-Logik komplexer ist als ein einfacher String-Vergleich

Für dieses Beispiel reicht der ExitStatus — der Decider ist nur als Vergleich erhalten.

---

## Konzept 5: JobAbcCommandLineRunner — manueller Job-Start

Spring Batch startet Jobs normalerweise **automatisch** beim Anwendungsstart.
Hier übernimmt stattdessen ein `CommandLineRunner`, weil die **JobParameter** aus
Kommandozeilenargumenten gelesen werden müssen.

```
mvn spring-boot:run -Dspring-boot.run.arguments=--okOderFehler=ok
```

Der Zeitstempel-Parameter verhindert `JobInstanceAlreadyCompleteException`:
Spring Batch erlaubt denselben Job mit denselben Parametern nur einmal erfolgreich zu starten.
Jeder neue Start bekommt eine neue Millisekunde als ID.

---

## Konzept 6: Deterministischer Test mit Awaitility

```java
@SpringBootTest(properties = {
    "OK_ODER_FEHLER=ok",
    "spring.batch.job.enabled=true"
})
```

Ohne `OK_ODER_FEHLER=ok` würde der `ArbeitsTasklet` per Zufall (50/50) Erfolg oder Fehler
zurückgeben — der Test würde bei jedem zweiten Lauf fehlschlagen.

`Awaitility` ersetzt `Thread.sleep`:
- Schlägt fehl, sobald das Timeout (5 Sekunden) überschritten wird
- Endet sofort, sobald die Bedingung erfüllt ist
- Kein fixes Warten auf einen willkürlichen Timeout-Wert

```java
await().atMost(5, TimeUnit.SECONDS).until(() -> !je.isRunning());
```

---

## Ablauf im Überblick

```
Anwendungsstart
  │
  └─► JobAbcCommandLineRunner.run()
         │
         └─► arbeitsStep (ArbeitsTasklet)
                │
                ├── Exception geworfen (ExitStatus: FAILED)
                │      └─► fehlerbehandlungsStep
                │                └─► abschliessenderStep → Job: COMPLETED
                │
                └── kein Fehler (ExitStatus: COMPLETED)
                       └─► okStep
                               └─► abschliessenderStep → Job: COMPLETED
```

---

## Was im Log zu sehen ist

**Erfolgsfall (`--okOderFehler=ok`):**
```
=== Starte JobAbc mit Parametern: {OK_ODER_FEHLER=ok, ts=...} ===
---- Job: jobAbc, mit JobParametern: {OK_ODER_FEHLER=ok, ts=...}
ArbeitsStep: ok
OkStep
AbschliessenderStep
=== Job beendet ===
Status:     COMPLETED
ExitStatus: ExitStatus{exitCode=COMPLETED, exitDescription=''}
```

**Fehlerfall (`--okOderFehler=fehler`):**
```
=== Starte JobAbc mit Parametern: {OK_ODER_FEHLER=fehler, ts=...} ===
ArbeitsStep: mit Fehler
FehlerbehandlungsStep
AbschliessenderStep
=== Job beendet ===
Status:     COMPLETED
ExitStatus: ExitStatus{exitCode=COMPLETED, exitDescription=''}
```

Der Job endet in beiden Fällen `COMPLETED` — der Fehler wurde behandelt,
nicht ignoriert.
