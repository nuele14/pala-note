#!/usr/bin/env python3
import sys
import shutil
import argparse
from pathlib import Path
from datetime import datetime, timezone

from db import Database
from sync_client import SyncClient
from stt_engine import STTEngine
from llm_engine import LLMEngine
from exporter import Exporter


def print_banner():
    print("""
  ╔══════════════════════════════════════════════════════════════════╗
  ║                 PALA NOTE — LOCAL DESKTOP HOST                   ║
  ║             Edge AI Audio Sync, Sbobinatura & LLM                ║
  ╚══════════════════════════════════════════════════════════════════╝
""")


def cmd_sync(args, db):
    print("\n📡 [1/4] Avvio sincronizzazione con ESP32 Pala Note...")
    client = SyncClient(host=args.host, port=args.port, db=db)
    synced_ids = client.sync_all(auto_ack=not args.no_ack, auto_close=not args.no_close)
    print(f"✓ Sincronizzazione completata: {len(synced_ids)} note scaricate.\n")
    return synced_ids


def cmd_transcribe(args, db):
    print("\n🎙️ [2/4] Trascrizione STT con Whisper...")
    stt = STTEngine(model_size=args.whisper_model, language=args.language, db=db)
    processed_ids = stt.process_all_pending()
    print(f"✓ Trascrizione completata: {len(processed_ids)} note elaborate.\n")
    return processed_ids


def cmd_elaborate(args, db):
    print("\n🧠 [3/4] Revisione ed elaborazione con LLM...")
    llm = LLMEngine(model_name=args.llm_model, ollama_url=args.ollama_url, db=db)
    elaborated_ids = llm.process_all_pending()
    print(f"✓ Elaborazione LLM completata: {len(elaborated_ids)} note revisionate.\n")
    return elaborated_ids


def cmd_export(args, db):
    print("\n📤 [4/4] Esportazione file Markdown...")
    exp = Exporter(db=db)
    files = exp.export_all_unexported()
    print(f"✓ Esportazione completata: {len(files)} file generati in '{exp.export_dir}'.\n")
    for f in files:
        print(f"  • {f}")
    print()
    return files


def cmd_pipeline(args, db):
    print("\n🚀 ESECUZIONE PIPELINE COMPLETA (Sync ➔ STT ➔ LLM ➔ Export)")
    cmd_sync(args, db)
    cmd_transcribe(args, db)
    cmd_elaborate(args, db)
    cmd_export(args, db)
    print("✨ Tutti i passaggi sono stati completati con successo!\n")


def cmd_list(args, db):
    notes = db.list_notes(tag=args.tag, limit=args.limit)
    print(f"\n📋 Note memorizzate nel DB locale ({len(notes)} totali):")
    print("─" * 80)
    print(f"{'#':<4} {'ID (UUID)':<12} {'Tag':<10} {'Data (UTC)':<20} {'Durata':<8} {'STT':<6} {'LLM':<6}")
    print("─" * 80)

    for n in notes:
        trans = db.get_transcription(n["id"])
        elab = db.get_favorite_elaboration(n["id"])

        stt_status = "✓" if trans else "✗"
        llm_status = "✓" if elab else "✗"
        short_id = n["id"][:8] + "..."
        created = n["created_utc"][:19] if n["created_utc"] else "N/A"
        dur = f"{n['duration_sec']:.1f}s"

        print(f"#{n['device_note_num']:<3} {short_id:<12} {n['tag']:<10} {created:<20} {dur:<8} {stt_status:<6} {llm_status:<6}")

    print("─" * 80 + "\n")


def cmd_show(args, db):
    target = args.note_id
    note = None

    # Prova per numero nota
    if target.isdigit():
        with db.get_connection() as conn:
            row = conn.execute("SELECT * FROM notes WHERE device_note_num = ? ORDER BY created_at DESC LIMIT 1", (int(target),)).fetchone()
            if row:
                note = dict(row)

    if not note:
        note = db.get_note(target)

    if not note:
        print(f"❌ Nota '{target}' non trovata nel database.")
        return

    trans = db.get_transcription(note["id"])
    elabs = db.get_elaborations(note["id"])

    print("\n" + "═" * 70)
    print(f"📄 DETTAGLIO NOTA #{note['device_note_num']} (ID: {note['id']})")
    print(f"• Tag: {note['tag']} | Data: {note['created_utc']} | Durata: {note['duration_sec']:.1f}s")
    print(f"• Audio Locale: {note['audio_local_path']}")
    print("═" * 70)

    print("\n🎙️ TRASCRIZIONE STT (Whisper):")
    if trans:
        print(f"[{trans.get('stt_model')} in {trans.get('duration_ms')}ms]")
        print(trans["raw_text"])
    else:
        print("(Nessuna trascrizione ancora registrata)")

    print("\n🧠 ELABORAZIONE LLM:")
    if elabs:
        fav = next((e for e in elabs if e.get("is_favorite")), elabs[0])
        print(f"[{fav.get('llm_model')} - Versione {fav.get('version')}]")
        print("─" * 40)
        print(fav["content_markdown"])
    else:
        print("(Nessuna elaborazione LLM ancora registrata)")

    print("\n" + "═" * 70 + "\n")


def cmd_search(args, db):
    print(f"\n🔍 Ricerca Full-Text (FTS5) per: '{args.query}'")
    results = db.search_notes(args.query, limit=args.limit)
    print(f"Trovate {len(results)} note corrispondenti:\n")
    for r in results:
        print(f"• #{r['device_note_num']} [{r['tag']}] {r['title']} (ID: {r['id'][:8]}...)")
        snippet = r.get("raw_text", "")[:120]
        if snippet:
            print(f"  Snippet: \"{snippet}...\"")
    print()


def cmd_mock_add(args, db):
    """Aggiunge un file WAV locale al DB per testare la pipeline senza hardware"""
    src_path = Path(args.audio_file)
    if not src_path.exists():
        print(f"❌ File audio non trovato: {src_path}")
        return

    audio_dir = Path(__file__).parent / "data" / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)

    dest_path = audio_dir / f"mock_{src_path.name}"
    shutil.copy2(src_path, dest_path)

    import hashlib
    with open(dest_path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()

    now_utc = datetime.now(timezone.utc).isoformat()
    note_id, is_new = db.upsert_note(
        device_id="ESP32-MOCK",
        device_note_num=999,
        created_utc=now_utc,
        tag=args.tag,
        duration_sec=args.duration,
        audio_file_size=dest_path.stat().st_size,
        audio_local_path=str(dest_path),
        audio_sha256=sha256,
        sync_status="DOWNLOADED",
    )
    print(f"✓ Nota mock #{999} aggiunta con successo (ID: {note_id}, Tag: {args.tag}).")
    print(f"  Ora puoi eseguire: python orchestrator.py process --whisper-model base\n")


def main():
    print_banner()
    db = Database()

    parser = argparse.ArgumentParser(description="Pala Note Desktop Host & AI Pipeline")
    parser.add_argument("--host", default="192.168.4.1", help="IP dell'ESP32 SoftAP (default: 192.168.4.1)")
    parser.add_argument("--port", type=int, default=80, help="Porta HTTP dell'ESP32 (default: 80)")
    parser.add_argument("--whisper-model", default="base", help="Dimensione modello Whisper: tiny, base, small, medium")
    parser.add_argument("--language", default="it", help="Lingua trascrizione (default: it)")
    parser.add_argument("--llm-model", default="qwen2.5:1.5b", help="Modello LLM per Ollama (default: qwen2.5:1.5b)")
    parser.add_argument("--ollama-url", default="http://localhost:11434", help="URL server Ollama locale")
    parser.add_argument("--no-ack", action="store_true", help="Non inviare ACK all'ESP32 dopo il download")
    parser.add_argument("--no-close", action="store_true", help="Non chiudere il SoftAP all'ESP32 dopo il download")

    subparsers = parser.add_subparsers(dest="command", help="Comando da eseguire")

    # sync
    subparsers.add_parser("sync", help="Scarica le note nuove dall'ESP32")

    # transcribe
    subparsers.add_parser("transcribe", help="Esegue STT (Whisper) su tutte le note pendenti")

    # elaborate
    subparsers.add_parser("elaborate", help="Esegue revisione LLM sulle note trascritte")

    # export
    subparsers.add_parser("export", help="Esporta le note elaborate in Markdown")

    # run / all
    subparsers.add_parser("run", help="Esegue la pipeline completa (Sync ➔ STT ➔ LLM ➔ Export)")
    subparsers.add_parser("all", help="Alias di 'run'")

    # list
    list_p = subparsers.add_parser("list", help="Elenca le note salvate nel DB locale")
    list_p.add_argument("--tag", default=None, help="Filtra per tag")
    list_p.add_argument("--limit", type=int, default=50, help="Limite note")

    # show
    show_p = subparsers.add_parser("show", help="Mostra i dettagli completi di una nota")
    show_p.add_argument("note_id", help="ID UUID o numero nota (#)")

    # search
    search_p = subparsers.add_parser("search", help="Ricerca full-text tra le note")
    search_p.add_argument("query", help="Termine di ricerca")
    search_p.add_argument("--limit", type=int, default=20)

    # mock-add
    mock_p = subparsers.add_parser("mock-add", help="Aggiunge un file audio locale per testare la pipeline")
    mock_p.add_argument("audio_file", help="Percorso del file .wav")
    mock_p.add_argument("--tag", default="Todo", help="Tag da assegnare")
    mock_p.add_argument("--duration", type=float, default=10.0, help="Durata stimata in secondi")

    args = parser.parse_args()

    if not args.command or args.command in ("run", "all"):
        cmd_pipeline(args, db)
    elif args.command == "sync":
        cmd_sync(args, db)
    elif args.command == "transcribe":
        cmd_transcribe(args, db)
    elif args.command == "elaborate":
        cmd_elaborate(args, db)
    elif args.command == "export":
        cmd_export(args, db)
    elif args.command == "list":
        cmd_list(args, db)
    elif args.command == "show":
        cmd_show(args, db)
    elif args.command == "search":
        cmd_search(args, db)
    elif args.command == "mock-add":
        cmd_mock_add(args, db)


if __name__ == "__main__":
    main()
