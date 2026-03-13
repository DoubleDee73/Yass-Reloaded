# Fix Audio Assignment

## Goal
`YassSongList.assignAudioFiles()` soll Audio-Dateien deutlich robuster zu `#AUDIO`, `#INSTRUMENTAL` und `#VOCALS` zuordnen, ohne bestehende korrekte Tags kaputt zu machen.

Die neue Logik soll konservativ sein:
- harte Fehler automatisch beheben
- starke Heuristiken automatisch anwenden
- mehrdeutige F?lle nur als Hinweis oder mit manueller Auswahl behandeln

## Current Problems
Die aktuelle Implementierung in `YassSongList.assignAudioFiles()` und `handleAudioFiles()` hat mehrere strukturelle Schw?chen:

- Sie arbeitet fast nur mit einfachen Teilstring-Qualifiers aus Properties.
- Sie kennt keinen Song-Kontext wie `#TITLE`, `#ARTIST`, Dateiname der `.txt`, Solo/Duet oder mehrere Versionen pro Ordner.
- Sie entfernt Dateien fr?h aus der Kandidatenliste und verschlechtert dadurch sp?tere Entscheidungen.
- Sie behandelt eine gefundene Teilstring-?bereinstimmung oft schon als ausreichend sichere Zuordnung.
- Sie unterscheidet nicht sauber zwischen:
  - expliziter Vocal-Datei
  - Basis-Audio
  - expliziter Instrumental-Datei
  - Video-Container mit Audio wie `.webm`
- Sie hat keine Schutzregeln f?r bereits plausible bestehende Tags.

## Design Principles
Die neue Zuweisung soll auf folgenden Prinzipien basieren:

- Bestehende plausible Werte m?glichst nicht ?berschreiben.
- Nur starke Signale f?hren zu automatischen ?nderungen.
- Reihenfolge: erst validieren, dann scoren, dann nur sichere ?nderungen anwenden.
- `.txt`-Kontext ist Teil der Entscheidung, nicht nur der Ordnerinhalt.
- Bei mehreren `.txt` in einem Ordner wird jede Song-Version separat bewertet.
- Hinweise und Auto-Fixes werden getrennt modelliert.

## Scope
V1 dieser Verbesserung betrifft:
- `#AUDIO`
- `#INSTRUMENTAL`
- `#VOCALS`

Nicht Teil von V1:
- `#VIDEO`
- Netzwerkzugriffe
- MVSEP-spezifische API-Logik

## Domain Findings From Library Scan
Die bisherigen Pr?fungen ?ber die Song-Library legen folgende robuste Regeln nahe:

### AUDIO
- Wenn `#AUDIO` auf eine existierende `.webm` zeigt, ist das zul?ssig. Hintergrund: das kann eine in WebM containerisierte Opus-Datei sein.
- Wenn `#AUDIO` bereits auf eine Datei mit `vox` im Namen zeigt, soll das nicht automatisch ge?ndert werden.
- Wenn eine Datei exakt dem Muster `<ARTIST> - <TITLE>.<m4a|opus>` entspricht, ist sie ein starker Kandidat f?r `#AUDIO`.
- Dateien mit `Lead Vocals`, `Vocals`, `Vocal` sind eher Kandidaten f?r `#VOCALS`, nicht prim?r f?r `#AUDIO`.

### VOCALS
- Dateien mit `Lead Vocals`, `Vocals`, `Vocal` oder `vox` sind starke Kandidaten f?r `#VOCALS`.
- Fehlendes `#VOCALS` sollte nicht blind erg?nzt werden, wenn die Lage mehrdeutig ist.
- Existierendes `#VOCALS` soll nur ge?ndert werden, wenn die referenzierte Datei fehlt oder ein sehr starker Kandidat vorliegt.

### INSTRUMENTAL
- Wenn `#INSTRUMENTAL` bereits auf eine existierende Datei mit `instrumental` oder `inst` im Namen zeigt, soll das in der Regel unangetastet bleiben.
- Wenn `#INSTRUMENTAL` auf `<artist> - <title>.<ext>` zeigt, aber im Ordner zus?tzlich eine `vox`-Datei und eine explizite Instrumental-Datei existieren, ist das ein Hinweisfall.
- In so einem Fall nicht sofort automatisch ?ndern, sondern nur markieren.
- Sonderfall Duet/Solo:
  - `[DUET]` kann plausibel auf die reinere `inst`-Datei zeigen.
  - Solo-Version kann plausibel auf `inst with backing`, `with BV`, `backing track` etc. zeigen.
  - Diese Unterscheidung braucht Song-Kontext und darf nicht rein dateinamenbasiert passieren.

### Multiple TXT Files Per Folder
Wenn mehrere `.txt` in einem Ordner liegen, ist das oft beabsichtigt:
- Solo vs Duet
- Deutsch vs Englisch
- andere Edit-/Mix-Versionen

Deshalb darf die Zuordnung nicht nur auf Ordnerebene erfolgen. Der Titelkontext der konkreten `.txt` muss ber?cksichtigt werden:
- `#TITLE`
- `#ARTIST`
- `.txt`-Dateiname
- Duet-Indikatoren wie `[DUET]`, `DUETSINGERP1`, `DUETSINGERP2`

## Proposed Architecture
### 1. Split Detection From Mutation
Neue Struktur:
- `collectAudioCandidates(song, folder)`
- `evaluateAssignment(song, candidates)`
- `applyAssignment(song, decision, mode)`

`mode` sollte mindestens unterst?tzen:
- `SAFE_AUTO_FIX`
- `INTERACTIVE`
- optional sp?ter `REPORT_ONLY`

### 2. Candidate Model
Ein Kandidat sollte mehr Informationen tragen als nur den Dateinamen.

Beispiel:
```java
record AudioCandidate(
    String filename,
    String normalizedName,
    String extension,
    boolean exists,
    boolean audioLike,
    boolean webmAudioLike,
    int audioScore,
    int vocalsScore,
    int instrumentalScore,
    Set<AudioHint> hints
) {}
```

`AudioHint`-Beispiele:
- `VOX`
- `LEAD_VOCALS`
- `VOCALS`
- `INSTRUMENTAL`
- `INST`
- `BACKING`
- `WITH_BV`
- `PLAIN_ARTIST_TITLE`
- `WEBM`
- `M4A_PLAIN`
- `OPUS_PLAIN`
- `DUET`

### 3. Song Context Model
Zus?tzlich pro Song:
```java
record AudioAssignmentContext(
    String artist,
    String title,
    String txtFilename,
    boolean duet,
    List<String> normalizedTitleKeys
) {}
```

### 4. Decision Object
Die Entscheidung soll begr?nden, warum etwas ge?ndert oder nicht ge?ndert wird.

Beispiel:
```java
record AudioAssignmentDecision(
    String audioValue,
    String instrumentalValue,
    String vocalsValue,
    List<String> warnings,
    List<String> autoFixes,
    boolean changed
) {}
```

## Proposed Heuristic Pipeline
### Phase 1: Validate Existing Values
F?r jeden gesetzten Tag zuerst pr?fen:
- Tag leer?
- referenzierte Datei existiert?
- referenzierte Datei ist plausibel genug?

Regeln:
- `#AUDIO -> *.webm` und Datei existiert: g?ltig
- `#AUDIO` mit `vox` im Namen: g?ltig, nicht automatisch ?ndern
- `#INSTRUMENTAL` mit `instrumental` oder `inst` im Namen: g?ltig, nicht automatisch ?ndern
- `#VOCALS` nur dann angreifen, wenn Datei fehlt oder eindeutig unplausibel ist

### Phase 2: Build Candidate Scores
F?r jede Datei getrennte Scores berechnen:
- `audioScore`
- `vocalsScore`
- `instrumentalScore`

Score-Signale:
- exakter `<artist> - <title>`-Match
- Match zum `.txt`-Dateinamen
- `vox`
- `lead vocals`
- `vocals`
- `instrumental`
- `inst`
- `backing`
- `with bv`
- `karaoke`
- Dateiendung `.m4a`, `.opus`, `.webm`
- Duet-Hinweise

### Phase 3: Apply Stop Rules
Bevor etwas ge?ndert wird, Stop-Regeln pr?fen:
- existierender Wert ist plausibel genug -> nichts ?ndern
- mehrere nahezu gleich gute Kandidaten -> nicht automatisch ?ndern
- Basis-Audio als `#INSTRUMENTAL`, aber explizite Instrumental-Datei vorhanden -> Warnung statt Auto-Fix
- Duet/Solo-Konflikt -> Warnung oder interaktive Auswahl

### Phase 4: Safe Auto-Fixes
Nur diese F?lle automatisch ?ndern:
- gesetzte Datei fehlt und es gibt genau einen starken Ersatz
- Tag fehlt und es gibt genau einen starken Kandidaten
- `#AUDIO` zeigt auf klar falsche Datei, aber es existiert ein klar st?rkerer Kandidat

Nicht automatisch ?ndern:
- blo? bessere Geschmacksentscheidung
- Duet/Solo-Interpretation
- plain base track vs backing-instrumental, wenn beide plausibel sind

## Recommended Matching Rules
### AUDIO Safe Rules
Auto-Fix m?glich, wenn:
- bestehender Wert fehlt
- oder bestehender Wert weder `.webm` noch `vox` ist
- und ein Kandidat mit sehr starkem `audioScore` existiert

Starke positive Signale:
- `<artist> - <title>.m4a`
- `<artist> - <title>.opus`
- Titel-/Dateinamens-Match zur konkreten `.txt`

Starke negative Signale:
- `lead vocals`
- `vocals`
- `instrumental`
- `inst`
- `backing track`

### VOCALS Safe Rules
Auto-Fix nur wenn:
- bestehende Datei fehlt
- oder Tag fehlt
- und es genau einen starken Kandidaten mit `lead vocals`, `vocals`, `vocal` oder `vox` gibt

### INSTRUMENTAL Safe Rules
Auto-Fix nur wenn:
- bestehende Datei fehlt
- oder Tag fehlt
- und es genau einen starken Kandidaten mit `instrumental`, `inst`, `instrum-only`, `backing track` gibt

Nur Hinweis, kein Auto-Fix:
- `#INSTRUMENTAL` zeigt auf Basisdatei `<artist> - <title>.<ext>`
- zus?tzlich existieren `vox` und explizite Instrumental-Datei

## Proposed UI / UX Behavior
### Library Action
`assignAudioFiles()` sollte k?nftig zwei Modi unterst?tzen:
- `Safe auto-assign`
- `Review ambiguous assignments`

Empfehlung f?r V1:
- bestehender Men?punkt bleibt
- arbeitet standardm??ig als `Safe auto-assign`
- zeigt am Ende Summary:
  - Songs gepr?ft
  - Tags ge?ndert
  - Songs mit Warnungen

### Optional Follow-Up Dialog
Wenn es Warnf?lle gibt, optional kleiner Review-Dialog:
- Song
- aktueller Wert
- vorgeschlagener Kandidat
- Grund

Das w?re Phase 2, nicht zwingend V1.

## Proposed Refactoring Plan
1. Bestehende String-Qualifier-Logik nicht direkt erweitern, sondern durch Scoring-Modell abl?sen.
2. `handleAudioFiles()` in kleinere Testbare Methoden zerlegen.
3. Neue Helper-Klasse einf?hren, z. B. `AudioAssignmentHeuristics`.
4. `assignAudioFiles()` nur noch f?r Iteration, UI und Zusammenfassung verwenden.
5. Logging erg?nzen:
   - warum ein Kandidat gew?hlt wurde
   - warum ein bestehender Wert unangetastet blieb
   - warum ein Fall als mehrdeutig markiert wurde

## Tests
Mindestens folgende Testf?lle als Daten-getriebene Unit-Tests:

### AUDIO
- `#AUDIO` zeigt auf existierende `.webm` -> bleibt unver?ndert
- `#AUDIO` zeigt auf `vox.mp3` -> bleibt unver?ndert
- Basisdatei `.mp3` und plain `.m4a` vorhanden -> `.m4a` gewinnt
- mehrere `.txt` im Ordner -> passende Datei nach Titelkontext w?hlen

### VOCALS
- `Lead Vocals` vorhanden -> als `#VOCALS` erkannt
- `vox` vorhanden, `#VOCALS` fehlt -> nur setzen, wenn eindeutig
- `#VOCALS` zeigt auf fehlende Datei -> auf klaren Ersatz umstellen

### INSTRUMENTAL
- explizite `instrumental`-Datei vorhanden -> als `#INSTRUMENTAL` bevorzugen
- bestehendes `#INSTRUMENTAL` enth?lt `inst` -> nicht ?ndern
- Basisdatei als `#INSTRUMENTAL`, plus `vox` plus explizites instrumental -> nur Warnung
- Duet/Solo mit `inst` vs `with BV` -> nicht blind automatisch ?ndern

### Multi-Version Folder
- Englisch/Deutsch in einem Ordner
- Solo/Duet in einem Ordner
- `.txt`-Dateiname weicht vom Ordnernamen ab

## Migration Strategy
V1 sollte defensiv eingef?hrt werden:
- neue Heuristik hinter neuem Codepfad
- Logging auf `INFO` f?r tats?chliche ?nderungen
- `FINE` f?r Kandidatenscores
- alte Logik erst entfernen, wenn die Tests und eine Stichprobe ?ber echte Songs sauber laufen

## Recommendations
Meine Empfehlungen f?r die Implementierung sind:
- konservativ auto-fixen, aggressiv nur reporten
- `.txt`-Kontext unbedingt einbeziehen
- bestehende plausible Tags als Stop-Regel behandeln
- `#VOCALS` vorsichtiger behandeln als `#AUDIO`
- Duet/Solo zun?chst nur markieren, nicht automatisch umbiegen

## Decisions
Diese Punkte sind f?r V1 festgelegt:

1. `assignAudioFiles()` soll sichere Auto-Fixes schreiben und Warnf?lle nicht mehr einzeln dialogisch abfragen.
2. Fehlendes `#VOCALS` wird nur automatisch gesetzt, wenn der Kandidat explizit `lead vocals`, `vocals`, `vocal` oder `vox` enth?lt.
3. Wenn `#INSTRUMENTAL` auf der Basisdatei liegt, obwohl `vox` und eine explizite Instrumental-Datei existieren, wird das nur markiert, nicht automatisch ge?ndert.
4. Duet/Solo-Sonderf?lle erscheinen in V1 nur als Warnung.
5. Der erste Slice bekommt eine kleine Testbibliothek plus Stichprobe auf echten Songs.

## Warning Handling
F?r Markierf?lle ohne Auto-Fix soll die Logik strukturierte Warnungen erzeugen.

Empfehlung:
- `assignAudioFiles()` sammelt pro Song `warnings`
- am Ende kommt eine kompakte Summary im Stil von `X songs changed, Y songs with warnings`
- zus?tzlich Logging auf `INFO` pro Warnfall
- auf `FINE` kann die zugrunde liegende Kandidatenbewertung geloggt werden

F?r den speziellen `#INSTRUMENTAL`-Markierfall w?rde ich so vorgehen:
- Bedingung:
  - aktuelles `#INSTRUMENTAL` zeigt auf Basisdatei `<artist> - <title>.<ext>` oder passenden `.txt`-Basisnamen
  - im Ordner existiert zus?tzlich eine explizite Vocal-Datei (`vox` / `vocals`)
  - im Ordner existiert zus?tzlich eine explizite Instrumental-Datei (`inst` / `instrumental`)
- Verhalten:
  - kein Auto-Fix
  - Warning-Text in Summary/Log, z. B.:
    - `Instrumental looks ambiguous: base track is assigned, but an explicit instrumental file also exists`
- optional f?r Phase 2:
  - Review-Liste mit `current value`, `candidate`, `reason`

## Remaining Questions
Inhaltlich ist f?r V1 nichts Kritisches mehr offen.
Offen bleibt nur eine UI-Entscheidung f?r sp?ter:
- Soll es in Phase 2 einen kleinen Review-Dialog f?r Warnf?lle geben oder nur Logging/Summary?
Meine Empfehlung: erstmal nur Summary plus Logging.
