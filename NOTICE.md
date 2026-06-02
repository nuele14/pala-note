# Licensing & provenance

This repository contains components under different licenses and origins. Read
this before reusing or redistributing any part of it.

## `cloud/` — MIT

The cloud application (`cloud/`) is original work, licensed under the MIT
License. See [cloud/LICENSE](cloud/LICENSE).

## `firmware/` — mixed

The firmware is **not** covered by the MIT license. It combines:

- **Original application code** (e.g. `firmware/pala_note/pala_note.ino`,
  `firmware/pala_note/src/app/*`) — modifications and integration work for the
  Pala Cloud setup.
- **Third-party vendored libraries** that retain their own licenses and
  copyright headers, including Espressif Systems' audio codec drivers under
  `firmware/pala_note/src/esp_codec_dev/` and `firmware/pala_note/src/codec_board/`
  (Apache-2.0 / MIT — see the SPDX and copyright headers in those files).
- Portions derived from the device vendor's reference firmware.

Keep all existing copyright and license headers intact. Honor each upstream
license when redistributing.

## `hardware/` — vendor-derived

The 3D-printable case files in `hardware/` are derived from the device
vendor's reference design and are provided here for convenience. They are
**not** licensed under MIT. If you intend to redistribute or manufacture from
them, confirm the vendor's terms first.

## Secrets

No credentials are committed. `secrets.h` (firmware) and `.env.local` (cloud)
are git-ignored; credentials are provisioned at flash/runtime.
