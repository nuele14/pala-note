/**
 * Pala device config blob — the on-flash contract between this web flasher and
 * the firmware. The browser writes this blob to the `pala_cfg` partition; the
 * firmware reads it at boot (see firmware src/app/provisioning).
 *
 * Layout (little-endian):
 *   0   "PALA"            4-byte magic
 *   4   version (=2)      1 byte
 *   5   field count       1 byte
 *   6   fields...         each: uint16 length + UTF-8 bytes
 *
 * Field order is fixed: ssid, password, ssid2, password2, openaiKey, palaKey,
 * palaHost. The firmware parses by the count byte, so older/newer field counts
 * degrade gracefully. Keep this in sync with the firmware parser.
 */

export const PALA_CFG_MAGIC = "PALA";
export const PALA_CFG_VERSION = 2;
export const PALA_CFG_PARTITION_SIZE = 0x1000; // 4 KiB

export interface PalaDeviceConfig {
  ssid: string;
  password: string;
  ssid2: string;
  password2: string;
  openaiKey: string;
  palaKey: string;
  palaHost: string;
}

const FIELD_ORDER: (keyof PalaDeviceConfig)[] = [
  "ssid",
  "password",
  "ssid2",
  "password2",
  "openaiKey",
  "palaKey",
  "palaHost",
];

export function buildConfigBlob(cfg: PalaDeviceConfig): Uint8Array {
  const enc = new TextEncoder();
  const fields = FIELD_ORDER.map((k) => enc.encode(cfg[k] ?? ""));

  const total = 6 + fields.reduce((n, f) => n + 2 + f.length, 0);
  if (total > PALA_CFG_PARTITION_SIZE) {
    throw new Error("Device config is too large for the config partition.");
  }

  const out = new Uint8Array(PALA_CFG_PARTITION_SIZE); // zero-padded
  const view = new DataView(out.buffer);
  let o = 0;
  out.set(enc.encode(PALA_CFG_MAGIC), o);
  o += 4;
  out[o++] = PALA_CFG_VERSION;
  out[o++] = FIELD_ORDER.length;
  for (const f of fields) {
    view.setUint16(o, f.length, true);
    o += 2;
    out.set(f, o);
    o += f.length;
  }
  return out;
}
