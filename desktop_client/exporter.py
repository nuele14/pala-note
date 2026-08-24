import re
import logging
from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List

from db import Database

logger = logging.getLogger("Exporter")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

DEFAULT_EXPORT_DIR = Path(__file__).parent / "exports"


class Exporter:
    def __init__(self, export_dir: Optional[Path] = None, db: Optional[Database] = None):
        self.export_dir = Path(export_dir or DEFAULT_EXPORT_DIR)
        self.export_dir.mkdir(parents=True, exist_ok=True)
        self.db = db or Database()

    @staticmethod
    def sanitize_filename(name: str) -> str:
        s = re.sub(r'[\\/*?:"<>|]', "", name).strip()
        s = re.sub(r"\s+", "_", s)
        return s[:60] if s else "nota"

    def format_markdown(self, note: Dict[str, Any], transcription: Optional[Dict[str, Any]], elaboration: Optional[Dict[str, Any]]) -> str:
        """
        Genera il contenuto Markdown completo con frontmatter YAML e dettagli espandibili.
        """
        created_utc = note.get("created_utc", "")
        tag = note.get("tag", "Untagged")
        duration_sec = note.get("duration_sec", 0.0)
        stt_model = transcription.get("stt_model", "N/A") if transcription else "N/A"
        llm_model = elaboration.get("llm_model", "N/A") if elaboration else "N/A"
        now_utc = datetime.now(timezone.utc).isoformat()

        content_md = elaboration.get("content_markdown", "") if elaboration else (transcription.get("raw_text", "") if transcription else "")

        yaml_header = (
            "---\n"
            f"id: \"{note.get('id')}\"\n"
            f"device_id: \"{note.get('device_id')}\"\n"
            f"device_note_num: {note.get('device_note_num')}\n"
            f"tag: \"{tag}\"\n"
            f"created_utc: \"{created_utc}\"\n"
            f"duration_sec: {duration_sec}\n"
            f"stt_model: \"{stt_model}\"\n"
            f"llm_model: \"{llm_model}\"\n"
            f"exported_utc: \"{now_utc}\"\n"
            "---\n\n"
        )

        full_doc = yaml_header + content_md

        # Se abbiamo sia elaborazione che trascrizione originale, aggiungiamo la sezione grezza espandibile
        if elaboration and transcription and transcription.get("raw_text"):
            raw = transcription["raw_text"]
            full_doc += (
                "\n\n---\n"
                "<details>\n"
                "<summary>🎙️ Trascrizione Grezza (Audio Originale)</summary>\n\n"
                f"{raw}\n"
                "</details>\n"
            )

        return full_doc

    def export_note_to_local(self, note_id: str) -> Optional[str]:
        """
        Esporta una nota in un file Markdown locale organizzato in sottocartelle per anno-mese e tag.
        """
        note = self.db.get_note(note_id)
        if not note:
            logger.error(f"Nota {note_id} non trovata.")
            return None

        transcription = self.db.get_transcription(note_id)
        elaboration = self.db.get_favorite_elaboration(note_id)

        created_str = note.get("created_utc", "")[:10]  # YYYY-MM-DD
        folder_date = created_str[:7] if len(created_str) >= 7 else "Varie"
        tag = note.get("tag", "Untagged")

        target_dir = self.export_dir / folder_date / tag
        target_dir.mkdir(parents=True, exist_ok=True)

        title = elaboration.get("title") if elaboration else f"Nota_{note.get('device_note_num')}"
        clean_title = self.sanitize_filename(title)
        filename = f"{created_str}_{clean_title}.md" if created_str else f"{clean_title}.md"
        file_path = target_dir / filename

        md_text = self.format_markdown(note, transcription, elaboration)

        with open(file_path, "w", encoding="utf-8") as f:
            f.write(md_text)

        logger.info(f"Nota esportata in locale: {file_path}")

        self.db.record_export(
            note_id=note_id,
            elaboration_id=elaboration.get("id") if elaboration else None,
            target="local_folder",
            local_file_path=str(file_path),
            status="COMPLETED",
        )
        return str(file_path)

    def export_all_unexported(self) -> List[str]:
        """Esporta tutte le note elaborate che non sono ancora state esportate"""
        with self.db.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
            SELECT n.id FROM notes n
            JOIN elaborations e ON n.id = e.note_id
            LEFT JOIN exports ex ON n.id = ex.note_id AND ex.status = 'COMPLETED'
            WHERE ex.id IS NULL
            GROUP BY n.id
            ORDER BY n.created_utc ASC
            """)
            note_ids = [r["id"] for r in cursor.fetchall()]

        logger.info(f"Trovate {len(note_ids)} note da esportare...")
        exported = []
        for nid in note_ids:
            p = self.export_note_to_local(nid)
            if p:
                exported.append(p)
        return exported
