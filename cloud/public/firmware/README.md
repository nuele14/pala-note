# Firmware binaries for browser flashing

The setup wizard at `/dashboard/flash` flashes the file in `manifest.json` over
Web Serial, then appends a per-user config blob to the `pala_cfg` partition.

## What to drop here (git-ignored)

Just **one file**: `pala_note.merged.bin` — the single combined image produced by
Arduino IDE's **Sketch → Export Compiled Binary** (it bundles bootloader +
partition table + boot_app0 + app at the right offsets). It's flashed at `0x0`.

The wizard writes the per-device config blob at `0x310000` (the `pala_cfg`
partition) automatically — keep that offset in sync with `partitions.csv` and
`lib/pala-device-config.ts`.

> Build it with **Partition Scheme = "Custom"** so the sketch's `partitions.csv`
> (which defines `pala_cfg`) is used. See `../../../firmware/pala_note/BUILD.md`
> (or run `scripts/build-firmware.sh` to build + stage headlessly).

### Fallback (older cores without merged.bin)

If your core doesn't emit `*.merged.bin`, switch `manifest.json` back to the
four-file form (`bootloader.bin`@0x0, `partitions.bin`@0x8000,
`boot_app0.bin`@0xe000, `pala_note.bin`@0x10000) — ping the assistant and it'll
flip the manifest.
