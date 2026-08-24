import os
import sys
import subprocess
import threading
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Dict, Any

import flet as ft

# Includi il percorso corrente per importare i moduli locali
sys.path.insert(0, str(Path(__file__).parent))

from db import Database
from sync_client import SyncClient
from stt_engine import STTEngine
from llm_engine import LLMEngine
from exporter import Exporter

TAG_COLORS = {
    "Todo": ft.Colors.ORANGE_400,
    "Meeting": ft.Colors.PURPLE_400,
    "Idea": ft.Colors.GREEN_400,
    "Work": ft.Colors.BLUE_400,
    "Buy": ft.Colors.AMBER_400,
    "Private": ft.Colors.PINK_400,
    "Note": ft.Colors.CYAN_400,
    "Untagged": ft.Colors.GREY_400,
}


def get_tag_color(tag: str) -> str:
    return TAG_COLORS.get(tag, ft.Colors.BLUE_GREY_400)


def main(page: ft.Page):
    page.title = "ES1 — Extransformer Shield Uno"
    page.theme_mode = ft.ThemeMode.DARK
    page.window.width = 460
    page.window.height = 860
    page.window.min_width = 380
    page.window.min_height = 650
    page.padding = 0

    db = Database()

    # Stato applicazione
    current_tab_index = 0
    selected_tag_filter = "All"
    search_query = ""
    is_syncing = False
    active_audio_proc: Optional[subprocess.Popen] = None

    # ---------------------------------------------------------------------------
    # Componenti Condivisi
    # ---------------------------------------------------------------------------
    def show_snackbar(msg: str, is_error: bool = False):
        page.snack_bar = ft.SnackBar(
            content=ft.Text(msg, color=ft.Colors.WHITE),
            bgcolor=ft.Colors.RED_700 if is_error else ft.Colors.GREEN_700,
            duration=3000,
        )
        page.snack_bar.open = True
        page.update()

    def stop_current_audio():
        nonlocal active_audio_proc
        if active_audio_proc is not None:
            try:
                active_audio_proc.terminate()
            except Exception:
                pass
            active_audio_proc = None

    # ---------------------------------------------------------------------------
    # 1. SCHERMATA DETTAGLIO NOTA (Modal Sheet)
    # ---------------------------------------------------------------------------
    def open_note_detail(note_id: str):
        stop_current_audio()
        note = db.get_note(note_id)
        if not note:
            show_snackbar("Nota non trovata", is_error=True)
            return

        trans = db.get_transcription(note_id)
        elab = db.get_favorite_elaboration(note_id)
        tag = note.get("tag", "Untagged")
        color = get_tag_color(tag)

        # Audio player con afplay nativo macOS
        audio_path = note.get("audio_local_path", "")
        play_btn = None

        if audio_path and Path(audio_path).exists():
            def toggle_audio(e):
                nonlocal active_audio_proc
                if active_audio_proc is None or active_audio_proc.poll() is not None:
                    try:
                        active_audio_proc = subprocess.Popen(["afplay", audio_path])
                        play_btn.icon = ft.Icons.STOP_ROUNDED
                        play_btn.text = "Ferma"
                    except Exception as ex:
                        show_snackbar(f"Errore riproduzione: {ex}", is_error=True)
                else:
                    stop_current_audio()
                    play_btn.icon = ft.Icons.PLAY_ARROW_ROUNDED
                    play_btn.text = "Riproduci"
                page.update()

            play_btn = ft.ElevatedButton(
                "Riproduci Audio",
                icon=ft.Icons.PLAY_ARROW_ROUNDED,
                style=ft.ButtonStyle(bgcolor=ft.Colors.SURFACE_CONTAINER_HIGH, color=ft.Colors.ON_SURFACE),
                on_click=toggle_audio,
            )

        # Re-elaborazione LLM
        def trigger_re_elaborate(e):
            show_snackbar("Rielaborazione LLM in corso...")
            threading.Thread(target=run_llm_worker).start()

        def run_llm_worker():
            llm = LLMEngine(db=db)
            llm.elaborate_note(note_id)
            if page.bottom_sheet:
                page.bottom_sheet.open = False
            render_current_view()
            show_snackbar("Nota rielaborata con successo!")

        # Esportazione Markdown
        def trigger_export(e):
            exp = Exporter(db=db)
            p = exp.export_note_to_local(note_id)
            if p:
                show_snackbar(f"Esportato: {Path(p).name}")

        sheet_content = ft.Container(
            padding=20,
            content=ft.Column(
                scroll=ft.ScrollMode.ADAPTIVE,
                spacing=16,
                controls=[
                    # Header
                    ft.Row(
                        alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                        controls=[
                            ft.Container(
                                content=ft.Text(f"#{note.get('device_note_num')} {tag}", color=ft.Colors.WHITE, weight=ft.FontWeight.BOLD),
                                bgcolor=color,
                                border_radius=16,
                                padding=ft.Padding(12, 6, 12, 6),
                            ),
                            ft.Text(note.get("created_utc", "")[:16].replace("T", " "), size=12, color=ft.Colors.GREY_400),
                        ],
                    ),
                    # Audio Bar
                    ft.Container(
                        bgcolor=ft.Colors.SURFACE_CONTAINER_HIGH,
                        border_radius=12,
                        padding=12,
                        content=ft.Row(
                            alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                            controls=[
                                ft.Row([
                                    ft.Icon(ft.Icons.MIC_ROUNDED, color=color),
                                    ft.Text(f"{note.get('duration_sec', 0.0):.1f}s • {note.get('audio_file_size', 0)//1024} KB", size=13),
                                ]),
                                play_btn if play_btn else ft.Text("Audio mancante", size=12, color=ft.Colors.GREY_500),
                            ],
                        ),
                    ),
                    # Elaborazione LLM (Markdown)
                    ft.Text("✨ Contenuto Elaborato", size=15, weight=ft.FontWeight.BOLD, color=ft.Colors.PRIMARY),
                    ft.Container(
                        bgcolor=ft.Colors.SURFACE,
                        border=ft.Border.all(1, ft.Colors.OUTLINE_VARIANT),
                        border_radius=12,
                        padding=14,
                        content=ft.Markdown(
                            elab.get("content_markdown", "_Nessuna elaborazione disponibile._") if elab else "_Nessuna elaborazione disponibile._",
                            selectable=True,
                            extension_set=ft.MarkdownExtensionSet.GITHUB_FLAVORED,
                        ),
                    ),
                    # Trascrizione Originale Grezza
                    ft.ExpansionTile(
                        title=ft.Text("🎙️ Trascrizione Grezza (STT)", size=14, weight=ft.FontWeight.W_500),
                        expanded=False,
                        controls=[
                            ft.Container(
                                padding=12,
                                bgcolor=ft.Colors.SURFACE_CONTAINER_HIGH,
                                border_radius=8,
                                content=ft.Text(
                                    trans.get("raw_text", "Nessuna trascrizione") if trans else "Nessuna trascrizione",
                                    size=13,
                                    color=ft.Colors.ON_SURFACE_VARIANT,
                                    selectable=True,
                                ),
                            )
                        ],
                    ),
                    # Pulsanti Azione
                    ft.Row(
                        alignment=ft.MainAxisAlignment.END,
                        spacing=10,
                        controls=[
                            ft.OutlinedButton("Esporta .MD", icon=ft.Icons.DOWNLOAD_ROUNDED, on_click=trigger_export),
                            ft.FilledButton("Rielabora", icon=ft.Icons.AUTO_AWESOME_ROUNDED, on_click=trigger_re_elaborate),
                        ],
                    ),
                ],
            ),
        )

        bs = ft.BottomSheet(content=sheet_content, show_drag_handle=True, scrollable=True)
        page.bottom_sheet = bs
        bs.open = True
        page.update()

    # ---------------------------------------------------------------------------
    # 2. DIALOG DI SINCRONIZZAZIONE (Sync Workflow)
    # ---------------------------------------------------------------------------
    def open_sync_dialog(e):
        nonlocal is_syncing
        if is_syncing:
            return

        sync_status_text = ft.Text("Connessione all'hotspot ES1...", size=14)
        progress_bar = ft.ProgressBar(width=340, value=None)
        close_btn = ft.TextButton("Chiudi", visible=False)

        def close_dialog(e):
            dialog.open = False
            page.update()
            render_current_view()

        close_btn.on_click = close_dialog

        dialog = ft.AlertDialog(
            title=ft.Row([
                ft.Icon(ft.Icons.WIFI_ROUNDED, color=ft.Colors.PRIMARY),
                ft.Text("Sincronizzazione ES1", size=18, weight=ft.FontWeight.BOLD),
            ]),
            content=ft.Container(
                width=360,
                height=140,
                content=ft.Column(
                    spacing=14,
                    alignment=ft.MainAxisAlignment.CENTER,
                    controls=[
                        sync_status_text,
                        progress_bar,
                    ],
                ),
            ),
            actions=[close_btn],
            actions_alignment=ft.MainAxisAlignment.END,
        )

        page.dialog = dialog
        dialog.open = True
        page.update()

        def sync_worker():
            nonlocal is_syncing
            is_syncing = True
            try:
                # 1. Download
                client = SyncClient(db=db)
                sync_status_text.value = "Download note in corso da 192.168.4.1..."
                page.update()

                downloaded_ids = client.sync_all(auto_ack=True, auto_close=True)
                if not downloaded_ids:
                    sync_status_text.value = "Nessuna nuova nota trovata o dispositivo non raggiungibile."
                    progress_bar.value = 1.0
                    close_btn.visible = True
                    is_syncing = False
                    page.update()
                    return

                # 2. STT
                sync_status_text.value = f"Trascrizione Whisper in corso ({len(downloaded_ids)} note)..."
                page.update()
                stt = STTEngine(db=db)
                stt.process_all_pending()

                # 3. LLM
                sync_status_text.value = "Revisione intelligente con LLM..."
                page.update()
                llm = LLMEngine(db=db)
                llm.process_all_pending()

                # 4. Export
                sync_status_text.value = "Esportazione file Markdown..."
                page.update()
                exp = Exporter(db=db)
                exp.export_all_unexported()

                sync_status_text.value = f"✓ Sincronizzazione completata! ({len(downloaded_ids)} note elaborate)"
                progress_bar.value = 1.0
                close_btn.visible = True
            except Exception as ex:
                sync_status_text.value = f"Errore: {ex}"
                progress_bar.value = 0.0
                close_btn.visible = True
            finally:
                is_syncing = False
                page.update()

        threading.Thread(target=sync_worker).start()

    # ---------------------------------------------------------------------------
    # 3. VISTE PRINCIPALI
    # ---------------------------------------------------------------------------

    # --- TAB 0: FEED NOTE ---
    def build_notes_feed() -> ft.Control:
        notes = db.list_notes(tag=None if selected_tag_filter == "All" else selected_tag_filter)

        # Chips filtri tag
        all_tags = ["All", "Todo", "Meeting", "Idea", "Work", "Buy", "Private", "Note", "Untagged"]
        tag_chips = []

        for t in all_tags:
            is_sel = (t == selected_tag_filter)
            def on_chip_click(e, tag_val=t):
                nonlocal selected_tag_filter
                selected_tag_filter = tag_val
                render_current_view()

            tag_chips.append(
                ft.Container(
                    content=ft.Text(t, size=12, weight=ft.FontWeight.W_600 if is_sel else ft.FontWeight.NORMAL, color=ft.Colors.WHITE if is_sel else ft.Colors.ON_SURFACE_VARIANT),
                    bgcolor=get_tag_color(t) if is_sel else ft.Colors.SURFACE_CONTAINER_HIGH,
                    border_radius=16,
                    padding=ft.Padding(12, 6, 12, 6),
                    on_click=on_chip_click,
                    animate=ft.Animation(200, ft.AnimationCurve.EASE_OUT),
                )
            )

        chips_row = ft.Row(
            scroll=ft.ScrollMode.ADAPTIVE,
            spacing=8,
            controls=tag_chips,
        )

        # Lista note Cards
        cards = []
        for n in notes:
            nid = n["id"]
            tag = n.get("tag", "Untagged")
            color = get_tag_color(tag)
            trans = db.get_transcription(nid)
            elab = db.get_favorite_elaboration(nid)

            title = elab.get("title") if elab else (trans.get("raw_text", f"Nota #{n['device_note_num']}")[:45] if trans else f"Nota #{n['device_note_num']}")
            preview_snippet = elab.get("content_markdown", "")[:90] if elab else (trans.get("raw_text", "In attesa di trascrizione...")[:90] if trans else "Audio registrato")
            preview_clean = preview_snippet.replace("#", "").replace("- [ ]", "•").strip()

            def on_card_click(e, note_id=nid):
                open_note_detail(note_id)

            card = ft.Card(
                elevation=2,
                margin=ft.Margin(0, 4, 0, 4),
                content=ft.Container(
                    padding=14,
                    border_radius=12,
                    on_click=on_card_click,
                    content=ft.Column(
                        spacing=8,
                        controls=[
                            ft.Row(
                                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                                controls=[
                                    ft.Row([
                                        ft.Container(
                                            content=ft.Text(tag, size=11, weight=ft.FontWeight.BOLD, color=ft.Colors.WHITE),
                                            bgcolor=color,
                                            border_radius=10,
                                            padding=ft.Padding(8, 3, 8, 3),
                                        ),
                                        ft.Text(f"#{n['device_note_num']}", size=13, weight=ft.FontWeight.BOLD, color=ft.Colors.GREY_400),
                                    ], spacing=6),
                                    ft.Text(f"{n.get('duration_sec', 0.0):.1f}s • {n.get('created_utc', '')[:10]}", size=11, color=ft.Colors.GREY_500),
                                ],
                            ),
                            ft.Text(title, size=15, weight=ft.FontWeight.W_600, max_lines=1, overflow=ft.TextOverflow.ELLIPSIS),
                            ft.Text(preview_clean, size=12, color=ft.Colors.ON_SURFACE_VARIANT, max_lines=2, overflow=ft.TextOverflow.ELLIPSIS),
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                spacing=8,
                                controls=[
                                    ft.Icon(ft.Icons.MIC_ROUNDED, size=14, color=ft.Colors.GREEN_400 if trans else ft.Colors.GREY_600),
                                    ft.Icon(ft.Icons.AUTO_AWESOME_ROUNDED, size=14, color=ft.Colors.BLUE_400 if elab else ft.Colors.GREY_600),
                                    ft.Icon(ft.Icons.CHECK_CIRCLE_ROUNDED, size=14, color=ft.Colors.PURPLE_400),
                                ],
                            ),
                        ],
                    ),
                ),
            )
            cards.append(card)

        if not cards:
            cards.append(
                ft.Container(
                    alignment=ft.Alignment.CENTER,
                    padding=40,
                    content=ft.Column(
                        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
                        spacing=10,
                        controls=[
                            ft.Icon(ft.Icons.NOTE_ALT_OUTLINED, size=48, color=ft.Colors.GREY_600),
                            ft.Text("Nessuna nota trovata", size=16, color=ft.Colors.GREY_500),
                            ft.Text("Registra una nota su ES1 e sincronizza!", size=12, color=ft.Colors.GREY_600),
                        ],
                    ),
                )
            )

        return ft.Container(
            padding=16,
            expand=True,
            content=ft.Column(
                expand=True,
                spacing=12,
                controls=[
                    chips_row,
                    ft.Container(
                        expand=True,
                        content=ft.ListView(
                            controls=cards,
                            spacing=6,
                            expand=True,
                        ),
                    ),
                ],
            ),
        )

    # --- TAB 1: RICERCA FTS5 ---
    search_list_view = ft.ListView(spacing=6, expand=True)

    def update_search_results(query_str: str):
        search_results = []
        if query_str.strip():
            results = db.search_notes(query_str.strip(), limit=30)
            for r in results:
                nid = r["id"]
                tag = r.get("tag", "Untagged")
                color = get_tag_color(tag)

                def on_res_click(e, note_id=nid):
                    open_note_detail(note_id)

                search_results.append(
                    ft.Card(
                        content=ft.ListTile(
                            leading=ft.Container(
                                content=ft.Icon(ft.Icons.RECORD_VOICE_OVER_ROUNDED, color=color),
                                padding=4,
                            ),
                            title=ft.Text(r.get("title", f"Nota #{r['device_note_num']}"), weight=ft.FontWeight.BOLD, size=14),
                            subtitle=ft.Text(r.get("raw_text", "")[:70], size=12, max_lines=2),
                            trailing=ft.Text(f"#{r['device_note_num']}", color=ft.Colors.GREY_500, size=12),
                            on_click=on_res_click,
                        ),
                    )
                )

        if not search_results:
            search_results.append(
                ft.Container(
                    alignment=ft.Alignment.CENTER,
                    padding=40,
                    content=ft.Text("Digita per cercare all'istante nei testi sbobinati...", color=ft.Colors.GREY_500),
                )
            )

        search_list_view.controls = search_results
        try:
            search_list_view.update()
        except Exception:
            pass

    def build_search_view() -> ft.Control:
        def on_search_change(e):
            nonlocal search_query
            search_query = e.control.value
            update_search_results(search_query)

        search_tf = ft.TextField(
            value=search_query,
            hint_text="Cerca tra tutte le registrazioni...",
            prefix_icon=ft.Icons.SEARCH_ROUNDED,
            border_radius=12,
            autofocus=True,
            on_change=on_search_change,
        )

        update_search_results(search_query)

        return ft.Container(
            padding=16,
            expand=True,
            content=ft.Column(
                expand=True,
                spacing=14,
                controls=[
                    search_tf,
                    ft.Container(
                        expand=True,
                        content=search_list_view,
                    ),
                ],
            ),
        )

    # --- TAB 2: IMPOSTAZIONI & REGOLE PROMPT ---
    def build_settings_view() -> ft.Control:
        rules = db.list_tag_rules()
        rule_items = []

        for r in rules:
            tag = r["tag"]
            prompt = r["system_prompt"]
            color = get_tag_color(tag)

            prompt_field = ft.TextField(
                value=prompt,
                multiline=True,
                min_lines=2,
                max_lines=5,
                text_size=12,
                border_radius=8,
            )

            def save_prompt(e, tag_name=tag, field=prompt_field):
                with db.get_connection() as conn:
                    conn.execute("UPDATE tag_rules SET system_prompt = ? WHERE tag = ?", (field.value.strip(), tag_name))
                    conn.commit()
                show_snackbar(f"Prompt salvato per tag '{tag_name}'")

            rule_items.append(
                ft.ExpansionTile(
                    title=ft.Row([
                        ft.Container(bgcolor=color, width=10, height=10, border_radius=5),
                        ft.Text(f"Tag: {tag}", weight=ft.FontWeight.BOLD, size=14),
                    ], spacing=8),
                    subtitle=ft.Text(f"Output: {r.get('output_format')} • Cartella: {r.get('target_drive_folder')}", size=11, color=ft.Colors.GREY_500),
                    controls=[
                        ft.Container(
                            padding=12,
                            content=ft.Column([
                                ft.Text("System Prompt:", size=12, weight=ft.FontWeight.W_500),
                                prompt_field,
                                ft.Row(
                                    alignment=ft.MainAxisAlignment.END,
                                    controls=[ft.FilledButton("Salva Regola", icon=ft.Icons.SAVE_ROUNDED, on_click=save_prompt)],
                                ),
                            ], spacing=8),
                        ),
                    ],
                )
            )

        return ft.Container(
            padding=16,
            expand=True,
            content=ft.Column(
                scroll=ft.ScrollMode.ADAPTIVE,
                spacing=16,
                controls=[
                    ft.Text("⚙️ Impostazioni AI & Pipeline", size=18, weight=ft.FontWeight.BOLD),
                    ft.Card(
                        content=ft.Container(
                            padding=14,
                            content=ft.Column([
                                ft.Text("Motore Whisper (STT Locale)", weight=ft.FontWeight.BOLD, size=13),
                                ft.Text("faster-whisper (Int8) • Modello: base (140 MB)", size=12, color=ft.Colors.GREY_400),
                                ft.Divider(),
                                ft.Text("Motore LLM (Revisione Locale)", weight=ft.FontWeight.BOLD, size=13),
                                ft.Text("Ollama (qwen2.5:1.5b) o Fallback Euristico", size=12, color=ft.Colors.GREY_400),
                            ], spacing=6),
                        ),
                    ),
                    ft.Text("📝 Editor Prompt & Regole Tag", size=16, weight=ft.FontWeight.BOLD),
                    ft.Column(controls=rule_items, spacing=8),
                ],
            ),
        )

    # ---------------------------------------------------------------------------
    # RENDER & NAVIGAZIONE
    # ---------------------------------------------------------------------------
    body_container = ft.Container(expand=True)

    def render_current_view():
        stop_current_audio()
        if current_tab_index == 0:
            body_container.content = build_notes_feed()
        elif current_tab_index == 1:
            body_container.content = build_search_view()
        elif current_tab_index == 2:
            body_container.content = build_settings_view()
        page.update()

    def on_nav_change(e):
        nonlocal current_tab_index
        current_tab_index = e.control.selected_index
        render_current_view()

    # Top App Bar
    app_bar = ft.AppBar(
        leading=ft.Icon(ft.Icons.GRAPHIC_EQ_ROUNDED, color=ft.Colors.PRIMARY),
        leading_width=40,
        title=ft.Text("ES1", weight=ft.FontWeight.BOLD, size=18),
        center_title=False,
        bgcolor=ft.Colors.SURFACE,
        actions=[
            ft.IconButton(ft.Icons.REFRESH_ROUNDED, tooltip="Aggiorna Feed", on_click=lambda e: render_current_view()),
        ],
    )

    # Bottom Navigation Bar
    nav_bar = ft.NavigationBar(
        selected_index=0,
        on_change=on_nav_change,
        destinations=[
            ft.NavigationBarDestination(icon=ft.Icons.NOTES_ROUNDED, label="Note"),
            ft.NavigationBarDestination(icon=ft.Icons.SEARCH_ROUNDED, label="Cerca"),
            ft.NavigationBarDestination(icon=ft.Icons.SETTINGS_ROUNDED, label="Impostazioni"),
        ],
    )

    # Floating Action Button per Sincronizzazione
    fab = ft.FloatingActionButton(
        bgcolor=ft.Colors.PRIMARY,
        content=ft.Row(
            alignment=ft.MainAxisAlignment.CENTER,
            controls=[
                ft.Icon(ft.Icons.SYNC_ROUNDED, color=ft.Colors.ON_PRIMARY),
                ft.Text("Sync", color=ft.Colors.ON_PRIMARY, weight=ft.FontWeight.BOLD),
            ],
            spacing=6,
        ),
        on_click=open_sync_dialog,
    )

    page.appbar = app_bar
    page.navigation_bar = nav_bar
    page.floating_action_button = fab
    page.add(body_container)

    render_current_view()


if __name__ == "__main__":
    ft.app(target=main)
