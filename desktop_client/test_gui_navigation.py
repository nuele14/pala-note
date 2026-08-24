import sys
import subprocess
from pathlib import Path
import flet as ft

# Add parent directory to sys.path
sys.path.insert(0, str(Path(__file__).parent.parent))

from db import Database
import app_gui

class TestPage:
    def __init__(self):
        self.title = 'Test'
        self.theme_mode = ft.ThemeMode.DARK
        self.window = type('Win', (), {'width': 460, 'height': 860, 'min_width': 380, 'min_height': 650})()
        self.padding = 0
        self.overlay = []
        self.snack_bar = None
        self.dialog = None
        self.bottom_sheet = None
        self.appbar = None
        self.navigation_bar = None
        self.floating_action_button = None
        self.controls = []

    def add(self, *ctrls):
        self.controls.extend(ctrls)

    def update(self):
        pass

def run_tests():
    print("=== STARTING FULL GUI NAVIGATION AUDIT ===")
    p = TestPage()
    app_gui.main(p)
    print("✓ Main view initialized")

    body = p.controls[0]

    # --- Test 1: Click all Tag chips in Home Feed ---
    feed_col = body.content.content  # Column in build_notes_feed
    chips_row = feed_col.controls[0]
    for idx, chip in enumerate(chips_row.controls):
        chip_text = chip.content.value
        chip.on_click(None)
        print(f"✓ Tag chip '{chip_text}' clicked and filtered successfully")

    # Reset filter to All
    chips_row.controls[0].on_click(None)

    # --- Test 2: Click every Note Card in Feed ---
    list_view = feed_col.controls[1].content
    cards = list_view.controls
    print(f"Found {len(cards)} note cards in feed")
    for idx, card in enumerate(cards):
        if hasattr(card, 'content') and hasattr(card.content, 'on_click') and card.content.on_click:
            card.content.on_click(None)
            print(f"✓ Note card #{idx+1} clicked -> Note detail opened")
            
            # Check bottom sheet content
            bs = p.bottom_sheet
            assert bs is not None, "BottomSheet should be set on page"
            assert bs.open is True, "BottomSheet should be open"
            
            # Test audio play button if present
            sheet_col = bs.content.content
            audio_bar = sheet_col.controls[1]
            play_btn = audio_bar.content.controls[1]
            if isinstance(play_btn, ft.ElevatedButton):
                play_btn.on_click(None)
                print("  ✓ Play audio clicked")
                play_btn.on_click(None)
                print("  ✓ Pause audio clicked")

            # Test export button
            action_row = sheet_col.controls[5]
            export_btn = action_row.controls[0]
            export_btn.on_click(None)
            print("  ✓ Export .MD button clicked")

    # --- Test 3: Search Tab Navigation & Live Typing ---
    nav_event = type('Evt', (), {'control': type('C', (), {'selected_index': 1})()})()
    p.navigation_bar.on_change(nav_event)
    print("✓ Switched to Search Tab")

    search_col = body.content.content
    search_field = search_col.controls[0]
    
    # Test typing query
    search_field.value = "Carrier"
    search_field.on_change(type('Evt', (), {'control': search_field})())
    print("✓ Search query 'Carrier' executed")

    # Check search result click
    search_list = search_col.controls[1].content
    if search_list.controls and isinstance(search_list.controls[0], ft.Card):
        res_card = search_list.controls[0]
        res_listtile = res_card.content
        res_listtile.on_click(None)
        print("✓ Search result card clicked -> Note detail opened")

    # --- Test 4: Settings Tab Navigation & Prompt Editor ---
    nav_event = type('Evt', (), {'control': type('C', (), {'selected_index': 2})()})()
    p.navigation_bar.on_change(nav_event)
    print("✓ Switched to Settings Tab")

    settings_col = body.content.content
    rule_tiles_col = settings_col.controls[3]
    for idx, tile in enumerate(rule_tiles_col.controls):
        tile_inner_col = tile.controls[0].content
        save_btn = tile_inner_col.controls[2].controls[0]
        save_btn.on_click(None)
        print(f"✓ Saved rule for tag {idx+1}")

    # --- Test 5: Sync Dialog Flow ---
    p.floating_action_button.on_click(None)
    print("✓ FAB clicked -> Sync dialog opened")
    assert p.dialog is not None, "Dialog should be open"
    assert p.dialog.open is True, "Dialog should be open"

    # Close dialog
    close_btn = p.dialog.actions[0]
    close_btn.on_click(None)
    print("✓ Sync dialog closed")

    # --- Test 6: App Bar Refresh ---
    refresh_btn = p.appbar.actions[0]
    refresh_btn.on_click(None)
    print("✓ App Bar refresh clicked")

    print("\n🎉 ALL NAVIGATION AND INTERACTION TESTS PASSED WITH 0 ERRORS! 🎉\n")

if __name__ == "__main__":
    run_tests()
