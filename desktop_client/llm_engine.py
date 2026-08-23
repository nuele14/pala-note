import re
import time
import json
import logging
from typing import Optional, Dict, Any, List, Tuple
import requests

from db import Database

logger = logging.getLogger("LLMEngine")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

DEFAULT_OLLAMA_URL = "http://localhost:11434"
DEFAULT_OLLAMA_MODEL = "qwen2.5:1.5b"


class LLMEngine:
    def __init__(
        self,
        backend: str = "ollama",  # 'ollama', 'llama_cpp', 'heuristic'
        model_name: str = DEFAULT_OLLAMA_MODEL,
        ollama_url: str = DEFAULT_OLLAMA_URL,
        db: Optional[Database] = None,
    ):
        self.backend = backend
        self.model_name = model_name
        self.ollama_url = ollama_url
        self.db = db or Database()

    def is_ollama_available(self) -> bool:
        try:
            r = requests.get(f"{self.ollama_url}/api/tags", timeout=1.5)
            return r.status_code == 200
        except Exception:
            return False

    def _call_ollama(self, system_prompt: str, user_text: str) -> str:
        prompt = f"System: {system_prompt}\n\nNota Vocale:\n\"{user_text}\"\n\nElaborazione richiesta:"
        payload = {
            "model": self.model_name,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.3,
                "top_p": 0.9,
            },
        }
        resp = requests.post(f"{self.ollama_url}/api/generate", json=payload, timeout=60.0)
        if resp.status_code == 200:
            return resp.json().get("response", "").strip()
        raise RuntimeError(f"Ollama error HTTP {resp.status_code}: {resp.text}")

    def _heuristic_fallback(self, tag: str, raw_text: str) -> str:
        """
        Fallback di formattazione euristica quando nessun motore LLM è attivo.
        Garantisce che la pipeline generi sempre un Markdown pulito e strutturato.
        """
        lines = [s.strip() for s in re.split(r"[.!?]\s+", raw_text) if s.strip()]
        title = lines[0] if lines else "Nuova Nota Vocale"
        if len(title) > 50:
            title = title[:47] + "..."

        if tag.lower() == "todo":
            md = f"# {title}\n\n### 📋 Task da completare\n"
            for line in lines:
                md += f"- [ ] {line.capitalize()}.\n"
            return md
        elif tag.lower() == "meeting":
            md = f"# {title}\n\n## 📝 Verbale Riunione\n"
            for line in lines:
                md += f"- {line.capitalize()}.\n"
            md += "\n## ✅ Action Items\n- [ ] Definire prossimi passi.\n"
            return md
        elif tag.lower() == "idea":
            md = f"# 💡 {title}\n\n## Concetto\n{raw_text}\n\n## Spunti\n- Valutare fattibilità ed implementazione.\n"
            return md
        else:
            md = f"# {title}\n\n"
            paragraphs = ["\n".join(lines[i:i+3]) for i in range(0, len(lines), 3)]
            md += "\n\n".join(paragraphs) if paragraphs else raw_text
            return md

    def elaborate_text(self, tag: str, raw_text: str, custom_system_prompt: Optional[str] = None) -> Tuple[str, str, str, int]:
        """
        Elabora il testo grezzo e produce: (title, content_markdown, model_used, duration_ms)
        """
        rule = self.db.get_tag_rule(tag)
        system_prompt = custom_system_prompt or rule.get("system_prompt", "")

        start_t = time.perf_counter()
        model_used = self.model_name

        if self.backend == "ollama" and self.is_ollama_available():
            try:
                logger.info(f"Elaborazione LLM con Ollama ({self.model_name})...")
                content_md = self._call_ollama(system_prompt, raw_text)
            except Exception as e:
                logger.warning(f"Chiamata Ollama fallita ({e}). Uso fallback euristico.")
                content_md = self._heuristic_fallback(tag, raw_text)
                model_used = "heuristic-rules"
        else:
            logger.info("Ollama non disponibile. Uso motore di formattazione euristico.")
            content_md = self._heuristic_fallback(tag, raw_text)
            model_used = "heuristic-rules"

        # Estrai o genera il titolo dal Markdown
        title = "Nota Vocale"
        for line in content_md.splitlines():
            line_str = line.strip()
            if line_str.startswith("# "):
                title = line_str[2:].strip()
                break

        duration_ms = int((time.perf_counter() - start_t) * 1000)
        return title, content_md, model_used, duration_ms

    def elaborate_note(self, note_id: str, custom_prompt: Optional[str] = None) -> Optional[str]:
        """
        Esegue la revisione intelligente della nota memorizzata nel DB.
        Ritorna l'elaboration_id creato.
        """
        note = self.db.get_note(note_id)
        if not note:
            logger.error(f"Nota {note_id} non trovata.")
            return None

        transcription = self.db.get_transcription(note_id)
        if not transcription or not transcription.get("raw_text"):
            logger.warning(f"Nessuna trascrizione disponibile per la nota {note_id}. Esegui prima STT.")
            return None

        tag = note.get("tag", "Untagged")
        raw_text = transcription["raw_text"]

        logger.info(f"Elaborazione nota #{note.get('device_note_num')} (Tag: {tag})...")
        title, content_md, model_used, duration_ms = self.elaborate_text(tag, raw_text, custom_prompt)

        rule = self.db.get_tag_rule(tag)
        elab_id = self.db.add_elaboration(
            note_id=note_id,
            title=title,
            content_markdown=content_md,
            llm_model=model_used,
            elaboration_type=rule.get("output_format", "markdown"),
            prompt_used=custom_prompt or rule.get("system_prompt"),
            duration_ms=duration_ms,
            is_favorite=True,
        )
        logger.info(f"Elaborazione completata in {duration_ms}ms (ID: {elab_id}, Titolo: '{title}')")
        return elab_id

    def process_all_pending(self) -> List[str]:
        """Elabora tutte le note trascritte che non hanno ancora un'elaborazione LLM"""
        pending = self.db.get_pending_elaborations()
        if not pending:
            logger.info("Nessuna nota in attesa di elaborazione LLM.")
            return []

        logger.info(f"Trovate {len(pending)} note da elaborare con LLM...")
        processed_ids = []
        for note in pending:
            eid = self.elaborate_note(note["id"])
            if eid:
                processed_ids.append(note["id"])
        return processed_ids
