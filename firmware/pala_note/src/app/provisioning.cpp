#include "provisioning.h"
#include "../../secrets.h"
#include <Preferences.h>
#include <esp_partition.h>

// Custom data-partition subtype for our config blob (app-defined range 0x40+).
#define PALA_CFG_SUBTYPE ((esp_partition_subtype_t)0x40)
#define PALA_CFG_LABEL   "pala_cfg"
#define PALA_NVS_NS      "pala"

#define NUM_FIELDS 7

static String g_ssid, g_pass, g_ssid2, g_pass2, g_openai, g_palakey, g_palahost;
static int g_activeNet = 0;
static bool g_loaded = false;

// Parse the length-prefixed blob into out[count]. Returns the number of fields
// parsed (0 if malformed). Tolerates fewer/more fields than NUM_FIELDS.
static int parseBlob(const uint8_t *buf, size_t len, String *out, int maxFields) {
  if (len < 6) return 0;
  if (!(buf[0] == 'P' && buf[1] == 'A' && buf[2] == 'L' && buf[3] == 'A')) return 0;
  // accept any version >= 1
  uint8_t count = buf[5];
  size_t o = 6;
  int parsed = 0;
  for (int i = 0; i < count && i < maxFields; i++) {
    if (o + 2 > len) break;
    uint16_t flen = (uint16_t)buf[o] | ((uint16_t)buf[o + 1] << 8);
    o += 2;
    if (o + flen > len) break;
    out[i] = "";
    out[i].reserve(flen + 1);
    for (uint16_t j = 0; j < flen; j++) out[i] += (char)buf[o + j];
    o += flen;
    parsed++;
  }
  return parsed;
}

// If a freshly-flashed config blob is present, persist it to NVS and wipe the
// partition. Returns true if it provisioned new values.
static bool provisionFromPartition(Preferences &prefs) {
  const esp_partition_t *p = esp_partition_find_first(
      ESP_PARTITION_TYPE_DATA, PALA_CFG_SUBTYPE, PALA_CFG_LABEL);
  if (!p) return false;

  uint8_t buf[512];
  size_t n = p->size < sizeof(buf) ? p->size : sizeof(buf);
  if (esp_partition_read(p, 0, buf, n) != ESP_OK) return false;

  String f[NUM_FIELDS];
  int parsed = parseBlob(buf, n, f, NUM_FIELDS);
  if (parsed < 2) return false; // empty/erased or not ours

  prefs.putString("ssid", f[0]);
  prefs.putString("pass", f[1]);
  prefs.putString("ssid2", parsed > 2 ? f[2] : String(""));
  prefs.putString("pass2", parsed > 3 ? f[3] : String(""));
  prefs.putString("openai", parsed > 4 ? f[4] : String(""));
  prefs.putString("palakey", parsed > 5 ? f[5] : String(""));
  prefs.putString("palahost", parsed > 6 ? f[6] : String(""));
  prefs.putInt("activenet", 0); // re-provision resets the default to primary

  esp_partition_erase_range(p, 0, p->size);
  Serial.println("[provision] applied config from flasher");
  return true;
}

void palaConfigInit() {
  Preferences prefs;
  prefs.begin(PALA_NVS_NS, false);
  provisionFromPartition(prefs);
  g_ssid = prefs.getString("ssid", "");
  g_pass = prefs.getString("pass", "");
  g_ssid2 = prefs.getString("ssid2", "");
  g_pass2 = prefs.getString("pass2", "");
  g_openai = prefs.getString("openai", "");
  g_palakey = prefs.getString("palakey", "");
  g_palahost = prefs.getString("palahost", "");
  g_activeNet = prefs.getInt("activenet", 0);
  prefs.end();
  if (g_activeNet == 1 && g_ssid2.length() == 0) g_activeNet = 0;
  g_loaded = true;
}

static const char *orDefine(const String &v, const char *def) {
  return v.length() ? v.c_str() : def;
}

const char *palaWifiSsidAt(int i) {
  if (i == 1) return g_ssid2.c_str();
  return orDefine(g_ssid, WIFI_SSID);
}
const char *palaWifiPassAt(int i) {
  if (i == 1) return g_pass2.c_str();
  return orDefine(g_pass, WIFI_PASS);
}
const char *palaWifiSsid() { return palaWifiSsidAt(g_activeNet); }
const char *palaWifiPass() { return palaWifiPassAt(g_activeNet); }

bool palaHasSecondNet() { return g_ssid2.length() > 0; }
int palaActiveNet() { return g_activeNet; }

void palaSetActiveNet(int i) {
  if (i != 0 && i != 1) return;
  if (i == 1 && !palaHasSecondNet()) return;
  g_activeNet = i;
  Preferences prefs;
  prefs.begin(PALA_NVS_NS, false);
  prefs.putInt("activenet", i);
  prefs.end();
}

const char *palaOpenAiKey() { return orDefine(g_openai, OPENAI_KEY); }
const char *palaApiKey() { return orDefine(g_palakey, PALA_API_KEY); }
const char *palaApiHost() { return orDefine(g_palahost, PALA_API_HOST); }

bool palaIsProvisioned() { return g_loaded && (g_ssid.length() > 0); }
