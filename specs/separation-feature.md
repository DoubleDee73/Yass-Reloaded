# Separation Feature

## Goal

Yass soll die Erstellung, Verfolgung und den Download von vocal-separierten Audiotracks ueber externe Dienste ermoeglichen, beginnend mit MVSEP.

Die Funktion soll:
- im Editor nutzbar sein
- optional auch in der Library nutzbar sein
- die erzeugten Dateien im Song-Ordner mit sinnvollen Namen ablegen
- heruntergeladene Dateien nach Moeglichkeit automatisch mit `#VOCALS` und `#INSTRUMENTAL` verknuepfen
- spaeter auf weitere externe Tools/Dienste erweiterbar bleiben

## Confirmed External Constraint

MVSEP bietet eine API an, darunter:
- Login via `POST /api/app/login`
- Job-Erstellung via `POST /api/separation/create`
- Statusabfrage via `GET /api/separation/get?hash=...`
- Queue/User/History-Endpunkte
- Cancel-Endpunkt

Quelle:
- [MVSEP User API](https://mvsep.com/user-api)

## Product Framing

### Recommended Direction

Empfehlung:
- In `YassOptions` einen neuen Hauptzweig `External Tools` zwischen `Wizard` und `Advanced` einfuehren.
- Darunter zunaechst ein Unterpanel `MVSEP` anlegen.
- `yt-dlp` mittelfristig thematisch ebenfalls unter `External Tools` einordnen.
- Die erste Version nur im Editor starten lassen.
- Library-Integration in Phase 2 ergaenzen.

Begruendung:
- MVSEP ist kein Editor-internes Audiofeature, sondern ein externer Cloud-Dienst.
- Ein allgemeiner `External Tools`-Knoten verhindert, dass spaetere Integrationen die Optionsstruktur weiter zerfasern.
- Editor-first reduziert Risiko, weil dort die Song-Zuordnung, Tags und Download-Ziele am eindeutigsten sind.

### Recommendation on Scope

Empfehlung fuer V1:
- Nur MVSEP als Provider
- Nur ein Song pro Job
- Trigger im Editor
- Kein Library-Trigger in V1
- Polling statt Webhooks
- Automatisches Verknuepfen nur dann, wenn `#VOCALS` bzw. `#INSTRUMENTAL` leer sind oder auf fehlende Dateien zeigen

Nicht fuer V1 empfohlen:
- Browser-Automation
- Batch-Queue fuer viele Songs
- Mehrere Provider gleichzeitig
- Automatisches Ueberschreiben existierender funktionierender Verknuepfungen

## UX Proposal

### Options

Neuer Baum in `YassOptions`:
- `Library`
- `Metadata`
- `Errors`
- `Editor`
- `Wizard`
- `External Tools`
- `Advanced`

Unter `External Tools`:
- `MVSEP`
- spaeter optional `yt-dlp`

### MVSEP Options Panel

Empfohlene Einstellungen:
- `MVSEP enabled`
- `API token`
- `Default separation model`
- `Default output format`
- `Poll interval`
- `Auto-download finished jobs`
- `Auto-link downloaded stems`
- `Filename scheme`

Empfehlungen zu einzelnen Feldern:
- Nur `API token` in V1
- Poll-Intervall default `10-15s`
- Output default verlustfrei oder projekttauglich, z. B. `flac` oder `wav`, falls MVSEP das fuer das Modell unterstuetzt

### Editor Trigger

Empfohlene Einstiegsorte im Editor:
- Menuepunkt `Extras -> Audio separieren`
- Menuepunkt ist deaktiviert, wenn kein API-Token hinterlegt ist

Empfohlener Dialogfluss:
1. `Create separated tracks`
2. Quelle bestaetigen: welche Datei hochgeladen wird
3. Modell und Output-Format koennen pro Job mit Defaults aus den Optionen ueberschrieben werden
4. Zielnamen anzeigen
5. Starten
6. Hintergrundstatus mit Fortschritt/Queue-Status
7. Nach Abschluss: Download + Auto-Link + Hinweis an User

### Library Trigger

Empfehlung:
- Nicht in V1 priorisieren
- Falls frueh gewuenscht, nur fuer einen selektierten Song
- Kein Multi-Select in der ersten Ausbaustufe

Begruendung:
- API-Limits, Queue-Zeiten und Fehlerszenarien sind bei Stapelverarbeitung deutlich komplexer
- Song-spezifische Dateiwahl und Konfliktbehandlung sind im Editor klarer

## File and Tagging Proposal

### Source Selection

Offene Produktfrage:
- Soll als Upload-Quelle primaer `#AUDIO` oder `#MP3` verwendet werden?

Meine Empfehlung:
- Ausschliesslich `#AUDIO`
- Nicht `#VOCALS` oder `#INSTRUMENTAL`
- Im Startdialog explizit anzeigen, welche Datei hochgeladen wird

Begruendung:
- `#AUDIO` repraesentiert in neueren Songs oft die fachlich richtige Arbeitsgrundlage.
- Wenn `#AUDIO` fehlt, ist die Aktion in V1 nicht verfuegbar oder fuehrt mit klarer Fehlermeldung ab.

### Downloaded Filenames

Empfehlung:
Gewuenschtes Schema fuer V1:
- `<basename> (vocals).<ext>`
- `<basename> (instrumental).<ext>`

Regel bei Dateikonflikten:
- Existierende Dateien werden nicht ueberschrieben
- Stattdessen wird eindeutig durchnummeriert, z. B.:
- `<basename> (vocals 2).<ext>`
- `<basename> (instrumental 2).<ext>`

Begruendung:
- fuer Menschen gut lesbar
- direkt im Song-Ordner erkennbar
- kollisionssicher ohne Rueckfrage

### Auto-Linking Rules

Empfehlung:
- Nach erfolgreichem Download `#INSTRUMENTAL` setzen, wenn leer oder defekt
- Nach erfolgreichem Download `#VOCALS` setzen, wenn leer oder defekt
- Existierende valide Eintraege nicht ueberschreiben
- Keine Rueckfrage in V1

Technische Anknuepfungspunkte im Code:
- Song/Library-Seite: [YassSongList.java](/C:/Yass/src/yass/YassSongList.java#L4652)
- Editor-Seite: [YassTable.java](/C:/Yass/src/yass/YassTable.java#L1557)

## Architecture Proposal

### Recommended Design

Neue Schichten:
- `yass.integration.separation.SeparationProvider`
- `yass.integration.separation.mvsep.MvsepClient`
- `yass.integration.separation.SeparationJob`
- `yass.integration.separation.SeparationService`
- `yass.integration.separation.SeparationDownloadResult`

Verantwortlichkeiten:
- `SeparationProvider`: Provider-abstrakte Operationen
- `MvsepClient`: konkrete HTTP-Kommunikation mit MVSEP
- `SeparationService`: Orchestrierung, Status, Retry, Download, Dateinamen, Verknuepfung
- `SeparationJob`: lokaler Jobstatus fuer UI und Polling

Empfehlung:
- Provider-Interface von Anfang an einfuehren, auch wenn zuerst nur MVSEP implementiert wird.

Warum:
- Harter Provider-Code im UI waere spaeter teurer aufzuloesen.

### State Model

Lokale Job-Zustaende in Yass:
- `NEW`
- `UPLOADING`
- `QUEUED`
- `PROCESSING`
- `DOWNLOADING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

MVSEP-Status auf lokale Stati abbilden:
- `waiting` -> `QUEUED`
- `processing` -> `PROCESSING`
- `done` -> `DOWNLOADING`/`COMPLETED`
- `failed` -> `FAILED`

### Background Execution

Empfehlung:
- asynchroner Hintergrundjob mit Swing-sicherem UI-Update
- Polling per Scheduled Executor oder bestehender Task-Infrastruktur
- Jobs nur fuer laufende Session im Speicher halten

Nicht fuer V1 empfohlen:
- persistente Job-Wiederaufnahme nach App-Neustart

## Security and Privacy

### Credentials

Empfehlung:
- API-Token bevorzugen
- Passwort moeglichst nicht dauerhaft speichern
- Falls Speicherung noetig: klar als weniger empfehlenswerte Option kennzeichnen

Offene Produktfrage:
- Duerfen Zugangsdaten unverschluesselt in `YassProperties` liegen, oder wollen wir mindestens Obfuscation/OS-Keychain spaeter vorsehen?

Meine Empfehlung:
- V1: API-Token in Properties nur mit ausdruecklicher Nutzerentscheidung
- Passwort nicht persistent speichern
- Spaeter optional OS-Keychain

### Data Upload Transparency

Empfehlung:
- Vor dem ersten Upload klar darauf hinweisen, dass Audiodateien an einen externen Dienst uebertragen werden
- Checkbox `I understand this uploads audio to MVSEP`

## Error Handling

Zu beruecksichtigen:
- fehlender API-Token / Login fehlgeschlagen
- Rate limiting / Credits / Queue voll
- Netzwerkfehler
- Download teilweise erfolgreich
- Ziel-Datei existiert bereits
- Song wurde waehrend des Jobs verschoben oder umbenannt
- Editor wurde geschlossen, waehrend der Job laeuft

Empfehlung fuer Overwrite-Policy:
- Default `Do not overwrite existing files; create numbered filename instead`

## Open Product Questions

Diese Punkte sollten wir vor Implementierungsstart entscheiden:

1. V1 startet nur im Editor.

2. Upload-Quelle ist ausschliesslich `#AUDIO`.

3. Bestehende `#VOCALS`/`#INSTRUMENTAL`-Eintraege werden nicht automatisch ersetzt.

4. Dateinamenskonvention in V1 ist `<basename> (vocals).<ext>` und `<basename> (instrumental).<ext>`.

5. V1 verwendet Polling.

6. In V1 laeuft nur ein Job gleichzeitig.

7. MVSEP-History-Import ist nicht Teil von V1.

8. Library-Integration kommt spaeter in Phase 2.

## Proposed MVP

### V1 Scope

- `External Tools > MVSEP`-Optionen
- MVSEP-Authentifizierung mit API-Token
- Editor-Aktion `Extras -> Audio separieren`
- Upload der in `#AUDIO` referenzierten Song-Datei an MVSEP
- Polling des Jobstatus
- Download von Vocals und Instrumental in den Song-Ordner
- Auto-Link auf `#VOCALS` und `#INSTRUMENTAL`, wenn leer oder defekt
- Statusdialog waehrend des Jobs sowie Abschlussmeldung nach Erfolg oder Fehler

### Phase 2

- Trigger aus der Library
- Batch-Verarbeitung mit Queue
- Erweiterte Konflikt-Dialoge fuer bestehende Dateien/Tags
- History-Ansicht
- Optionaler Cancel aus Yass heraus

### Phase 3

- weitere Provider
- persistente Job-Wiederaufnahme
- Credentials sicherer speichern
- fortgeschrittene Dateinamensregeln

## Implementation Roadmap

### Step 1: Spec decisions

- Scope fuer V1 festziehen
- Dateinamensschema festlegen
- Upload-Quelle auf `#AUDIO` festlegen
- Overwrite- und Auto-Link-Regeln festlegen

### Step 2: Options and properties

- neuen `External Tools`-Knoten in `YassOptions` einfuehren
- neues Panel `MvsepPanel` bauen
- Properties fuer MVSEP einfuehren, z. B.:
  - `mvsep-enabled`
  - `mvsep-api-token`
  - `mvsep-output-format`
  - `mvsep-model`
  - `mvsep-poll-interval`
  - `mvsep-auto-download`
  - `mvsep-auto-link`
### Step 3: MVSEP client

- HTTP-Client fuer Login / Create / Get / Cancel / Download
- JSON-Parsing fuer API-Responses
- Fehlerabbildung in Yass-interne Exceptions

### Step 4: Separation service

- Song-Kontext aufloesen
- `#AUDIO`-Datei bestimmen und validieren
- Ziel-Dateinamen mit Klammer-Schema und Durchnummerierung erzeugen
- Polling orchestrieren
- Downloads speichern
- Resultate validieren

### Step 5: Editor integration

- neue Action in `YassActions`
- Menueeintrag `Extras -> Audio separieren`
- Menueeintrag deaktivieren, wenn kein API-Token hinterlegt ist
- Dialog fuer Startoptionen; Modell und Output-Format koennen pro Job ueberschrieben werden
- Statusdialog fuer laufenden Job
- Erfolgsmeldung inklusive Verknuepfungsergebnis

### Step 6: Auto-linking

- Download-Dateien mit Song verknuepfen
- `YassTable` und/oder Song-Modell aktualisieren
- nur bei leerem/defektem Link automatisch setzen

### Step 7: Library integration

- optionaler Trigger fuer einzelne Songs
- spaeter Batch-Queue

### Step 8: Tests

- Unit-Tests fuer Dateinamenslogik
- Unit-Tests fuer Auswahl der Upload-Quelle
- Unit-Tests fuer Auto-Link-Entscheidungen
- Mock-Tests fuer MVSEP-Client

## Suggested Decisions For Now

Wenn wir jetzt ohne lange Blockade starten wollen, wuerde ich diese Entscheidungen empfehlen:

- `External Tools` als neuer Hauptzweig zwischen `Wizard` und `Advanced`
- darunter zuerst nur `MVSEP`
- `yt-dlp` vorerst noch nicht verschieben, nur die neue Struktur vorbereiten
- V1 nur im Editor
- Trigger nur ueber `Extras -> Audio separieren`
- Aktion ist deaktiviert, wenn kein API-Token hinterlegt ist
- Standard-Quelle: nur `#AUDIO`
- Standard-Dateinamen: `<basename> (vocals).<ext>` und `<basename> (instrumental).<ext>`
- Bei Konflikten nicht ueberschreiben, sondern durchnummerieren
- Polling statt Webhooks
- Auto-Link nur bei leerem oder kaputtem Link
- API-Token statt Passwort

## Questions For Brainstorming

Diese Fragen sind die naechsten guten Entscheidungen:

1. Wollen wir in Phase 2 den Library-Trigger fuer genau einen Song oder spaeter auch fuer Mehrfachauswahl?
2. Soll der Startdialog in V1 nur bestaetigen oder Modell und Format bereits pro Job ueberschreibbar machen?
3. Wie sichtbar soll der Hintergrundstatus im Editor sein: Statusleiste, Dialog oder beides?
4. Soll V1 einen Cancel-Button im UI haben oder nur passives Polling?
5. Soll das spaetere `External Tools`-Panel auch `yt-dlp` dorthin migrieren?



