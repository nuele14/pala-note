import time
import logging
from pathlib import Path
from typing import Optional, List, Dict, Any, Tuple

from db import Database

logger = logging.getLogger("STTEngine")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")


class STTEngine:
    def __init__(
        self,
        model_size: str = "base",
        device: str = "auto",
        compute_type: str = "int8",
        language: str = "it",
        db: Optional[Database] = None,
    ):
        """
        Motore Speech-to-Text locale basato su faster-whisper.
        model_size: 'tiny', 'base', 'small', 'medium'
        compute_type: 'int8', 'float16', 'float32'
        """
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self.language = language
        self.db = db or Database()
        self._model = None

    def _load_model(self):
        if self._model is not None:
            return self._model

        logger.info(f"Caricamento modello Whisper '{self.model_size}' (compute_type={self.compute_type})...")
        try:
            from faster_whisper import WhisperModel
            self._model = WhisperModel(
                self.model_size,
                device=self.device if self.device != "auto" else "cpu",
                compute_type=self.compute_type,
            )
            logger.info("Modello Whisper caricato con successo.")
            return self._model
        except ImportError:
            logger.warning("Libreria 'faster-whisper' non trovata. Tentativo di fallback su 'whisper' standard...")
            try:
                import whisper
                self._model = ("openai_whisper", whisper.load_model(self.model_size))
                logger.info("Modello OpenAI-Whisper standard caricato.")
                return self._model
            except ImportError:
                logger.error("Nessun backend Whisper installato. Installa con: pip install faster-whisper")
                return None

    def transcribe_file(self, audio_path: str) -> Tuple[str, List[Dict[str, Any]], int]:
        """
        Trascrive un file audio locale WAV.
        Ritorna: (raw_text, segments_list, duration_ms)
        """
        p = Path(audio_path)
        if not p.exists():
            raise FileNotFoundError(f"File audio non trovato: {audio_path}")

        model = self._load_model()
        if model is None:
            raise RuntimeError("Motore STT non inizializzato. Installa faster-whisper.")

        start_t = time.perf_counter()
        segments_out = []
        full_text_parts = []

        if isinstance(model, tuple) and model[0] == "openai_whisper":
            # Fallback standard whisper
            raw_model = model[1]
            result = raw_model.transcribe(str(p), language=self.language)
            full_text = result.get("text", "").strip()
            for s in result.get("segments", []):
                segments_out.append({
                    "id": s.get("id"),
                    "start": round(s.get("start", 0.0), 2),
                    "end": round(s.get("end", 0.0), 2),
                    "text": s.get("text", "").strip(),
                })
        else:
            # faster-whisper
            segments_gen, info = model.transcribe(
                str(p),
                language=self.language,
                beam_size=5,
                vad_filter=True,
                vad_parameters=dict(min_silence_duration_ms=500),
            )
            for seg in segments_gen:
                t = seg.text.strip()
                if t:
                    full_text_parts.append(t)
                    segments_out.append({
                        "id": seg.id,
                        "start": round(seg.start, 2),
                        "end": round(seg.end, 2),
                        "text": t,
                        "avg_logprob": round(seg.avg_logprob, 3),
                    })
            full_text = " ".join(full_text_parts)

        duration_ms = int((time.perf_counter() - start_t) * 1000)
        logger.info(f"Trascrizione completata in {duration_ms}ms ({len(segments_out)} segmenti, {len(full_text)} caratteri).")
        return full_text, segments_out, duration_ms

    def transcribe_note(self, note_id: str) -> Optional[str]:
        """
        Trascrive una nota presente nel DB ed aggiorna la tabella transcriptions.
        Ritorna il transcription_id creato.
        """
        note = self.db.get_note(note_id)
        if not note:
            logger.error(f"Nota con ID {note_id} non trovata.")
            return None

        audio_path = note.get("audio_local_path")
        if not audio_path or not Path(audio_path).exists():
            logger.error(f"File audio mancante per la nota {note_id}: {audio_path}")
            return None

        logger.info(f"Trascrizione nota #{note.get('device_note_num')} (Tag: {note.get('tag')})...")
        try:
            raw_text, segments, duration_ms = self.transcribe_file(audio_path)
            model_name = f"faster-whisper-{self.model_size}-{self.compute_type}"
            trans_id = self.db.add_transcription(
                note_id=note_id,
                raw_text=raw_text,
                stt_model=model_name,
                segments=segments,
                duration_ms=duration_ms,
            )
            return trans_id
        except Exception as e:
            logger.error(f"Errore durante la trascrizione della nota {note_id}: {e}")
            return None

    def process_all_pending(self) -> List[str]:
        """Trascrive tutte le note scaricate che non hanno ancora una trascrizione nel DB"""
        pending = self.db.get_pending_transcriptions()
        if not pending:
            logger.info("Nessuna nota in attesa di trascrizione.")
            return []

        logger.info(f"Trovate {len(pending)} note da trascrivere...")
        processed_ids = []
        for note in pending:
            tid = self.transcribe_note(note["id"])
            if tid:
                processed_ids.append(note["id"])
        return processed_ids
