import sqlite3
import json
import hashlib
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, List, Dict, Any, Tuple

DEFAULT_DB_PATH = Path(__file__).parent / "data" / "notes.db"

# ---------------------------------------------------------------------------
# Default Tag Rules & Prompts
# ---------------------------------------------------------------------------

DEFAULT_TAG_RULES = {
    "Todo": {
        "system_prompt": (
            "Sei un assistente personale esperto in organizzazione. "
            "Dato l'audio trascritto di una nota vocale taggata 'Todo', estrai le azioni da compiere in formato checklist Markdown (- [ ]).\n"
            "Regole:\n"
            "1. Crea un titolo breve ed esplicativo in cima (# Titolo).\n"
            "2. Elenca ogni task in modo chiaro, indicando eventuali scadenze, priorità o dettagli citati.\n"
            "3. Se ci sono note o contesto aggiuntivo, aggiungi una breve sezione 'Note di contesto'."
        ),
        "output_format": "todo_tasks",
        "target_drive_folder": "PalaNotes/Todo",
    },
    "Meeting": {
        "system_prompt": (
            "Sei un assistente esecutivo per verbali di riunione. "
            "Dato l'audio trascritto di una nota vocale taggata 'Meeting', struttura un verbale sintetico e professionale.\n"
            "Regole:\n"
            "1. # Titolo della Riunione / Argomento principale\n"
            "2. ## Sintesi Esecutiva (2-4 frasi riassuntive)\n"
            "3. ## Punti Chiave Discussi (elenco puntato)\n"
            "4. ## Decisioni Prese & Action Items (- [ ] Chi fa Cosa entro Quando)"
        ),
        "output_format": "meeting_notes",
        "target_drive_folder": "PalaNotes/Meeting",
    },
    "Idea": {
        "system_prompt": (
            "Sei un consulente di innovazione e brainstorming. "
            "Dato l'audio trascritto di una nota vocale taggata 'Idea', elabora e struttura il concetto espresso.\n"
            "Regole:\n"
            "1. # Titolo dell'Idea\n"
            "2. ## Concetto Chiave (cosa risolve o propone)\n"
            "3. ## Punti di Forza e Opportunità\n"
            "4. ## Domande Aperte e Possibili Sviluppi Futuri"
        ),
        "output_format": "idea_brief",
        "target_drive_folder": "PalaNotes/Idee",
    },
    "Work": {
        "system_prompt": (
            "Sei un assistente per la produttività lavorativa. "
            "Dato l'audio trascritto di una nota vocale lavorativa, organizza il contenuto in paragrafi chiari ed evidenzia compiti e scadenze."
        ),
        "output_format": "work_notes",
        "target_drive_folder": "PalaNotes/Lavoro",
    },
    "Untagged": {
        "system_prompt": (
            "Sei un editor di testo professionale. "
            "Dato l'audio trascritto di una nota vocale grezza, correggi la punteggiatura, rimuovi gli intercalari/esitazioni "
            "e organizza il testo in paragrafi leggibili ed eleganti, senza alterare il significato originale."
        ),
        "output_format": "clean_transcript",
        "target_drive_folder": "PalaNotes/Memo",
    },
    "Buy": {
        "system_prompt": (
            "Sei un assistente personale. Estrai gli articoli da acquistare o commissioni da fare come checklist Markdown (- [ ])."
        ),
        "output_format": "shopping_list",
        "target_drive_folder": "PalaNotes/Acquisti",
    },
    "Private": {
        "system_prompt": (
            "Sei un diario personale riservato. Ripulisci e formatta questa riflessione personale mantenendo il tono e le emozioni originarie."
        ),
        "output_format": "journal_entry",
        "target_drive_folder": "PalaNotes/Personale",
    },
}


class Database:
    def __init__(self, db_path: Optional[Path] = None):
        self.db_path = Path(db_path or DEFAULT_DB_PATH)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    def get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path))
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    def _init_schema(self):
        with self.get_connection() as conn:
            # 1. Devices
            conn.execute("""
            CREATE TABLE IF NOT EXISTS devices (
                device_id       TEXT PRIMARY KEY,
                name            TEXT NOT NULL,
                last_ip         TEXT,
                last_sync_at    DATETIME,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """)

            # 2. Notes
            conn.execute("""
            CREATE TABLE IF NOT EXISTS notes (
                id                  TEXT PRIMARY KEY,
                device_id           TEXT NOT NULL,
                device_note_num     INTEGER NOT NULL,
                dedupe_hash         TEXT UNIQUE NOT NULL,
                created_utc         TEXT NOT NULL,
                tag                 TEXT DEFAULT 'Untagged',
                duration_sec        REAL DEFAULT 0.0,
                audio_file_size     INTEGER DEFAULT 0,
                audio_local_path    TEXT,
                audio_sha256        TEXT,
                sync_status         TEXT CHECK(sync_status IN ('PENDING', 'DOWNLOADED', 'ACKED')) DEFAULT 'PENDING',
                created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
            );
            """)

            # 3. Transcriptions (STT)
            conn.execute("""
            CREATE TABLE IF NOT EXISTS transcriptions (
                id                  TEXT PRIMARY KEY,
                note_id             TEXT NOT NULL,
                raw_text            TEXT NOT NULL,
                segments_json       TEXT,
                stt_model           TEXT NOT NULL,
                duration_ms         INTEGER,
                created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
            );
            """)

            # 4. Elaborations (LLM Multi-versioning)
            conn.execute("""
            CREATE TABLE IF NOT EXISTS elaborations (
                id                  TEXT PRIMARY KEY,
                note_id             TEXT NOT NULL,
                version             INTEGER DEFAULT 1,
                type                TEXT NOT NULL,
                title               TEXT,
                content_markdown    TEXT NOT NULL,
                structured_json     TEXT,
                llm_model           TEXT NOT NULL,
                prompt_used         TEXT,
                duration_ms         INTEGER,
                is_favorite         INTEGER DEFAULT 1,
                created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
            );
            """)

            # 5. Exports
            conn.execute("""
            CREATE TABLE IF NOT EXISTS exports (
                id                  TEXT PRIMARY KEY,
                note_id             TEXT NOT NULL,
                elaboration_id      TEXT,
                target              TEXT DEFAULT 'google_drive',
                remote_file_id      TEXT,
                remote_url          TEXT,
                local_file_path     TEXT,
                status              TEXT CHECK(status IN ('PENDING', 'COMPLETED', 'FAILED')) DEFAULT 'PENDING',
                error_message       TEXT,
                exported_at         DATETIME,
                FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE,
                FOREIGN KEY(elaboration_id) REFERENCES elaborations(id) ON DELETE SET NULL
            );
            """)

            # 6. Tag Rules
            conn.execute("""
            CREATE TABLE IF NOT EXISTS tag_rules (
                tag                 TEXT PRIMARY KEY,
                system_prompt       TEXT NOT NULL,
                output_format       TEXT DEFAULT 'markdown',
                target_drive_folder TEXT,
                enabled             INTEGER DEFAULT 1
            );
            """)

            # 7. Full-Text Search (FTS5)
            conn.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
                note_id UNINDEXED,
                raw_text,
                content_markdown
            );
            """)

            # Indici
            conn.execute("CREATE INDEX IF NOT EXISTS idx_notes_dev_num ON notes(device_id, device_note_num);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_notes_tag ON notes(tag);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_notes_sync ON notes(sync_status);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_trans_note ON transcriptions(note_id);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_elab_note ON elaborations(note_id);")

            # Seed default tag rules if table is empty
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM tag_rules")
            if cursor.fetchone()[0] == 0:
                for tag, rule in DEFAULT_TAG_RULES.items():
                    cursor.execute("""
                    INSERT INTO tag_rules (tag, system_prompt, output_format, target_drive_folder)
                    VALUES (?, ?, ?, ?)
                    """, (tag, rule["system_prompt"], rule["output_format"], rule["target_drive_folder"]))
                conn.commit()

    # -----------------------------------------------------------------------
    # Devices
    # -----------------------------------------------------------------------
    def upsert_device(self, device_id: str, name: str, last_ip: Optional[str] = None):
        now = datetime.now(timezone.utc).isoformat()
        with self.get_connection() as conn:
            conn.execute("""
            INSERT INTO devices (device_id, name, last_ip, last_sync_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                name = excluded.name,
                last_ip = COALESCE(excluded.last_ip, devices.last_ip),
                last_sync_at = excluded.last_sync_at
            """, (device_id, name, last_ip, now))
            conn.commit()

    # -----------------------------------------------------------------------
    # Notes
    # -----------------------------------------------------------------------
    @staticmethod
    def compute_dedupe_hash(device_id: str, note_num: int, created_utc: str) -> str:
        data = f"{device_id}:{note_num}:{created_utc}".encode("utf-8")
        return hashlib.sha256(data).hexdigest()

    def upsert_note(
        self,
        device_id: str,
        device_note_num: int,
        created_utc: str,
        tag: str = "Untagged",
        duration_sec: float = 0.0,
        audio_file_size: int = 0,
        audio_local_path: Optional[str] = None,
        audio_sha256: Optional[str] = None,
        sync_status: str = "DOWNLOADED",
    ) -> Tuple[str, bool]:
        """
        Salva o aggiorna una nota. Ritorna (note_id, is_new).
        """
        dedupe_hash = self.compute_dedupe_hash(device_id, device_note_num, created_utc)
        note_id = str(uuid.uuid4())

        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM notes WHERE dedupe_hash = ?", (dedupe_hash,))
            existing = cursor.fetchone()

            if existing:
                existing_id = existing["id"]
                conn.execute("""
                UPDATE notes SET
                    tag = ?,
                    duration_sec = ?,
                    audio_file_size = ?,
                    audio_local_path = COALESCE(?, audio_local_path),
                    audio_sha256 = COALESCE(?, audio_sha256),
                    sync_status = ?
                WHERE id = ?
                """, (tag, duration_sec, audio_file_size, audio_local_path, audio_sha256, sync_status, existing_id))
                conn.commit()
                return existing_id, False
            else:
                conn.execute("""
                INSERT INTO notes (
                    id, device_id, device_note_num, dedupe_hash, created_utc, tag,
                    duration_sec, audio_file_size, audio_local_path, audio_sha256, sync_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    note_id, device_id, device_note_num, dedupe_hash, created_utc, tag,
                    duration_sec, audio_file_size, audio_local_path, audio_sha256, sync_status
                ))
                conn.commit()
                return note_id, True

    def get_note(self, note_id: str) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM notes WHERE id = ?", (note_id,))
            row = cursor.fetchone()
            return dict(row) if row else None

    def list_notes(self, tag: Optional[str] = None, limit: int = 50) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if tag and tag != "All":
                cursor.execute("""
                SELECT * FROM notes WHERE tag = ? ORDER BY created_utc DESC LIMIT ?
                """, (tag, limit))
            else:
                cursor.execute("SELECT * FROM notes ORDER BY created_utc DESC LIMIT ?", (limit,))
            return [dict(r) for r in cursor.fetchall()]

    def get_pending_transcriptions(self) -> List[Dict[str, Any]]:
        """Note scaricate che non hanno ancora una trascrizione STT"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
            SELECT n.* FROM notes n
            LEFT JOIN transcriptions t ON n.id = t.note_id
            WHERE t.id IS NULL AND n.audio_local_path IS NOT NULL
            ORDER BY n.created_utc ASC
            """)
            return [dict(r) for r in cursor.fetchall()]

    def get_pending_elaborations(self) -> List[Dict[str, Any]]:
        """Note trascritte che non hanno ancora almeno un'elaborazione LLM"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
            SELECT n.*, t.raw_text, t.segments_json, t.id as transcription_id
            FROM notes n
            JOIN transcriptions t ON n.id = t.note_id
            LEFT JOIN elaborations e ON n.id = e.note_id
            WHERE e.id IS NULL
            ORDER BY n.created_utc ASC
            """)
            return [dict(r) for r in cursor.fetchall()]

    # -----------------------------------------------------------------------
    # Transcriptions
    # -----------------------------------------------------------------------
    def add_transcription(
        self,
        note_id: str,
        raw_text: str,
        stt_model: str,
        segments: Optional[List[Dict[str, Any]]] = None,
        duration_ms: Optional[int] = None,
    ) -> str:
        trans_id = str(uuid.uuid4())
        segments_json = json.dumps(segments, ensure_ascii=False) if segments else None

        with self.get_connection() as conn:
            conn.execute("""
            INSERT INTO transcriptions (id, note_id, raw_text, segments_json, stt_model, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """, (trans_id, note_id, raw_text, segments_json, stt_model, duration_ms))

            # Aggiorna indice FTS5
            conn.execute("""
            INSERT INTO notes_fts (note_id, raw_text, content_markdown)
            VALUES (?, ?, '')
            """, (note_id, raw_text))

            conn.commit()
        return trans_id

    def get_transcription(self, note_id: str) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM transcriptions WHERE note_id = ? ORDER BY created_at DESC LIMIT 1", (note_id,))
            row = cursor.fetchone()
            if not row:
                return None
            res = dict(row)
            if res.get("segments_json"):
                res["segments"] = json.loads(res["segments_json"])
            return res

    # -----------------------------------------------------------------------
    # Elaborations (LLM)
    # -----------------------------------------------------------------------
    def add_elaboration(
        self,
        note_id: str,
        title: str,
        content_markdown: str,
        llm_model: str,
        elaboration_type: str = "summary",
        structured_data: Optional[Dict[str, Any]] = None,
        prompt_used: Optional[str] = None,
        duration_ms: Optional[int] = None,
        is_favorite: bool = True,
    ) -> str:
        elab_id = str(uuid.uuid4())
        structured_json = json.dumps(structured_data, ensure_ascii=False) if structured_data else None

        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COALESCE(MAX(version), 0) + 1 FROM elaborations WHERE note_id = ?", (note_id,))
            next_version = cursor.fetchone()[0]

            if is_favorite:
                conn.execute("UPDATE elaborations SET is_favorite = 0 WHERE note_id = ?", (note_id,))

            conn.execute("""
            INSERT INTO elaborations (
                id, note_id, version, type, title, content_markdown, structured_json,
                llm_model, prompt_used, duration_ms, is_favorite
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                elab_id, note_id, next_version, elaboration_type, title, content_markdown,
                structured_json, llm_model, prompt_used, duration_ms, 1 if is_favorite else 0
            ))

            # Aggiorna FTS5 con il testo elaborato
            conn.execute("""
            INSERT INTO notes_fts (note_id, raw_text, content_markdown)
            VALUES (?, '', ?)
            """, (note_id, content_markdown))

            conn.commit()
        return elab_id

    def get_elaborations(self, note_id: str) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM elaborations WHERE note_id = ? ORDER BY version DESC", (note_id,))
            return [dict(r) for r in cursor.fetchall()]

    def get_favorite_elaboration(self, note_id: str) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM elaborations WHERE note_id = ? AND is_favorite = 1 LIMIT 1", (note_id,))
            row = cursor.fetchone()
            if not row:
                cursor.execute("SELECT * FROM elaborations WHERE note_id = ? ORDER BY version DESC LIMIT 1", (note_id,))
                row = cursor.fetchone()
            return dict(row) if row else None

    # -----------------------------------------------------------------------
    # Exports
    # -----------------------------------------------------------------------
    def record_export(
        self,
        note_id: str,
        elaboration_id: Optional[str],
        target: str = "google_drive",
        remote_file_id: Optional[str] = None,
        remote_url: Optional[str] = None,
        local_file_path: Optional[str] = None,
        status: str = "COMPLETED",
        error_message: Optional[str] = None,
    ) -> str:
        export_id = str(uuid.uuid4())
        now = datetime.now(timezone.utc).isoformat()
        with self.get_connection() as conn:
            conn.execute("""
            INSERT INTO exports (
                id, note_id, elaboration_id, target, remote_file_id, remote_url,
                local_file_path, status, error_message, exported_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                export_id, note_id, elaboration_id, target, remote_file_id, remote_url,
                local_file_path, status, error_message, now if status == "COMPLETED" else None
            ))
            conn.commit()
        return export_id

    # -----------------------------------------------------------------------
    # Tag Rules
    # -----------------------------------------------------------------------
    def get_tag_rule(self, tag: str) -> Dict[str, Any]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM tag_rules WHERE tag = ?", (tag,))
            row = cursor.fetchone()
            if row:
                return dict(row)
            # Fallback su Untagged se il tag non ha una regola esplicita
            cursor.execute("SELECT * FROM tag_rules WHERE tag = 'Untagged'")
            fallback = cursor.fetchone()
            return dict(fallback) if fallback else DEFAULT_TAG_RULES["Untagged"]

    def list_tag_rules(self) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM tag_rules ORDER BY tag ASC")
            return [dict(r) for r in cursor.fetchall()]

    # -----------------------------------------------------------------------
    # Full-Text Search (FTS5)
    # -----------------------------------------------------------------------
    def search_notes(self, query: str, limit: int = 20) -> List[Dict[str, Any]]:
        """Ricerca istantanea su trascrizioni STT ed elaborazioni LLM"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
            SELECT DISTINCT n.*, 
                   COALESCE(e.title, 'Nota #' || n.device_note_num) as title,
                   t.raw_text,
                   e.content_markdown
            FROM notes_fts fts
            JOIN notes n ON fts.note_id = n.id
            LEFT JOIN transcriptions t ON n.id = t.note_id
            LEFT JOIN elaborations e ON n.id = e.note_id AND e.is_favorite = 1
            WHERE notes_fts MATCH ?
            ORDER BY rank
            LIMIT ?
            """, (query, limit))
            return [dict(r) for r in cursor.fetchall()]
