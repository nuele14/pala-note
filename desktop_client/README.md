# Pala Note — Desktop Client & AI Host Pipeline

Client host locale per il registratore vocale **Pala Note (ESP32-S3)**.
Gestisce il download via **Wi-Fi SoftAP**, la memorizzazione in database relazionale **SQLite**, la sbobinatura tramite **OpenAI Whisper** (locale), la revisione intelligente con **LLM** (locale) e l'esportazione dei file **Markdown**.

---

### 📦 Installazione

```bash
cd desktop_client
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

---

### 🚀 Utilizzo

#### 1. Esecuzione Pipeline Completa
Metti il Pala Note in modalità **Sync** dal menu a schermo, connettiti al Wi-Fi `PalaNote-XXXX` dal tuo computer e lancia:

```bash
python orchestrator.py run
```

La pipeline eseguirà in sequenza:
1. **Sync**: Download differenziale dei file `.wav` e metadati da `192.168.4.1`.
2. **STT**: Trascrizione con Whisper locale (`whisper-base` o `whisper-small`).
3. **LLM**: Revisione e formattazione intelligente in base al Tag (`Todo`, `Meeting`, `Idea`, ecc.).
4. **Export**: Creazione dei file `.md` nella cartella `exports/YYYY-MM/Tag/`.

---

#### 2. Comandi Singoli

| Comando | Descrizione |
| :--- | :--- |
| `python orchestrator.py sync` | Esegue solo il download e l'invio degli ACK all'ESP32. |
| `python orchestrator.py transcribe` | Trascrive tutte le note scaricate che non hanno ancora STT. |
| `python orchestrator.py elaborate` | Esegue la revisione con LLM (Ollama o regole euristiche). |
| `python orchestrator.py export` | Genera i file Markdown formattati. |
| `python orchestrator.py list` | Elenca tutte le note nel database con stato di elaborazione. |
| `python orchestrator.py show <num_o_uuid>` | Mostra il dettaglio, l'audio, la trascrizione e la revisione. |
| `python orchestrator.py search "parola chiave"` | Esegue una ricerca Full-Text istantanea tra tutte le note. |

---

#### 3. Test Rapido senza Hardware (Mock Mode)

Puoi testare l'intera pipeline anche con un file audio `.wav` locale sul tuo PC:

```bash
python orchestrator.py mock-add /percorso/al/tuo/file.wav --tag Todo
python orchestrator.py transcribe
python orchestrator.py elaborate
python orchestrator.py export
```
