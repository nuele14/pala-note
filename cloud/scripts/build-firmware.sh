#!/usr/bin/env bash
#
# Build the Pala Note firmware headlessly with arduino-cli and stage the binary
# for the browser flasher (public/firmware/). Mirrors the board options used in
# the Arduino IDE: ESP32-S3, OPI PSRAM, 4MB flash, USB CDC on boot, and the
# custom partition table (partitions.csv) that defines the pala_cfg partition.
#
# Usage:
#   scripts/build-firmware.sh            # build + stage into public/firmware
#   scripts/build-firmware.sh --no-copy  # build only (verify it compiles)
#
set -euo pipefail

# Firmware sketch lives in the sibling firmware/ dir of this monorepo.
SKETCH="${PALA_SKETCH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../firmware/pala_note" && pwd)}"
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=4M,PartitionScheme=custom,CDCOnBoot=cdc"
# Adafruit_GFX + Adafruit_BusIO live here (the IDE keeps them under ~/Documents,
# which the sandbox can't read). Populate once:
#   git clone --depth 1 https://github.com/adafruit/Adafruit-GFX-Library ~/arduino-libs/Adafruit_GFX
#   git clone --depth 1 https://github.com/adafruit/Adafruit_BusIO        ~/arduino-libs/Adafruit_BusIO
LIBS="${PALA_ARDUINO_LIBS:-$HOME/arduino-libs}"
CLOUD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$CLOUD_DIR/.firmware-build"
DEST="$CLOUD_DIR/public/firmware"
SECRETS="$SKETCH/secrets.h"

echo "▸ Sketch:  $SKETCH"
echo "▸ FQBN:    $FQBN"

# --- guard: refuse to build with real secrets baked in --------------------
# Provisioning supplies WiFi/OpenAI/Pala at flash time, so the distributed
# binary must ship with placeholder secrets.h (values == "....").
if [[ -f "$SECRETS" ]]; then
  for key in WIFI_SSID WIFI_PASS OPENAI_KEY PALA_API_KEY; do
    val="$(grep -E "#define[[:space:]]+$key" "$SECRETS" | sed -E 's/.*"(.*)".*/\1/')"
    if [[ -n "$val" && "$val" != "...." ]]; then
      echo "✗ $key in secrets.h is not a placeholder ('....')."
      echo "  Scrub secrets.h before building a binary you'll publish — provisioning fills these."
      exit 1
    fi
  done
  echo "✓ secrets.h is scrubbed (placeholders)"
fi

echo "▸ Compiling… (first run pulls the toolchain; subsequent builds are fast)"
arduino-cli compile --fqbn "$FQBN" --libraries "$LIBS" --output-dir "$OUT" "$SKETCH"

APP="$OUT/pala_note.ino.bin"

# --- guard: scan the compiled app image for leaked credentials ------------
# Match real key *shapes* (long high-entropy strings), not substrings like the
# "pala_cfg" partition label or the "pala_note" app name.
if grep -aqE 'sk-[A-Za-z0-9_-]{24,}' "$APP"; then
  echo "✗ Compiled binary contains an OpenAI-key-shaped string. Aborting."
  exit 1
fi
if grep -aqE 'pala_[A-Za-z0-9]{28,}' "$APP"; then
  echo "✗ Compiled binary contains a Pala-API-key-shaped string. Aborting."
  exit 1
fi
echo "✓ compiled binary contains no key-shaped secrets"

if [[ "${1:-}" == "--no-copy" ]]; then
  echo "✓ build OK (--no-copy): $APP ($(wc -c < "$APP") bytes)"
  exit 0
fi

cp "$OUT/pala_note.ino.bootloader.bin" "$DEST/bootloader.bin"
cp "$OUT/pala_note.ino.partitions.bin" "$DEST/partitions.bin"
cp "$OUT/pala_note.ino.bin"            "$DEST/pala_note.bin"
# boot_app0.bin comes from the core, not the sketch build:
BOOT_APP0="$(find "$HOME/Library/Arduino15/packages/esp32/hardware/esp32" -name boot_app0.bin 2>/dev/null | head -1)"
[[ -n "$BOOT_APP0" ]] && cp "$BOOT_APP0" "$DEST/boot_app0.bin"

echo "✓ staged into public/firmware/:"
ls -la "$DEST"/*.bin | awk '{print "   ", $5, $9}'
echo "▸ Next: redeploy (npx vercel deploy --prod) to publish. The deploy is"
echo "  classifier-gated — it'll re-scan for secrets."
