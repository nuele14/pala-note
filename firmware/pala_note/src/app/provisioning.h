#pragma once
#include <Arduino.h>

// Runtime configuration provisioned by the Pala Cloud browser flasher.
//
// The flasher writes a small blob to the `pala_cfg` flash partition. On boot we
// parse it, persist the values to NVS, then erase the partition (so plaintext
// secrets don't linger). Getters fall back to the compile-time secrets.h
// defines when nothing has been provisioned — so manually-flashed devices keep
// working unchanged.
//
// Blob layout (little-endian), kept in sync with web: lib/pala-device-config.ts
//   0  "PALA"  (4-byte magic)
//   4  version (=2)
//   5  field count
//   6  fields...  each: uint16 length + UTF-8 bytes
//   field order: ssid, password, ssid2, password2, openaiKey, palaKey, palaHost

void palaConfigInit();          // call once, early in setup()

// Active network (the one tried first); 0 = primary, 1 = secondary.
const char *palaWifiSsid();     // active network SSID
const char *palaWifiPass();     // active network password
const char *palaWifiSsidAt(int i);
const char *palaWifiPassAt(int i);
bool palaHasSecondNet();        // true if a non-empty secondary SSID is set
int  palaActiveNet();           // 0 or 1
void palaSetActiveNet(int i);   // persist the chosen default network

const char *palaOpenAiKey();
const char *palaApiKey();
const char *palaApiHost();
bool palaIsProvisioned();       // true if a non-empty primary SSID is configured
