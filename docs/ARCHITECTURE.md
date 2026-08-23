# 📐 Documento di Architettura di Sistema
## Progetto: Pala Note — Local-First & Edge AI Pipeline

---

### 1. Visione e Obiettivi del Progetto

Il progetto **Pala Note** viene riprogettato secondo il paradigma **Local-First & Edge AI**. 
L'obiettivo è svincolarsi da qualsiasi infrastruttura cloud a pagamento o server proprietario, garantendo:

* **Massima Privacy**: Nessun dato vocale o testo non crittografato transita su server terzi per l'elaborazione.
* **Costi Operativi Nulli**: Eliminazione totale dei costi per token API (OpenAI Whisper / LLM) e database cloud (Neon/Vercel).
* **Autonomia Energetica dell'Hardware**: L'ESP32-S3 esegue solo la registrazione audio e va immediatamente in *Deep Sleep*, senza connessioni Wi-Fi o chiamate TLS ad ogni nota.
* **Funzionamento 100% Offline**: Registrazione, trascrizione e revisione funzionano ovunque senza connessione a Internet.
* **Archiviazione Personale**: I risultati finali elaborati vengono salvati nel database locale e opzionalmente sincronizzati su **Google Drive** personale dell'utente (o altri cloud storage).

---

### 2. Architettura ad Alto Livello

```
┌─────────────────────────────────────────────────────────────┐
│                    ESP32-S3 HARDWARE                        │
│  - Microfono I2S + Codec Audio                              │
│  - Storage SD Card (WAV 16kHz PCM + Metadati .meta)         │
│  - Display E-Paper (UI, Tag selector, stato note)           │
│  - Server HTTP locale su Wi-Fi SoftAP (Modalità Sync)       │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ Sincronizzazione Manuale Wi-Fi SoftAP
                               │ (HTTP GET /api/notes, /api/notes/{id}/audio)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│             HOST CLIENT (Desktop PC ➔ Android App)          │
│                                                             │
│  1. SYNC MANAGER                                            │
│     - Download differenziale dei file WAV                   │
│     - Controllo integrità SHA256                            │
│     - Invio ACK all'ESP32 per conferma salvataggio          │
│                                                             │
│  2. DATABASE RELAZIONALE LOCALE (SQLite / Room)             │
│     - Tabella 'notes' (Metadati, stati di avanzamento)      │
│     - Tabella 'transcriptions' (Testo STT + timestamp)      │
│     - Tabella 'elaborations' (Versioni multiple LLM)        │
│     - Indice Full-Text Search (FTS5)                        │
│                                                             │
│  3. MOTORE STT LOCALE (Speech-to-Text)                      │
│     - Whisper.cpp / Sherpa-ONNX (Whisper Base/Small Q8)     │
│                                                             │
│  4. MOTORE LLM LOCALE (Revisione & Task Extraction)         │
│     - Llama.cpp / MediaPipe GenAI (Qwen 2.5 / Llama 3.2)    │
│     - Prompt specializzati per Tag (Todo, Meeting, Idea...) │
│                                                             │
│  5. CLOUD EXPORTER                                          │
│     - Google Drive REST API (Upload Markdown con Frontmatter)│
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ Upload automatico o programmato
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   GOOGLE DRIVE PERSONALE                    │
│  - Cartella: "PalaNotes/YYYY-MM/[Tag] Titolo_Nota.md"        │
└─────────────────────────────────────────────────────────────┘
```

---

### 3. Decisioni Architetturali Chiave

#### A. Canale di Comunicazione: Wi-Fi SoftAP vs BLE
* **Volume Dati**: Il formato audio è WAV PCM 16kHz 16-bit Mono = **31,25 KB/s** (~1,9 MB per minuto di parlato). Un blocco di 5-10 minuti pesa 10-20 MB.
* **Scelta Adottata**: **Wi-Fi SoftAP (ESP32 come Hotspot HTTP Server)**.
  * *Motivazione*: Throughput di **1,5 - 3,5 MB/s**, completando il download di 10 MB in 3-5 secondi contro i 3-5 minuti necessari via BLE.
  * *Evoluzione Futura (Ibrido)*: In una fase successiva, BLE potrà essere aggiunto solo come canale leggero di sveglia e handshake per avviare il SoftAP senza interazione manuale complessa.

#### B. Gestione Dati: Database Relazionale 1-a-Molti vs File System
* **Scelta Adottata**: **Database SQLite con relazione 1-a-Molti** per metadati, trascrizioni ed elaborazioni multiple, mantenendo solo i file binari pesanti (WAV) su disco.
* **Vantaggi Rispetto ai File Separati su Disco**:
  * **Transazionalità ACID**: Nessun rischio di file orfani o disallineati se il processo si interrompe.
  * **Supporto a Note Lunghe & Segmentate**: Possibilità di salvare timestamp per ogni frase (`segments_json`).
  * **Versionamento Multiplo (1 Nota ➔ N Elaborazioni)**: Possibilità di rieseguire l'LLM con prompt o modelli diversi senza sovrascrivere o generare confusione di file.
  * **Ricerca Full-Text (FTS5)**: Ricerca istantanea su tutto lo storico delle note.

---

### 4. Specifiche del Protocollo HTTP (ESP32 SoftAP)

Quando l'utente attiva la modalità **"Sync"** sull'ESP32:
* L'ESP32 avvia l'AP: SSID `PalaNote-XXXX`, IP `192.168.4.1`.
* Espone le seguenti API REST:

| Metodo | Endpoint | Descrizione | Risposta |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/info` | Info dispositivo e batteria | `{"device_id": "ESP32-A1B2", "firmware_version": "v1.3", "battery_percent": 85, "total_notes": 10, "pending_notes": 3}` |
| `GET` | `/api/notes` | Elenco metadati di tutte le note | `[{"num": 1, "tag": "Todo", "created_utc": "2026-08-20T10:00:00Z", "duration_sec": 12.5, "file_size": 400044, "synced": false}]` |
| `GET` | `/api/notes/audio?num=X` | Download stream del file WAV | Stream binario `audio/wav` |
| `POST` | `/api/notes/ack?num=X` | Conferma sincronizzazione avvenuta | `{"status": "ok", "num": X, "synced": true}` (l'ESP32 marca la nota come `synced=true` su SD) |
| `POST` | `/api/sync/done` | Chiusura sessione sync | `{"status": "done"}` (l'ESP32 mostra completamento, spegne il Wi-Fi e torna in Idle/Sleep) |
| `GET` | `/` | Portale Web HTML/CSS locale | Interfaccia grafica completa per ascoltare e scaricare da browser |

---

### 5. Schema del Database SQLite (DDL)

```sql
-- Dispositivi hardware registrati
CREATE TABLE IF NOT EXISTS devices (
    device_id       TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    last_ip         TEXT,
    last_sync_at    DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Note e metadati hardware
CREATE TABLE IF NOT EXISTS notes (
    id                  TEXT PRIMARY KEY,       -- UUID generato dal client
    device_id           TEXT NOT NULL,
    device_note_num     INTEGER NOT NULL,
    dedupe_hash         TEXT UNIQUE NOT NULL,   -- SHA256(device_id + note_num + created_utc)
    created_utc         TEXT NOT NULL,
    tag                 TEXT DEFAULT 'Untagged',
    duration_sec        REAL DEFAULT 0.0,
    audio_file_size     INTEGER DEFAULT 0,
    audio_local_path    TEXT,                   -- Percorso locale del WAV
    audio_sha256        TEXT,                   -- Checksum del file audio
    sync_status         TEXT CHECK(sync_status IN ('PENDING', 'DOWNLOADED', 'ACKED')) DEFAULT 'PENDING',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
);

-- Trascrizioni STT (Supporto segmenti timestamped)
CREATE TABLE IF NOT EXISTS transcriptions (
    id                  TEXT PRIMARY KEY,
    note_id             TEXT NOT NULL,
    raw_text            TEXT NOT NULL,
    segments_json       TEXT,                   -- [{"start": 0.0, "end": 2.5, "text": "..."}]
    stt_model           TEXT NOT NULL,          -- es. "whisper-base-q8"
    duration_ms         INTEGER,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
);

-- Elaborazioni multiple LLM (Versioning)
CREATE TABLE IF NOT EXISTS elaborations (
    id                  TEXT PRIMARY KEY,
    note_id             TEXT NOT NULL,
    version             INTEGER DEFAULT 1,
    type                TEXT NOT NULL,          -- 'summary', 'todo_tasks', 'meeting_notes', 'clean_transcript'
    title               TEXT,
    content_markdown    TEXT NOT NULL,          -- Testo formattato
    structured_json     TEXT,                   -- Eventuali dati strutturati estratti (es. lista task)
    llm_model           TEXT NOT NULL,          -- es. "qwen2.5-1.5b-q4_k_m"
    prompt_used         TEXT,
    duration_ms         INTEGER,
    is_favorite         INTEGER DEFAULT 1,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
);

-- Esportazioni Cloud (Google Drive / Altri)
CREATE TABLE IF NOT EXISTS exports (
    id                  TEXT PRIMARY KEY,
    note_id             TEXT NOT NULL,
    elaboration_id      TEXT,
    target              TEXT DEFAULT 'google_drive',
    remote_file_id      TEXT,                   -- ID file Google Drive
    remote_url          TEXT,                   -- URL visualizzazione
    status              TEXT CHECK(status IN ('PENDING', 'COMPLETED', 'FAILED')) DEFAULT 'PENDING',
    error_message       TEXT,
    exported_at         DATETIME,
    FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE,
    FOREIGN KEY(elaboration_id) REFERENCES elaborations(id) ON DELETE SET NULL
);

-- Tabella di ricerca Full-Text su trascrizioni ed elaborazioni
CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
    note_id UNINDEXED,
    raw_text,
    content_markdown
);

-- Regole e Prompt associati ai Tag
CREATE TABLE IF NOT EXISTS tag_rules (
    tag                 TEXT PRIMARY KEY,
    system_prompt       TEXT NOT NULL,
    output_format       TEXT DEFAULT 'markdown',
    target_drive_folder TEXT,
    enabled             INTEGER DEFAULT 1
);
```

---

### 6. Pipeline dei Modelli AI On-Device

| Task | Motore (Desktop PC) | Motore (Android) | Modello Consigliato | Dimensione Modello |
| :--- | :--- | :--- | :--- | :--- |
| **STT (Sbobinatura)** | `faster-whisper` / `whisper.cpp` | `Sherpa-ONNX` / `whisper.cpp (NDK)` | Whisper Base / Small (Quant. Int8) | ~75 MB - 150 MB |
| **LLM (Revisione/Task)** | `llama-cpp-python` / `ollama` | `Llama.cpp Android` / `MediaPipe GenAI` | Qwen 2.5 1.5B Instruct / Llama 3.2 1B (Q4_K_M) | ~800 MB - 1.1 GB |

---

### 7. Registro delle Modifiche al Firmware (Changelog Implementazione)

Di seguito il dettaglio dei componenti modificati sul firmware ESP32-S3 (`pala_note`):

1. **`src/app/network.h` & `src/app/network.cpp`**:
   * Rimossa tutta la logica di trascrizione OpenAI e l'upload TLS verso Pala Cloud.
   * Eliminata la dipendenza da `ca_certs.h` e `WiFiClientSecure`.
   * Implementata la funzione `startSoftApSync()` che attiva l'hotspot `PalaNote-XXXX` con IP statico `192.168.4.1`.
   * Implementati gli endpoint REST `/api/info`, `/api/notes`, `/api/notes/audio`, `/api/notes/ack`, `/api/sync/done`.
   * Mantenuto il portale Web HTML (`/`, `/tags`, `/export.txt`, ecc.) per uso manuale da browser.

2. **`src/app/notes.h` & `src/app/notes.cpp`**:
   * Aggiunta la funzione `pendingSyncCount()` per calcolare istantaneamente quante note non sono ancora state marcate come sincronizzate.
   * Aggiunta la funzione `noteAudioFileSize(int num)` per calcolare la dimensione reale in byte dei file WAV su scheda SD.
   * Aggiunta la funzione `noteAudioDurationSec(int num)` per convertire i byte audio in secondi effettivi di parlato a 16kHz 16-bit mono.

3. **`src/app/ui.h` & `src/app/ui.cpp`**:
   * Aggiunta la schermata grafica `showSyncMode(ssid, ip, pending)` per il display e-paper.

4. **`pala_note.ino`**:
   * Rimosso il prompt post-registrazione che forzava la connessione a internet (`STATE_SYNC_CONFIRM`). La registrazione ora salva direttamente su SD con il tag scelto e torna in Idle/Sleep.
   * La voce **"Sync"** del menu avvia `startSyncFlow()`, che attiva il SoftAP e attende la sincronizzazione dal client.
   * Al termine del trasferimento (chiamata `/api/sync/done` o doppio clic di uscita), l'ESP32 spegne automaticamente il Wi-Fi e torna in Idle.

---

### 8. Tabella di Marcia per lo Sviluppo (Roadmap)

1. **Fase 1 (Firmware ESP32)**: ✅ *Completata*
   - Eliminazione chiamate ad API esterne da `network.cpp`.
   - Implementazione modulo server SoftAP ed endpoint REST `/api/notes`.
2. **Fase 2 (Core & Prototipo PC in Python - `desktop_client/`)**: ✅ *Completata*
   - Modulo database SQLite relazionale 1-a-molti + FTS5 (`db.py`).
   - Modulo Sync Client Wi-Fi SoftAP con download differenziale (`sync_client.py`).
   - Modulo STT Whisper locale con segmenti timestamped (`stt_engine.py`).
   - Modulo LLM locale per revisione e prompt per tag (`llm_engine.py`).
   - Modulo esportazione Google Drive / Markdown strutturato (`exporter.py`).
   - Orchestratore CLI con comandi `sync`, `transcribe`, `elaborate`, `export`, `list`, `show`, `search`, `mock-add` (`orchestrator.py`).
3. **Fase 3 (Porting su Mobile Android)**: ⏳ *Prossimo Step*
   - Trasposizione della logica in Kotlin (Room DB, Sherpa-ONNX STT, Llama.cpp LLM, Google Drive API).
   - Realizzazione dell'interfaccia utente in Jetpack Compose.
