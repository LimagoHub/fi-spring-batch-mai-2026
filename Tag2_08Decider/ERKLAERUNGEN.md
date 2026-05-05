# Tag2_08Decider — Erklärungen

Dieses Projekt demonstriert zwei eng miteinander verwandte Spring-Batch-Konzepte:

1. **`JobExecutionDecider`** — externe Entscheidungslogik, die den Job-Flow steuert
2. **Werteübergabe zwischen Steps** via `ExecutionContext`

---

## Inhaltsverzeichnis

1. [Projektübersicht und Szenario](#1-projektübersicht-und-szenario)
2. [Was ist ein JobExecutionDecider?](#2-was-ist-ein-jobexecutiondecider)
3. [FlowExecutionStatus — das Rückgabeformat des Deciders](#3-flowexecutionstatus--das-rückgabeformat-des-deciders)
4. [ExitStatus vs. FlowExecutionStatus — der wichtige Unterschied](#4-exitstatus-vs-flowexecutionstatus--der-wichtige-unterschied)
5. [Job-Flow-Konfiguration mit dem Decider](#5-job-flow-konfiguration-mit-dem-decider)
6. [Was ist der ExecutionContext?](#6-was-ist-der-executioncontext)
7. [StepExecutionContext vs. JobExecutionContext](#7-stepexecutioncontext-vs-jobexecutioncontext)
8. [Werteübergabe zwischen Steps — der Weg durch den JobExecutionContext](#8-werteübergabe-zwischen-steps--der-weg-durch-den-jobexecutioncontext)
9. [Wann Decider, wann ExitStatus-Routing?](#9-wann-decider-wann-exitstatus-routing)
10. [Test-Strategie](#10-test-strategie)
11. [Datenflussbild (Übersicht)](#11-datenflussbild-übersicht)

---

## 1. Projektübersicht und Szenario

### Job-Flow

```
bestellungErfassenStep          ← Schritt 1, läuft immer
         │
   KundenTypDecider              ← Entscheidet anhand von Parameter + Context
    ├─ "PREMIUM"  → premiumRabattStep       (20% Rabatt)
    ├─ "STANDARD" → standardBearbeitungStep (+ Versandkosten)
    └─ "*"        → fehlerBehandlungStep    (Fehlerprotokoll)
         │
abschlussQuittierungStep         ← Schritt 3, läuft immer
```

### Was wird demonstriert?

| Schritt | Konzept | Details |
|---------|---------|---------|
| Schritt 1 | ExecutionContext **schreiben** | `bestellungId`, `artikel`, `betrag` → JobExecutionContext |
| Decider | **Routing** auf Basis von Parameter + Context-Wert | liest `kundenTyp` (JobParameter) und `betrag` (Context) |
| Schritt 2a/b/c | ExecutionContext **lesen + ergänzen** | liest Schritt-1-Werte, schreibt pfadspezifische Werte |
| Schritt 3 | ExecutionContext **vollständig auslesen** | liest alle Werte aller vorherigen Steps |

---

## 2. Was ist ein JobExecutionDecider?

Ein `JobExecutionDecider` ist eine Spring-Batch-Schnittstelle, die **zwischen zwei Steps eingebettet** wird. Statt dass der ExitStatus eines Steps die Weichenstellung bestimmt, trifft der Decider die Entscheidung auf Basis beliebiger Logik.

```java
public interface JobExecutionDecider {
    FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution);
}
```

### Warum gibt es den Decider?

Das Problem ohne Decider: Die ExitStatus-basierte Verzweigung (aus Tag2_03) koppelt die Entscheidungslogik an den Step selbst. Der Step muss seinen eigenen `ExitStatus` setzen — zum Beispiel durch das Werfen einer Exception für "FAILED". Das hat drei Nachteile:

1. **Kopplung**: Geschäftslogik (was passiert?) und Ablaufsteuerung (wie geht es weiter?) liegen im selben Objekt.
2. **Lesbarkeit**: Man muss den Step-Code lesen, um den Ablauf zu verstehen.
3. **Wiederverwendbarkeit**: Der Step kann nicht mehr ohne seine Routing-Nebenwirkung genutzt werden.

Der Decider löst diese Probleme durch **Separation of Concerns**: Der Step macht seine Arbeit, der Decider entscheidet die Richtung.

### Typische Anwendungsfälle

- Routing auf Basis von **Job-Parametern** (wie hier: `kundenTyp`)
- Routing auf Basis von **Datenbankabfragen** (z.B. "Gibt es noch offene Bestellungen?")
- Routing auf Basis von **berechneten Werten** (z.B. Betragsklassen)
- **Zählerbasierte Schleifen** (Decider gibt "WEITER" oder "STOPP" zurück)
- **Zeitbasiertes Routing** (Wochentag-Logik)

---

## 3. FlowExecutionStatus — das Rückgabeformat des Deciders

Der Decider gibt einen `FlowExecutionStatus` zurück — das ist ein einfaches Wrapper-Objekt um einen String:

```java
return new FlowExecutionStatus("PREMIUM");
// entspricht intern: new FlowExecutionStatus("PREMIUM")
```

Spring Batch definiert einige Standard-Statuse:

| Konstante | String-Wert | Bedeutung |
|-----------|-------------|-----------|
| `FlowExecutionStatus.COMPLETED` | `"COMPLETED"` | Normales Ende |
| `FlowExecutionStatus.FAILED` | `"FAILED"` | Fehlerfall |
| `FlowExecutionStatus.STOPPED` | `"STOPPED"` | Angehalten |
| `FlowExecutionStatus.UNKNOWN` | `"UNKNOWN"` | Unbekannt |

Für **eigene Statuse** erzeugt man einfach eine neue Instanz mit einem beliebigen String:

```java
return new FlowExecutionStatus("PREMIUM_KUNDE");   // eigener Status
return new FlowExecutionStatus("GROSSAUFTRAG");    // beliebiger Name
```

Diese eigenen Strings entsprechen genau den `.on("...")` Mustern im `JobBuilder`.

---

## 4. ExitStatus vs. FlowExecutionStatus — der wichtige Unterschied

Dieser Unterschied verwirrt häufig. Hier eine klare Gegenüberstellung:

| | `ExitStatus` | `FlowExecutionStatus` |
|---|---|---|
| **Erzeuger** | Step (oder Listener) | JobExecutionDecider |
| **Zeitpunkt** | Am Ende eines Steps | Beim Aufruf des Deciders |
| **Persistenz** | Wird in der Batch-DB gespeichert | Nur im Arbeitsspeicher, nicht persistiert |
| **Verwendung** | `.on(exitStatus)` nach `.from(step)` | `.on(status)` nach `.next(decider)` / `.from(decider)` |
| **Standardwerte** | COMPLETED, FAILED, STOPPED, UNKNOWN, NOOP | COMPLETED, FAILED, STOPPED, UNKNOWN |
| **Eigene Werte** | `new ExitStatus("MEIN_STATUS")` | `new FlowExecutionStatus("MEIN_STATUS")` |

### Konkretes Beispiel (Tag2_03 vs. Tag2_08)

**ExitStatus-Routing (Tag2_03)**:
```java
// Der Step wirft eine Exception → ExitStatus wird FAILED
// Der JobBuilder reagiert darauf:
.flow(arbeitsStep)
.on("FAILED").to(fehlerbehandlungsStep)
.from(arbeitsStep).on("*").to(okStep)
```

**Decider-Routing (Tag2_08)**:
```java
// Der Decider gibt einen FlowExecutionStatus zurück
.start(bestellungErfassenStep())
.next(kundenTypDecider)          // Decider wird aufgerufen
.on("PREMIUM").to(premiumRabattStep())
.from(kundenTypDecider)
.on("STANDARD").to(standardBearbeitungStep())
```

---

## 5. Job-Flow-Konfiguration mit dem Decider

### Die Kette im JobBuilder

```java
return new JobBuilder("bestellungsJob", jobRepository)

    // Schritt 1: immer ausführen
    .start(bestellungErfassenStep())

    // Decider einbinden: .next(decider) leitet nach Schritt 1 zum Decider
    .next(kundenTypDecider)
        .on("PREMIUM").to(premiumRabattStep())         // 1. Ausgang

    // .from(decider) für jeden weiteren Ausgang
    .from(kundenTypDecider)
        .on("STANDARD").to(standardBearbeitungStep())  // 2. Ausgang

    // Wildcard-Fallback
    .from(kundenTypDecider)
        .on("*").to(fehlerBehandlungStep())            // 3. Ausgang (alles andere)

    // Alle Pfade → Abschluss-Step
    .from(premiumRabattStep()).on("*").to(abschlussQuittierungStep())
    .from(standardBearbeitungStep()).on("*").to(abschlussQuittierungStep())
    .from(fehlerBehandlungStep()).on("*").to(abschlussQuittierungStep())

    .end()
    .build();
```

### Reihenfolge der .on()-Muster

Die **Reihenfolge ist wichtig**: Spring Batch wertet die Muster **von oben nach unten** aus und nimmt das **erste passende**. Der Wildcard `"*"` muss deshalb **als letztes** stehen.

```java
// RICHTIG: spezifisch vor allgemein
.on("PREMIUM").to(premiumRabattStep())
.on("STANDARD").to(standardBearbeitungStep())
.on("*").to(fehlerBehandlungStep())        // greift, wenn keines oben passt

// FALSCH: "*" zuerst würde alles abfangen
.on("*").to(fehlerBehandlungStep())        // würde PREMIUM und STANDARD nie erreichen!
.on("PREMIUM").to(premiumRabattStep())
```

### Warum `.from(decider)` statt weiterer `.on()`-Ketten?

Nach dem ersten `.on(...).to(...)` befindet man sich im "Übergangs-Builder" für den Ziel-Step, nicht mehr für den Decider. `.from(decider)` kehrt explizit zum Decider zurück, um weitere Ausgänge zu definieren. Das ist das Spring-Batch-Fluent-API-Design.

---

## 6. Was ist der ExecutionContext?

Der `ExecutionContext` ist ein **Key-Value-Speicher**, der zum `JobExecution`- oder `StepExecution`-Objekt gehört. Er wird von Spring Batch in der Batch-Datenbank **persistiert** — das ist der entscheidende Unterschied zu einer einfachen Java-Map.

### Warum wird er persistiert?

Wenn ein Job neu gestartet wird (Restart nach Fehler), kann Spring Batch die Steps genau dort fortsetzen, wo sie aufgehört haben. Der ExecutionContext liefert den Zustand, den ein Step beim Neustart benötigt — zum Beispiel die Zeile in einer Datei, bis zu der bereits gelesen wurde.

### Unterstützte Datentypen

Der `ExecutionContext` speichert intern `Map<String, Object>`, aber für die Persistierung müssen die Werte **serialisierbar** sein. Die Convenience-Methoden verwenden primitive Typen:

```java
context.putString("schluessel", "wert");     // → getString()
context.putDouble("betrag", 89.99);           // → getDouble()
context.putLong("anzahl", 42L);              // → getLong()
context.putInt("seite", 1);                  // → getInt()
context.put("obj", einSerialisierbaresObj);  // → get() für beliebige Objekte
```

---

## 7. StepExecutionContext vs. JobExecutionContext

Das ist der häufigste Fehler beim Übergeben von Werten zwischen Steps:

| | **StepExecutionContext** | **JobExecutionContext** |
|---|---|---|
| **Lebensdauer** | Nur während des eigenen Steps | Gesamter Job-Lauf |
| **Sichtbarkeit** | Nur für den eigenen Step | Für alle Steps des Jobs |
| **Persistiert** | In `BATCH_STEP_EXECUTION_CONTEXT` | In `BATCH_JOB_EXECUTION_CONTEXT` |
| **Zugriff** | `contribution.getStepExecution().getExecutionContext()` | `chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()` |
| **Typischer Verwendungszweck** | Restart-State (z.B. "bis Zeile 247 gelesen") | Job-weite Daten (z.B. Bestellungs-ID für alle Steps) |

### Konkreter Code-Vergleich

```java
// StepExecutionContext: nur für diesen Step
ExecutionContext stepContext = contribution.getStepExecution().getExecutionContext();

// JobExecutionContext: job-weit sichtbar — Schlüssel für die Step-Übergabe!
ExecutionContext jobContext = chunkContext
    .getStepContext()
    .getStepExecution()
    .getJobExecution()        // ← dieser Sprung ist entscheidend
    .getExecutionContext();
```

**Merksatz**: Ein Schritt schreibt in den **JobExecutionContext**, wenn der nächste Schritt die Daten lesen soll. Der StepExecutionContext ist für interne Restart-Informationen gedacht.

---

## 8. Werteübergabe zwischen Steps — der Weg durch den JobExecutionContext

### Das Problem

Steps in Spring Batch sind **voneinander isoliert**. Es gibt keine direkte Methode, um von Step A nach Step B einen Wert zu übergeben. Die Lösung ist der gemeinsame `JobExecutionContext`.

### Das Muster

```
Step 1 schreibt:              Step 2 liest:
jobContext.put("key", val)  → jobContext.get("key")
```

Beide Steps holen sich denselben `JobExecution`-Kontext — über den gemeinsamen `jobExecution`-Verweis. Da alle Steps zum selben Job gehören, zeigen sie auf dieselbe `JobExecution`-Instanz.

### Im Code

**Schreiben (BestellungErfassenTasklet):**
```java
ExecutionContext jobContext = chunkContext
    .getStepContext()
    .getStepExecution()
    .getJobExecution()
    .getExecutionContext();

jobContext.putString("bestellungId", bestellungId);
jobContext.putDouble("betrag", betrag);
```

**Lesen (PremiumRabattTasklet, läuft danach):**
```java
ExecutionContext jobContext = chunkContext
    .getStepContext()
    .getStepExecution()
    .getJobExecution()
    .getExecutionContext();

String bestellungId = jobContext.getString("bestellungId");
double betrag       = jobContext.getDouble("betrag");
```

**Lesen im Decider** (läuft zwischen Schritt 1 und 2):
```java
@Override
public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
    // Der Decider bekommt jobExecution direkt als Parameter — kein Umweg über ChunkContext
    Double betrag = (Double) jobExecution.getExecutionContext().get("betrag");
    ...
}
```

### Werte "akkumulieren"

Ein elegantes Muster: Jeder Step kann neue Schlüssel **hinzufügen**, ohne die alten zu überschreiben. So baut sich im JobExecutionContext über den Job-Lauf hinweg ein vollständiges Bild auf:

```
Nach Schritt 1: { bestellungId, artikel, betrag }
Nach Schritt 2 (PREMIUM): { bestellungId, artikel, betrag, rabatt, endBetrag, verarbeitungsTyp }
Nach Schritt 3: liest alle → erstellt Zusammenfassung
```

---

## 9. Wann Decider, wann ExitStatus-Routing?

### ExitStatus-Routing verwenden (Tag2_03-Stil)

Gut geeignet, wenn:
- Die Entscheidung **direkt aus dem Ergebnis des Steps** folgt (z.B. "Step schlug fehl → Fehler-Pfad")
- Wenige, einfache Zweige (FAILED / COMPLETED)
- Keine separate Entscheidungslogik nötig

```java
// Step wirft Exception → ExitStatus FAILED → Fehler-Pfad
.flow(arbeitsStep)
.on("FAILED").to(fehlerbehandlungsStep)
.from(arbeitsStep).on("*").to(okStep)
```

### Decider verwenden (Tag2_08-Stil)

Gut geeignet, wenn:
- Die Entscheidung auf **Job-Parametern** basiert (unabhängig vom Step-Ergebnis)
- Die Entscheidungslogik **wiederverwendbar** oder **testbar** sein soll
- Es **mehr als zwei Ausgänge** gibt
- Die Logik **Datenbankabfragen** oder **externe Zustandsabfragen** benötigt
- Man **Separation of Concerns** bevorzugt (Step ≠ Routing-Logik)

### Entscheidungshilfe

```
Ist die Entscheidung das direkte Ergebnis des Steps?
  → JA: ExitStatus reicht aus
  → NEIN: JobExecutionDecider verwenden

Hat die Entscheidung mehr als 2-3 Ausgänge?
  → JA: Decider (übersichtlichere Logik)
  → NEIN: ExitStatus ausreichend

Soll die Routing-Logik isoliert getestet werden?
  → JA: Decider (ist ein eigener Spring-Bean)
  → NEIN: ExitStatus ist einfacher
```

---

## 10. Test-Strategie

### @SpringBatchTest

Die Annotation `@SpringBatchTest` registriert `JobLauncherTestUtils` und `JobRepositoryTestUtils` im Application Context. `JobLauncherTestUtils` kennt den einzigen Job und erlaubt ihn mit eigenen `JobParameters` zu starten.

### @MockitoBean JobRunner

Der `JobRunner` implementiert `CommandLineRunner`. Beim Hochfahren des Spring-Contexts im Test würde `CommandLineRunner.run()` aufgerufen — das würde den Job ein zweites Mal starten, unkontrolliert.

Die Lösung: `@MockitoBean JobRunner` ersetzt den echten Bean durch einen Mock. Der Mock tut beim `run()`-Aufruf nichts. Die Tests starten den Job dann selbst mit kontrollierten Parametern.

```java
@MockitoBean
private JobRunner jobRunner;  // verhindert automatische Ausführung beim Teststart
```

**Hinweis**: `@MockitoBean` ist seit Spring Boot 3.4 verfügbar und ersetzt das veraltete `@MockBean`.

### Was sollen Tests beim Decider prüfen?

1. **Pfad-Routing**: Welche Steps wurden ausgeführt? Welche nicht?
2. **ExecutionContext-Inhalte**: Stimmen die Werte im Job-Context?
3. **BatchStatus**: War der Job erfolgreich (`COMPLETED`)?

```java
// Prüfen ob ein bestimmter Step gelaufen ist
boolean gelaufen = execution.getStepExecutions().stream()
    .anyMatch(s -> s.getStepName().equals("premiumRabattStep"));

// ExecutionContext-Werte prüfen
ExecutionContext ctx = execution.getExecutionContext();
assertThat(ctx.getString("verarbeitungsTyp")).isEqualTo("PREMIUM");
assertThat(ctx.getDouble("endBetrag")).isEqualTo(71.99);
```

---

## 11. Datenflussbild (Übersicht)

```
Job-Parameter:   kundenTyp = "PREMIUM"
                      │
┌─────────────────────▼───────────────────────┐
│  bestellungErfassenStep                      │
│  schreibt in JobExecutionContext:            │
│    bestellungId = "abc-123-..."              │
│    artikel      = "Spring-Batch-Fachbuch"    │
│    betrag       = 89.99                      │
└─────────────────────┬───────────────────────┘
                      │
              KundenTypDecider
              liest: kundenTyp (Parameter) → "PREMIUM"
              liest: betrag (Context) → 89.99 > 0 ✓
              gibt zurück: FlowExecutionStatus("PREMIUM")
                      │
                .on("PREMIUM")
                      │
┌─────────────────────▼───────────────────────┐
│  premiumRabattStep                           │
│  liest  aus Context: bestellungId, betrag    │
│  schreibt in Context:                        │
│    rabatt           = 18.00                  │
│    endBetrag        = 71.99                  │
│    verarbeitungsTyp = "PREMIUM"              │
└─────────────────────┬───────────────────────┘
                      │
                 .on("*")
                      │
┌─────────────────────▼───────────────────────┐
│  abschlussQuittierungStep                    │
│  liest aus Context:                          │
│    bestellungId, artikel, betrag,            │
│    verarbeitungsTyp, rabatt, endBetrag       │
│  druckt vollständige Zusammenfassung         │
└─────────────────────────────────────────────┘

JobExecutionContext am Ende:
  bestellungId     = "abc-123-..." (von Schritt 1)
  artikel          = "Spring-Batch-Fachbuch" (von Schritt 1)
  betrag           = 89.99 (von Schritt 1)
  rabatt           = 18.00 (von Schritt 2a)
  endBetrag        = 71.99 (von Schritt 2a)
  verarbeitungsTyp = "PREMIUM" (von Schritt 2a)
```

---

## Vergleich mit Tag2_03

| | Tag2_03 (Conditional Flow) | Tag2_08 (Decider) |
|---|---|---|
| Steuerungsmechanismus | ExitStatus des Steps | FlowExecutionStatus des Deciders |
| Entscheidungsort | Im Step selbst | Im separaten Decider-Bean |
| Entscheidungsgrundlage | Ergebnis der Step-Ausführung | Job-Parameter + Context-Daten |
| Separation of Concerns | Gering (Step + Routing gemischt) | Hoch (Step und Routing getrennt) |
| Testbarkeit | Step-Test genügt | Decider kann isoliert getestet werden |
| Lesbarkeit bei vielen Ausgängen | Wird schnell unübersichtlich | Übersichtlich durch `from(decider)` |
| Werteübergabe | Kein explizites Konzept gezeigt | Zentrales Thema: JobExecutionContext |
