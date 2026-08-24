# 🛡️ ES1 — Extransformer Shield Uno
> **Local-First Minimalist Focus & Edge AI Companion (E-Paper ESP32-S3)**

[![Hardware](https://img.shields.io/badge/Hardware-ESP32--S3%20%7C%20E--Paper%201.54%22-blue.svg)](#hardware)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](NOTICE.md)
[![Edge AI](https://img.shields.io/badge/Edge%20AI-Whisper%20Int8%20%7C%20Ollama-orange.svg)](#edge-ai-pipeline)

---

## 📖 The Lore & Origins

### 🦆 1. Extransformer Shield Uno ("ES1")
Il nome **ES1** è un omaggio alla saga cult **PKNA (Paperinik New Adventures)**.
Nella lore di Pikappa, lo **Scudo Extransformer** è il capolavoro tecnologico creato dal geniale scienziato Everett Ducklair, equipaggiato con una miriade di funzioni modulari e collegato direttamente all'intelligenza artificiale **Uno** al 151° piano della Ducklair Tower.
Proprio come lo scudo di PK, **ES1** è un compagno tascabile, resistente, modulare e autonomo che porta a bordo intelligenza artificiale, produttività e benessere senza le distrazioni invasive del mondo moderno.

### ♟️ 2. Shikamaru Focus Timer
Il timer di produttività integrato è intitolato a **Shikamaru Nara (Naruto)**, il leggendario maestro della strategia con un QI superiore a 200.
Shikamaru incarna la filosofia di questo dispositivo: massima concentrazione e lucidità strategica nel momento dell'azione (*"Focus Mode"*), alternate al meritato riposo e alla contemplazione delle nuvole (*"Che seccatura... ma quando serve, do il massimo"*).

---

## ⚡ Caratteristiche Principali

* **🎙️ Registratore Vocale Ultra-Rapido**: Premi `REC` e registra note istantanee salvate su MicroSD in WAV 16kHz non compresso.
* **⏱️ Timer "Shikamaru" Integrato**: Modalità Pomodoro autonoma sul display (25 min Focus / 5 min Pausa) con conteggio delle sessioni e jingle sonoro rilassante.
* **🖼️ Screensaver E-Paper a Consumo Zero (0.0 µA)**: Durante l'inattività, il display mostra a rotazione citazioni motivazionali e grafiche 1-bit caricate dalla cartella `/screensavers/` su MicroSD.
* **📡 Sincronizzazione SoftAP Locale (`ES1-XXXX`)**: Connessione Wi-Fi diretta tra Mac/PC/Smartphone e dispositivo (`192.168.4.1`) a zero dipendenza cloud.
* **🧠 Pipeline Edge AI Locale**:
  * **STT**: Sbobinatura ultra-veloce con `faster-whisper` (modello int8, ~350ms per nota).
  * **LLM**: Revisione intelligente basata sui tag (*Todo, Meeting, Idea, Work, Buy, Private, Note*) con `Ollama` (`qwen2.5:1.5b`) o fallback euristico.
  * **FTS5 Search & Export**: Ricerca full-text istantanea ed esportazione automatica in Markdown con frontmatter YAML.
* **🎨 Material 3 Desktop GUI**: Interfaccia grafica fluida sviluppata in Flet, con player audio nativo e gestione prompt.

---

## 🏗️ Architettura del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                 ES1 (ESP32-S3 E-Paper Device)               │
│   - Voice Notes Recorder (/notes/*.wav)                     │
│   - Shikamaru Focus Timer (25m Focus / 5m Rest)             │
│   - Minimalist Screensavers (/screensavers/*.bin)           │
│   - SoftAP Server ("ES1-XXXX" @ 192.168.4.1)               │
└──────────────────────────────┬──────────────────────────────┘
                               │  Wi-Fi Sync (HTTP REST)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│         DESKTOP HOST & ANDROID CLIENT (Local-First)         │
│   - Sync Client (Differential download + ACK + SHA256)      │
│   - STT Engine (faster-whisper 16kHz Int8)                  │
│   - LLM Engine (Ollama / Local Rules Engine)                │
│   - SQLite DB (FTS5 Full-Text Search)                       │
│   - Markdown Exporter (YAML Frontmatter + Checklists)       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Guida Rapida

### 1. Avvio della Desktop GUI (Flet)

```bash
cd desktop_client
source .venv/bin/activate
python app_gui.py
```

### 2. Conversione Immagini per Screensaver E-Paper (200x200 1-bit)

Puoi convertire qualsiasi immagine (`.png`, `.jpg`, `.bmp`, `.webp`) nel formato nativo per il display:

```bash
# Converti una singola immagine
python tools/img_to_screensaver.py mia_immagine.png

# Converti e copia direttamente sulla MicroSD montata
python tools/img_to_screensaver.py wallpaper.jpg --sd /Volumes/UNONOTE/screensavers

# Genera automaticamente 4 screensaver con citazioni minimaliste
python tools/img_to_screensaver.py --samples
```

### 3. Utilizzo del Timer "Shikamaru" sul Dispositivo

1. Dal menu principale di **ES1**, seleziona **"Shikamaru"**.
2. **Click `REC`**: Avvia / Mette in pausa il conto alla rovescia.
3. **Pressione Lunga `REC`**: Salta alla fase successiva (Focus ➔ Pausa).
4. **Click `PWR`**: Ritorna al menu principale.
5. Al termine dei 25 min (o 5 min di pausa), verrà riprodotto un jingle armonico rilassante (o il file personalizzato `/sounds/shikamaru.wav` se presente su MicroSD).

---

## 📂 Struttura del Repository

| Cartella / File | Descrizione |
|---|---|
| [`firmware/pala_note/`](firmware/pala_note) | Firmware ESP32-S3 in C++/Arduino per ES1 (E-Paper, Audio ES8311, SoftAP, Shikamaru). |
| [`desktop_client/`](desktop_client) | Host locale Python (Sync, STT Whisper, LLM, DB SQLite, GUI Flet). |
| [`desktop_client/tools/`](desktop_client/tools) | Tool CLI per conversione screensaver e utilità di gestione. |
| [`hardware/`](hardware) | File 3D e case per il dispositivo (STEP + 3MF). |
| [`docs/`](docs) | Specifiche architetturali e manuali di sistema. |

---

## 📄 Licenza

Software rilasciato sotto licenza MIT. I componenti firmware e hardware di terze parti seguono i rispettivi termini indicati in [NOTICE.md](NOTICE.md).
