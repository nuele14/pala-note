# Building Pala Note firmware (for browser flashing)

The Pala Cloud web app flashes pre-compiled binaries from the browser, then
writes a per-device config blob to the `pala_cfg` partition. This doc covers
producing those binaries.

## What changed for provisioning

- `partitions.csv` — adds a `pala_cfg` data partition at `0x310000` (4 KB).
- `src/app/provisioning.{h,cpp}` — at boot, reads the blob the flasher wrote,
  saves it to NVS, erases the partition, and exposes getters
  (`palaWifiSsid()`, `palaOpenAiKey()`, `palaApiKey()`, `palaApiHost()`, …).
- `pala_note.ino` / `network.cpp` now call those getters instead of the
  `secrets.h` defines. **`secrets.h` is still the fallback**, so a hand-flashed
  device with secrets filled in keeps working.

So you can build **one generic binary** for everyone — no per-user compile.

## Build with arduino-cli

```bash
# from this directory (firmware 1.0/pala_note)
arduino-cli compile \
  --fqbn esp32:esp32:esp32s3 \
  --board-options "PartitionScheme=custom" \
  --output-dir ./out \
  .
```

`PartitionScheme=custom` makes the core use the `partitions.csv` in this folder.
(If your core version names it differently, pass
`--build-property build.partitions=partitions` instead.)

Confirm your board's **flash size** matches `partitions.csv` (this assumes
4 MB). For 8/16 MB boards, enlarge `spiffs` and keep `pala_cfg` at `0x310000`
(or move it — but then also update `manifest.json` + `lib/pala-device-config.ts`).

## Copy the binaries into pala-cloud

Drop these into `pala-cloud/public/firmware/` (git-ignored):

| arduino-cli output                 | rename to        | offset    |
| ---------------------------------- | ---------------- | --------- |
| `out/pala_note.ino.bootloader.bin` | `bootloader.bin` | `0x0`     |
| `out/pala_note.ino.partitions.bin` | `partitions.bin` | `0x8000`  |
| `<core>/tools/partitions/boot_app0.bin` | `boot_app0.bin` | `0xe000` |
| `out/pala_note.ino.bin`            | `pala_note.bin`  | `0x10000` |

The flasher writes the config blob to `0x310000` automatically.

## Leave secrets.h blank for distributed builds

For the public binary, set `secrets.h` values back to `"...."` so no real
credentials ship inside the firmware. Provisioning fills them at flash time.
