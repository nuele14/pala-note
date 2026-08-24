#!/usr/bin/env python3
"""
=============================================================================
ES1 (Extransformer Shield Uno) — Screensaver Image Converter
Converte qualsiasi immagine (PNG, JPG, BMP, WebP) nel formato ottimizzato
1-bit per il display E-Paper 200x200 pixel dell'ES1.
Zero dipendenze grafiche pesanti: leggero, rapido e affidabile al 100%.
=============================================================================
"""

import os
import sys
import argparse
import subprocess
from pathlib import Path
from typing import Optional, List, Tuple
from PIL import Image, ImageOps, ImageDraw, ImageFont

TARGET_W = 200
TARGET_H = 200
RAW_BYTE_COUNT = (TARGET_W * TARGET_H) // 8  # Esattamente 5,000 bytes


def convert_image_to_1bit_data(
    img_path: Path,
    mode: str = "cover",
    dither: bool = True,
    invert: bool = False,
    threshold: int = 128
) -> Tuple[bytes, Image.Image]:
    """
    Carica un'immagine, la ridimensiona a 200x200 e la converte in 1-bit monocromatico.
    Restituisce:
      - raw_bytes: 5,000 bytes binari (row-major, MSB-first: 1=nero, 0=bianco)
      - pil_1bit: oggetto PIL Image a 1-bit (200x200).
    """
    img = Image.open(img_path).convert("RGBA")

    # Applica sfondo bianco su eventuale trasparenza
    bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
    img = Image.alpha_composite(bg, img).convert("L")

    # Ridimensionamento
    if mode == "cover":
        img = ImageOps.fit(img, (TARGET_W, TARGET_H), method=Image.Resampling.LANCZOS)
    elif mode == "contain":
        img.thumbnail((TARGET_W, TARGET_H), Image.Resampling.LANCZOS)
        padded = Image.new("L", (TARGET_W, TARGET_H), 255)
        offset = ((TARGET_W - img.width) // 2, (TARGET_H - img.height) // 2)
        padded.paste(img, offset)
        img = padded
    else:  # stretch
        img = img.resize((TARGET_W, TARGET_H), Image.Resampling.LANCZOS)

    if invert:
        img = ImageOps.invert(img)

    if dither:
        # Floyd-Steinberg dithering ad alta resa per E-Paper
        img_1bit = img.convert("1", dither=Image.Dither.FLOYDSTEINBERG)
    else:
        img_1bit = img.point(lambda p: 255 if p > threshold else 0, mode="1")

    # Estrazione buffer 1bpp raw (1=nero, 0=bianco, row-major MSB-first)
    pixels = img_1bit.load()
    raw_bytes = bytearray(RAW_BYTE_COUNT)

    for y in range(TARGET_H):
        for x in range(TARGET_W):
            if pixels[x, y] == 0:  # 0 = nero in PIL "1"
                byte_idx = (y * TARGET_W + x) // 8
                bit_idx = 7 - (x % 8)
                raw_bytes[byte_idx] |= (1 << bit_idx)

    return bytes(raw_bytes), img_1bit


def find_sd_screensaver_dir() -> Optional[Path]:
    """Cerca automaticamente la cartella /screensavers/ su schede MicroSD montate su macOS/Linux."""
    candidates = [
        Path("/Volumes/UNONOTE/screensavers"),
        Path("/Volumes/PalaNote/screensavers"),
        Path("/Volumes/ES1/screensavers"),
        Path("/Volumes/NO NAME/screensavers"),
    ]
    for c in candidates:
        if c.parent.exists():
            c.mkdir(parents=True, exist_ok=True)
            return c
    return None


def pick_file_macos() -> Optional[Path]:
    """Apre la finestra nativa di selezione file del Finder su macOS."""
    if sys.platform != "darwin":
        return None
    try:
        script = 'POSIX path of (choose file with prompt "Seleziona un\'immagine da convertire per ES1:" of type {"public.image"})'
        result = subprocess.run(["osascript", "-e", script], capture_output=True, text=True, check=True)
        path_str = result.stdout.strip()
        if path_str:
            return Path(path_str)
    except Exception:
        pass
    return None


def generate_sample_screensavers(out_dir: Path):
    """Genera 4 screensaver motivazionali con grafica 1-bit integrata."""
    out_dir.mkdir(parents=True, exist_ok=True)

    quotes = [
        ("ES1 FOCUS", "Do one thing\nat a time.", "PKNA • DUCKLAIR TOWER"),
        ("MINDFULNESS", "Less, but\nbetter.", "DIETER RAMS"),
        ("DISCIPLINA", "Focus is a\nmuscle.\nTrain it.", "ES1 COMPANION"),
        ("SHIKAMARU", "\"Che seccatura...\nma lo faro'\nal meglio.\"", "SHIKAMARU NARA"),
    ]

    print("\n🎨 Generazione screensaver di esempio...")
    for idx, (title, text, footer) in enumerate(quotes, 1):
        img = Image.new("1", (TARGET_W, TARGET_H), 1)
        draw = ImageDraw.Draw(img)

        # Cornice geometrica minimalista
        draw.rounded_rectangle([(8, 8), (191, 191)], radius=12, outline=0, width=2)
        draw.rectangle([(20, 18), (180, 36)], fill=0)
        draw.text((100, 27), title, fill=1, anchor="mm")

        lines = text.split("\n")
        y_start = 100 - (len(lines) * 11)
        for i, line in enumerate(lines):
            draw.text((100, y_start + i * 22), line, fill=0, anchor="mm")

        draw.line([(24, 164), (176, 164)], fill=0, width=1)
        draw.text((100, 175), footer, fill=0, anchor="mm")

        base_name = f"screensaver_{idx:02d}"
        bmp_path = out_dir / f"{base_name}.bmp"
        bin_path = out_dir / f"{base_name}.bin"

        img.save(bmp_path)

        pixels = img.load()
        raw = bytearray(RAW_BYTE_COUNT)
        for y in range(TARGET_H):
            for x in range(TARGET_W):
                if pixels[x, y] == 0:
                    byte_idx = (y * TARGET_W + x) // 8
                    bit_idx = 7 - (x % 8)
                    raw[byte_idx] |= (1 << bit_idx)

        bin_path.write_bytes(raw)
        print(f"  ✓ Creato: {bmp_path.name} e {bin_path.name}")


def main():
    parser = argparse.ArgumentParser(
        description="ES1 Screensaver Converter — Converte immagini per display E-Paper 200x200 (1-bit)."
    )
    parser.add_argument("input", nargs="?", help="File immagine o cartella con immagini da convertire.")
    parser.add_argument("-o", "--output", default="./screensavers_out", help="Cartella di output (default: ./screensavers_out).")
    parser.add_argument("--mode", choices=["cover", "contain", "stretch"], default="cover", help="Modalità ritaglio (default: cover).")
    parser.add_argument("--no-dither", action="store_true", help="Disabilita dithering Floyd-Steinberg e usa soglia fissa.")
    parser.add_argument("--threshold", type=int, default=128, help="Soglia bianco/nero (0-255).")
    parser.add_argument("--invert", action="store_true", help="Inverte i colori (bianco <-> nero).")
    parser.add_argument("--sd", help="Copia i file direttamente nella cartella screensavers della MicroSD.")
    parser.add_argument("--samples", action="store_true", help="Genera automaticamente 4 screensaver con citazioni minimaliste.")

    args = parser.parse_args()

    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.samples:
        generate_sample_screensavers(out_dir)
        if args.sd:
            sd_dir = Path(args.sd)
            if sd_dir.exists():
                for f in out_dir.glob("*.bin"):
                    (sd_dir / f.name).write_bytes(f.read_bytes())
                print(f"  ✓ Copiati su MicroSD: {sd_dir}")
        print(f"\n✨ Campioni pronti in: {out_dir}\n")
        return

    in_path: Optional[Path] = None

    if args.input:
        in_path = Path(args.input)
    else:
        # Se avviato senza argomenti, apre il selettore file nativo del Finder
        print("📂 Nessun file specificato. Apertura selettore file...")
        in_path = pick_file_macos()
        if not in_path:
            print("\n💡 Utilizzo rapido:")
            print("  1. python tools/img_to_screensaver.py mia_immagine.png")
            print("  2. python tools/img_to_screensaver.py cartella_immagini/")
            print("  3. python tools/img_to_screensaver.py --samples\n")
            return

    if not in_path.exists():
        print(f"❌ Errore: File o cartella non trovata: {in_path}")
        sys.exit(1)

    files_to_convert: List[Path] = []
    if in_path.is_file():
        files_to_convert.append(in_path)
    elif in_path.is_dir():
        exts = {".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tiff", ".gif"}
        files_to_convert = [f for f in in_path.iterdir() if f.suffix.lower() in exts]

    if not files_to_convert:
        print(f"❌ Nessuna immagine trovata in {in_path}")
        sys.exit(1)

    print(f"\n🚀 Conversione di {len(files_to_convert)} immagine/i per ES1 Display (200x200 1-bit)...")

    sd_target_dir = Path(args.sd) if args.sd else find_sd_screensaver_dir()
    if sd_target_dir:
        print(f"💾 MicroSD rilevata: {sd_target_dir}")

    for img_file in files_to_convert:
        try:
            raw_bytes, pil_1bit = convert_image_to_1bit_data(
                img_file,
                mode=args.mode,
                dither=not args.no_dither,
                invert=args.invert,
                threshold=args.threshold
            )

            base_name = img_file.stem
            bmp_path = out_dir / f"{base_name}.bmp"
            bin_path = out_dir / f"{base_name}.bin"

            # Salva anteprima BMP e file raw binario
            pil_1bit.save(bmp_path)
            bin_path.write_bytes(raw_bytes)

            print(f"  ✓ {img_file.name} ➔ {bmp_path.name} & {bin_path.name} ({len(raw_bytes)} bytes)")

            # Copia su MicroSD se presente
            if sd_target_dir and sd_target_dir.exists():
                (sd_target_dir / bin_path.name).write_bytes(raw_bytes)
                print(f"    ↳ Copiato su MicroSD: {sd_target_dir / bin_path.name}")

        except Exception as e:
            print(f"  ❌ Errore durante la conversione di {img_file.name}: {e}")

    print(f"\n✨ Conversione completata con successo! File salvati in: {out_dir}\n")


if __name__ == "__main__":
    main()
