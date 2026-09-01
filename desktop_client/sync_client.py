import os
import hashlib
import logging
from pathlib import Path
from typing import Optional, List, Dict, Any, Tuple
import requests

from db import Database

logger = logging.getLogger("SyncClient")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

DEFAULT_ESP32_HOST = "192.168.4.1"
DEFAULT_ESP32_PORT = 80
AUDIO_STORAGE_DIR = Path(__file__).parent / "data" / "audio"


class SyncClient:
    def __init__(self, host: str = DEFAULT_ESP32_HOST, port: int = DEFAULT_ESP32_PORT, db: Optional[Database] = None):
        self.base_url = f"http://{host}:{port}"
        self.db = db or Database()
        self.audio_dir = AUDIO_STORAGE_DIR
        self.audio_dir.mkdir(parents=True, exist_ok=True)

    def ping(self, timeout: float = 3.0) -> bool:
        """Verifica se l'ESP32 è raggiungibile via SoftAP"""
        try:
            resp = requests.get(f"{self.base_url}/api/info", timeout=timeout)
            return resp.status_code == 200
        except requests.RequestException:
            return False

    def get_device_info(self, timeout: float = 4.0) -> Optional[Dict[str, Any]]:
        """Recupera le informazioni del dispositivo e della batteria"""
        try:
            resp = requests.get(f"{self.base_url}/api/info", timeout=timeout)
            if resp.status_code == 200:
                data = resp.json()
                device_id = data.get("device_id", "unknown_esp32")
                self.db.upsert_device(device_id=device_id, name=f"Pala Note ({device_id})", last_ip=self.base_url)
                return data
            logger.error(f"Errore get_device_info: status {resp.status_code}")
            return None
        except requests.RequestException as e:
            logger.error(f"Impossibile raggiungere ESP32 su {self.base_url}: {e}")
            return None

    def fetch_device_notes(self, timeout: float = 5.0) -> List[Dict[str, Any]]:
        """Scarica l'elenco di tutte le note presenti sull'ESP32"""
        try:
            resp = requests.get(f"{self.base_url}/api/notes", timeout=timeout)
            if resp.status_code == 200:
                data = resp.json()
                if isinstance(data, dict):
                    return data.get("notes", [])
                elif isinstance(data, list):
                    return data
                return []
            logger.error(f"Errore fetch_device_notes: status {resp.status_code}")
            return []
        except requests.RequestException as e:
            logger.error(f"Errore richiesta /api/notes: {e}")
            return []

    def download_audio_file(self, note_num: int, target_filename: Optional[str] = None, timeout: float = 30.0) -> Optional[Tuple[str, str, int]]:
        """
        Scarica il file WAV della nota dall'ESP32.
        Ritorna (percorso_locale_file, sha256_hash, file_size_bytes) o None se fallisce.
        """
        url = f"{self.base_url}/api/notes/audio?num={note_num}"
        if not target_filename:
            target_filename = f"note_{note_num:03d}.wav"

        local_path = self.audio_dir / target_filename
        sha256 = hashlib.sha256()
        total_bytes = 0

        try:
            logger.info(f"Scaricamento audio #{note_num} da {url}...")
            with requests.get(url, stream=True, timeout=timeout) as r:
                if r.status_code != 200:
                    logger.error(f"Download fallito per nota #{note_num}: HTTP {r.status_code}")
                    return None

                with open(local_path, "wb") as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        if chunk:
                            f.write(chunk)
                            sha256.update(chunk)
                            total_bytes += len(chunk)

            checksum = sha256.hexdigest()
            logger.info(f"Audio #{note_num} salvato in {local_path} ({total_bytes} bytes, SHA256: {checksum[:8]}...)")
            return str(local_path), checksum, total_bytes
        except requests.RequestException as e:
            logger.error(f"Eccezione durante il download audio #{note_num}: {e}")
            if local_path.exists():
                local_path.unlink()
            return None

    def send_ack(self, note_num: int, timeout: float = 4.0) -> bool:
        """Invia la conferma di ricezione (ACK) all'ESP32 per marcare la nota come sincronizzata su SD"""
        try:
            resp = requests.post(f"{self.base_url}/api/notes/ack?num={note_num}", timeout=timeout)
            return resp.status_code == 200
        except requests.RequestException as e:
            logger.warning(f"Impossibile inviare ACK per nota #{note_num}: {e}")
            return False

    def send_sync_done(self, timeout: float = 4.0) -> bool:
        """Informa l'ESP32 che la sincronizzazione è terminata (l'ESP32 spegne il SoftAP e va in Deep Sleep)"""
        try:
            resp = requests.post(f"{self.base_url}/api/sync/done", timeout=timeout)
            if resp.status_code == 200:
                logger.info("Sessione di sincronizzazione chiusa con successo sull'ESP32.")
                return True
            return False
        except requests.RequestException as e:
            logger.warning(f"Errore invio /api/sync/done: {e}")
            return False

    def sync_all(self, auto_ack: bool = True, auto_close: bool = True) -> List[str]:
        """
        Esegue la sincronizzazione completa differenziale:
        1. Prende info dispositivo
        2. Scarica lista note
        3. Scarica solo gli audio mancanti
        4. Salva metadati nel DB
        5. Invia ACK e chiude il SoftAP
        Ritorna la lista dei note_id (UUID) scaricati/sincronizzati.
        """
        logger.info("Avvio sincronizzazione con Pala Note...")
        info = self.get_device_info()
        if not info:
            logger.error("Dispositivo Pala Note non raggiungibile. Assicurati di essere connesso al Wi-Fi 'PalaNote-XXXX'.")
            return []

        device_id = info.get("device_id", "ESP32-DEV")
        notes = self.fetch_device_notes()
        logger.info(f"Trovate {len(notes)} note sul dispositivo ({info.get('pending_notes', 0)} da sincronizzare).")

        synced_note_ids = []

        for item in notes:
            num = item.get("num")
            tag = item.get("tag", "Untagged")
            created_utc = item.get("created_utc") or "1970-01-01T00:00:00Z"
            duration = float(item.get("duration_sec", 0.0))
            is_synced_on_dev = item.get("synced", False)

            # Verifica se abbiamo già la nota nel DB locale
            dedupe_hash = self.db.compute_dedupe_hash(device_id, num, created_utc)
            with self.db.get_connection() as conn:
                existing = conn.execute("SELECT id, audio_local_path FROM notes WHERE dedupe_hash = ?", (dedupe_hash,)).fetchone()

            local_audio_path = None
            audio_hash = None
            audio_size = 0

            if existing and existing["audio_local_path"] and Path(existing["audio_local_path"]).exists():
                logger.info(f"Nota #{num} ({tag}) già presente in locale: {existing['id']}")
                note_id = existing["id"]
            else:
                # Scarica il file WAV
                res = self.download_audio_file(num, target_filename=f"{device_id}_note_{num:03d}.wav")
                if not res:
                    logger.error(f"Salto nota #{num} a causa di errore di download.")
                    continue
                local_audio_path, audio_hash, audio_size = res

                note_id, is_new = self.db.upsert_note(
                    device_id=device_id,
                    device_note_num=num,
                    created_utc=created_utc,
                    tag=tag,
                    duration_sec=duration,
                    audio_file_size=audio_size,
                    audio_local_path=local_audio_path,
                    audio_sha256=audio_hash,
                    sync_status="ACKED" if auto_ack else "DOWNLOADED",
                )
                logger.info(f"Nota #{num} registrata nel DB con ID: {note_id}")

            if auto_ack and not is_synced_on_dev:
                ack_ok = self.send_ack(num)
                if ack_ok:
                    logger.info(f"ACK inviato con successo per nota #{num}")

            synced_note_ids.append(note_id)

        if auto_close:
            self.send_sync_done()

        logger.info(f"Sincronizzazione completata! {len(synced_note_ids)} note pronte per l'elaborazione.")
        return synced_note_ids
