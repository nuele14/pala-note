#include "Arduino.h"
#include "../../config.h"
#include "../../globals.h"
#include "../../types.h"
#include "network.h"
#include "notes.h"
#include "battery.h"
#include "rtc.h"
#include "ui.h"
#include <WiFi.h>
#include <WebServer.h>
#include "SD_MMC.h"
#include "esp_heap_caps.h"

static bool syncDoneFlag = false;

bool isSyncDoneRequested() {
  return syncDoneFlag;
}

void clearSyncDoneRequested() {
  syncDoneFlag = false;
}

String getDeviceId() {
  uint64_t mac = ESP.getEfuseMac();
  char buf[24];
  snprintf(buf, sizeof(buf), "ESP32-%04X", (uint16_t)(mac & 0xFFFF));
  return String(buf);
}

String getSoftApSsid() {
  uint64_t mac = ESP.getEfuseMac();
  char buf[32];
  snprintf(buf, sizeof(buf), "ES1-%04X", (uint16_t)(mac & 0xFFFF));
  return String(buf);
}

static String jsonEscape(const String& in) {
  String out;
  out.reserve(in.length() + 8);
  for (int i = 0; i < (int)in.length(); i++) {
    char c = in[i];
    switch (c) {
      case '"':  out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n";  break;
      case '\r': out += "\\r";  break;
      case '\t': out += "\\t";  break;
      default:
        if ((uint8_t)c < 0x20) { char b[7]; snprintf(b, sizeof(b), "\\u%04x", c); out += b; }
        else                   out += c;
    }
  }
  return out;
}

// ─── REST API Handlers (Mobile App / PC Client) ───────────────────────────

void handleApiInfo() {
  loadIndex();
  int pending = pendingSyncCount();
  int total = (int)noteIndex.size();
  int batPct = readBatteryPercent();

  String json = "{";
  json += "\"device_id\":\"" + jsonEscape(getDeviceId()) + "\",";
  json += "\"firmware_version\":\"" FIRMWARE_VERSION "\",";
  json += "\"battery_percent\":" + String(batPct) + ",";
  json += "\"total_notes\":" + String(total) + ",";
  json += "\"pending_notes\":" + String(pending);
  json += "}";

  transferServer.sendHeader("Access-Control-Allow-Origin", "*");
  transferServer.send(200, "application/json", json);
}

void handleApiNotes() {
  loadIndex();
  String json = "[";
  for (size_t i = 0; i < noteIndex.size(); i++) {
    if (i > 0) json += ",";
    int num = noteIndex[i].num;
    String createdUtc = noteCreatedUtc(num);
    size_t sz = noteAudioFileSize(num);
    float dur = noteAudioDurationSec(num);

    json += "{";
    json += "\"num\":" + String(num) + ",";
    json += "\"tag\":\"" + jsonEscape(String(noteIndex[i].tag)) + "\",";
    if (createdUtc.length()) json += "\"created_utc\":\"" + jsonEscape(createdUtc) + "\",";
    else                     json += "\"created_utc\":null,";
    json += "\"duration_sec\":" + String(dur, 2) + ",";
    json += "\"file_size\":" + String((unsigned long)sz) + ",";
    json += "\"synced\":" + String(noteIndex[i].uploaded ? "true" : "false");
    json += "}";
  }
  json += "]";

  transferServer.sendHeader("Access-Control-Allow-Origin", "*");
  transferServer.send(200, "application/json", json);
}

void handleApiNoteAudio() {
  if (!transferServer.hasArg("num")) {
    transferServer.send(400, "application/json", "{\"error\":\"Missing num parameter\"}");
    return;
  }
  int num = transferServer.arg("num").toInt();
  if (num <= 0) {
    transferServer.send(400, "application/json", "{\"error\":\"Invalid num parameter\"}");
    return;
  }

  char path[64];
  snprintf(path, sizeof(path), "%s/note_%03d.wav", NOTES_DIR, num);
  if (!SD_MMC.exists(path)) {
    transferServer.send(404, "application/json", "{\"error\":\"Audio file not found\"}");
    return;
  }

  File f = SD_MMC.open(path);
  if (!f) {
    transferServer.send(500, "application/json", "{\"error\":\"Failed to open audio file\"}");
    return;
  }

  transferServer.sendHeader("Access-Control-Allow-Origin", "*");
  transferServer.streamFile(f, "audio/wav");
  f.close();
}

void handleApiNoteAck() {
  if (!transferServer.hasArg("num")) {
    transferServer.send(400, "application/json", "{\"error\":\"Missing num parameter\"}");
    return;
  }
  int num = transferServer.arg("num").toInt();
  if (num <= 0) {
    transferServer.send(400, "application/json", "{\"error\":\"Invalid num parameter\"}");
    return;
  }

  markUploaded(num);
  String resp = "{\"status\":\"ok\",\"num\":" + String(num) + ",\"synced\":true}";
  transferServer.sendHeader("Access-Control-Allow-Origin", "*");
  transferServer.send(200, "application/json", resp);
}

void handleApiSyncDone() {
  syncDoneFlag = true;
  transferServer.sendHeader("Access-Control-Allow-Origin", "*");
  transferServer.send(200, "application/json", "{\"status\":\"done\"}");
}

// ─── Web Portal Helpers ───────────────────────────────────────────────────

String htmlEscape(const String& s) {
  String out = s;
  out.replace("&", "&amp;"); out.replace("<", "&lt;");
  out.replace(">", "&gt;"); out.replace("\"", "&quot;");
  return out;
}

String readSmallFile(const char* path, size_t maxLen) {
  File f = SD_MMC.open(path);
  if (!f) return "";
  String out;
  while (f.available() && out.length() < maxLen) out += (char)f.read();
  f.close();
  return out;
}

String urlDecodeSimple(String s) {
  s.replace("+", " ");
  String out = "";
  for (int i = 0; i < (int)s.length(); i++) {
    if (s[i] == '%' && i + 2 < (int)s.length()) {
      String hex = s.substring(i + 1, i + 3);
      out += (char)strtol(hex.c_str(), nullptr, 16);
      i += 2;
    } else {
      out += s[i];
    }
  }
  return out;
}

String portalCss() {
  return String(
    "<style>"
    ":root{font-family:-apple-system,BlinkMacSystemFont,'Inter','Segoe UI',sans-serif;color:#111;background:#f3f0e9;}"
    "body{margin:0;padding:24px;background:#f3f0e9;}"
    ".wrap{max-width:780px;margin:0 auto;}"
    ".top{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:24px;}"
    "h1{font-size:44px;letter-spacing:-.06em;line-height:.9;margin:0;font-weight:800;}"
    ".sub{font-size:13px;text-transform:uppercase;letter-spacing:.12em;color:#6a665f;margin-top:10px;}"
    ".pill{display:inline-flex;border:1px solid #111;border-radius:999px;padding:8px 12px;font-size:13px;background:#fffaf1;}"
    ".grid{display:grid;grid-template-columns:1fr;gap:14px;}"
    ".card{background:#fffaf1;border:1.5px solid #111;border-radius:24px;padding:18px;box-shadow:4px 4px 0 #111;}"
    ".row{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;}"
    ".num{font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:#6a665f;margin-bottom:8px;}"
    ".date{font-size:13px;color:#6a665f;margin:-4px 0 12px;}"
    ".title{font-size:24px;line-height:1.05;letter-spacing:-.04em;font-weight:750;margin:0 0 12px;}"
    ".tag{border:1px solid #111;border-radius:999px;padding:5px 9px;font-size:12px;white-space:nowrap;background:#111;color:#fff;}"
    ".text{font-size:15px;line-height:1.45;color:#222;margin:0 0 14px;white-space:pre-wrap;}"
    ".actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px;}"
    "a.btn{color:#111;text-decoration:none;border:1px solid #111;border-radius:999px;padding:8px 12px;background:#f3f0e9;font-size:13px;}"
    "a.btn.primary{background:#111;color:#fff;}"
    ".empty{border:1.5px dashed #111;border-radius:24px;padding:34px;text-align:center;color:#6a665f;}"
    "audio{width:100%;margin-top:8px;}"
    "@media(max-width:520px){body{padding:16px}h1{font-size:36px}.card{border-radius:20px}.title{font-size:21px}}"
    "</style>"
  );
}

// ─── Web Portal Handlers ──────────────────────────────────────────────────

void handlePortalRoot() {
  loadIndex();

  String filter = "All";
  if (transferServer.hasArg("tag")) filter = transferServer.arg("tag");

  String html = "<!doctype html><html><head><meta charset='utf-8'>"
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                "<title>ES1 Portal — Extransformer Shield Uno</title>" + portalCss() + "</head><body><div class='wrap'>";

  html += "<div class='top'><div><h1>ES1<br>portal</h1>"
          "<div class='sub'>local note transfer · <a href=\"/tags\" style=\"color:inherit\">tags</a></div></div>"
          "<div class='pill'>" + String((int)noteIndex.size()) + " notes (" + String(pendingSyncCount()) + " unsynced)</div></div>";

  html += "<div class='actions' style='margin-bottom:18px'>";
  html += "<a class='btn " + String(filter == "All" ? "primary" : "") + "' href='/'>All</a>";
  for (int t = 0; t < tagCount; t++) {
    String tag = String(tags[t]);
    html += "<a class='btn " + String(filter == tag ? "primary" : "") + "' href='/?tag=" + tag + "'>" + htmlEscape(tag) + "</a>";
  }
  html += "</div>";

  int visibleCount = 0;
  for (int i = 0; i < (int)noteIndex.size(); i++)
    if (filter == "All" || filter == String(noteIndex[i].tag)) visibleCount++;

  if (visibleCount <= 0) {
    html += "<div class='empty'>No notes recorded yet.</div>";
  } else {
    html += "<div class='grid'>";
    for (int v = 0; v < (int)noteIndex.size(); v++) {
      int i = (int)noteIndex.size() - 1 - v;
      if (!(filter == "All" || filter == String(noteIndex[i].tag))) continue;
      int num = noteIndex[i].num;

      char wavPath[64];
      snprintf(wavPath, sizeof(wavPath), "%s/note_%03d.wav", NOTES_DIR, num);

      float dur = noteAudioDurationSec(num);
      size_t sz = noteAudioFileSize(num);

      String title = String("Voice note #") + String(num) + " (" + String(dur, 1) + "s)";

      html += "<div class='card'>";
      html += "<div class='row'><div><div class='num'>#" + String(num) + (noteIndex[i].uploaded ? " · synced" : " · unsynced") + "</div>";
      html += "<h2 class='title'>" + htmlEscape(title) + "</h2>";
      String createdUtc = noteCreatedUtc(num);
      if (createdUtc.length() > 0)
        html += "<div class='date' data-utc='" + createdUtc + "'>" + createdUtc + "</div>";
      else
        html += "<div class='date'>time not set</div>";
      html += "</div>";
      html += "<div class='tag'>" + htmlEscape(String(noteIndex[i].tag)) + "</div></div>";

      if (SD_MMC.exists(wavPath)) {
        html += "<audio controls src='/api/notes/audio?num=" + String(num) + "'></audio>";
      }
      html += "<div class='actions'>";
      if (SD_MMC.exists(wavPath)) {
        html += "<a class='btn primary' href='/wav?num=" + String(num) + "'>Download WAV (" + String((unsigned long)(sz / 1024)) + " KB)</a>";
      }
      html += "<a class='btn' style='margin-left:auto;color:#c0392b;border-color:#c0392b' "
              "href='/note/delete?num=" + String(num) + "' "
              "onclick=\"return confirm('Delete note #" + String(num) + "? This cannot be undone.')\">Delete</a>";
      html += "</div></div>";
    }
    html += "</div>";
  }

  html += "<script>"
          "document.querySelectorAll('[data-utc]').forEach(function(el){"
          "var d=new Date(el.dataset.utc);"
          "if(!isNaN(d)){el.textContent=d.toLocaleString([],{year:'numeric',month:'short',day:'2-digit',hour:'2-digit',minute:'2-digit'});}"
          "});"
          "</script>";
  html += "</div></body></html>";
  transferServer.send(200, "text/html", html);
}

void handleExportTxt() {
  loadIndex();
  String filter = "All";
  if (transferServer.hasArg("tag")) filter = transferServer.arg("tag");

  String exportText = "Pala Note Audio Manifest\nFilter: " + filter + "\n------------------------------\n\n";

  for (int v = 0; v < (int)noteIndex.size(); v++) {
    int i = (int)noteIndex.size() - 1 - v;
    if (!(filter == "All" || filter == String(noteIndex[i].tag))) continue;
    int num = noteIndex[i].num;
    exportText += "#";
    if (num < 100) exportText += "0";
    if (num < 10)  exportText += "0";
    exportText += String(num) + " · " + String(noteIndex[i].tag) + "\n";
    String createdUtc = noteCreatedUtc(num);
    if (createdUtc.length() > 0) exportText += "Created: " + createdUtc + "\n";
    exportText += "Duration: " + String(noteAudioDurationSec(num), 1) + "s\n";
    exportText += "Synced: " + String(noteIndex[i].uploaded ? "Yes" : "No") + "\n\n------------------------------\n\n";
  }

  String filename = "pala_notes_manifest.txt";
  transferServer.sendHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
  transferServer.send(200, "text/plain", exportText);
}

void sendFileByNum(const char* ext, const char* mime, bool attachment) {
  if (!transferServer.hasArg("num")) { transferServer.send(400, "text/plain", "Missing num"); return; }
  int num = transferServer.arg("num").toInt();
  if (num <= 0) { transferServer.send(400, "text/plain", "Invalid num"); return; }
  char path[64]; snprintf(path, sizeof(path), "%s/note_%03d.%s", NOTES_DIR, num, ext);
  File f = SD_MMC.open(path);
  if (!f) { transferServer.send(404, "text/plain", "File not found"); return; }
  if (attachment) {
    String filename = String("note_") + String(num) + "." + String(ext);
    transferServer.sendHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
  }
  transferServer.streamFile(f, mime);
  f.close();
}

void handleTagAdd() {
  if (!transferServer.hasArg("name")) {
    transferServer.sendHeader("Location", "/tags?msg=missing");
    transferServer.send(303); return;
  }
  String name = urlDecodeSimple(transferServer.arg("name"));
  bool ok = addCustomTag(name.c_str());
  transferServer.sendHeader("Location", ok ? "/tags?msg=added" : "/tags?msg=exists");
  transferServer.send(303);
}

void handleTagDelete() {
  if (!transferServer.hasArg("name")) {
    transferServer.sendHeader("Location", "/tags?msg=missing");
    transferServer.send(303); return;
  }
  String name = urlDecodeSimple(transferServer.arg("name"));
  bool hadNotes = tagHasNotes(name.c_str());
  bool ok = deleteTag(name.c_str());
  if (ok && hadNotes) transferServer.sendHeader("Location", "/tags?msg=moved");
  else                transferServer.sendHeader("Location", ok ? "/tags?msg=deleted" : "/tags?msg=protected");
  transferServer.send(303);
}

void handleTagsPage() {
  loadTags();
  loadIndex();
  activeFilter = -1;

  String html = "<!doctype html><html><head><meta charset='utf-8'>"
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                "<title>Pala Tags</title>"
                "<style>"
                "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;padding:24px;background:#f3f0e9;color:#111}"
                ".wrap{max-width:720px;margin:0 auto}"
                "h1{font-size:42px;line-height:.9;letter-spacing:-.05em;margin:0 0 22px;font-weight:800}"
                ".card{background:#fffaf1;border:1.5px solid #111;border-radius:24px;padding:18px;margin:14px 0;box-shadow:4px 4px 0 #111}"
                ".row{display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid #ddd;padding:12px 0}"
                ".row:first-child{border-top:0}"
                ".tag{font-size:20px;font-weight:700}"
                ".meta{font-size:13px;color:#666;margin-top:4px}"
                "input{font:inherit;padding:12px;border:1.5px solid #111;border-radius:999px;background:#fff;width:100%;box-sizing:border-box}"
                "button,.btn{font:inherit;border:1.5px solid #111;border-radius:999px;padding:10px 14px;background:#111;color:#fff;text-decoration:none;white-space:nowrap}"
                ".danger{background:#fffaf1;color:#111}"
                ".msg{border:1.5px solid #111;border-radius:18px;padding:12px 14px;background:#fff;margin:12px 0}"
                ".hint{font-size:13px;color:#666;line-height:1.4}"
                "form.add{display:flex;gap:10px}"
                "</style></head><body><div class='wrap'>";

  html += "<h1>pala<br>tags</h1>";
  html += "<a class='btn' href='/'>Back to notes</a>";

  if (transferServer.hasArg("msg")) {
    String msg = transferServer.arg("msg");
    html += "<div class='msg'>";
    if (msg == "added") html += "Tag added.";
    else if (msg == "exists")    html += "Tag already exists or cannot be added.";
    else if (msg == "deleted")   html += "Tag deleted.";
    else if (msg == "moved")     html += "Tag deleted. Existing notes were moved to Untagged.";
    else if (msg == "protected") html += "This tag cannot be deleted.";
    else html += "Please enter a tag name.";
    html += "</div>";
  }

  html += "<div class='card'><form class='add' action='/tag/add' method='get'>"
          "<input name='name' maxlength='31' placeholder='New tag name'>"
          "<button type='submit'>Add</button></form>"
          "<p class='hint'>Tags appear on the device after recording. Keep them short for the e-paper UI.</p></div>";

  html += "<div class='card'>";
  for (int i = 0; i < tagCount; i++) {
    int cnt = 0;
    for (int n = 0; n < (int)noteIndex.size(); n++)
      if (strcmp(noteIndex[n].tag, tags[i]) == 0) cnt++;
    html += "<div class='row'><div><div class='tag'>" + htmlEscape(String(tags[i])) + "</div>";
    html += "<div class='meta'>" + String(cnt) + (cnt == 1 ? " note" : " notes");
    if (cnt > 0) html += " · deleting moves them to Untagged";
    html += "</div></div>";
    if (strcasecmp(tags[i], "Untagged") != 0) {
      html += "<a class='btn danger' href='/tag/delete?name=" + htmlEscape(String(tags[i])) + "' "
              "onclick=\"return confirm('Delete this tag? Notes will not be deleted. Existing notes will move to Untagged.');\">Delete</a>";
    }
    html += "</div>";
  }
  html += "</div></div></body></html>";
  transferServer.send(200, "text/html", html);
}

void handleNoteDelete() {
  if (!transferServer.hasArg("num")) { transferServer.send(400, "text/plain", "Missing num"); return; }
  int num = transferServer.arg("num").toInt();
  if (num <= 0) { transferServer.send(400, "text/plain", "Invalid num"); return; }
  deleteNote(num);
  transferServer.sendHeader("Location", "/");
  transferServer.send(303);
}

// ─── Setup & Lifecycle ────────────────────────────────────────────────────

void setupTransferServer() {
  // REST API routes for mobile app & PC sync
  transferServer.on("/api/info",        HTTP_GET,  handleApiInfo);
  transferServer.on("/api/notes",       HTTP_GET,  handleApiNotes);
  transferServer.on("/api/notes/audio", HTTP_GET,  handleApiNoteAudio);
  transferServer.on("/api/notes/ack",   HTTP_ANY,  handleApiNoteAck);
  transferServer.on("/api/sync/done",   HTTP_ANY,  handleApiSyncDone);

  // Web portal routes
  transferServer.on("/",                HTTP_GET,  handlePortalRoot);
  transferServer.on("/tags",            HTTP_GET,  handleTagsPage);
  transferServer.on("/tag/add",         HTTP_GET,  handleTagAdd);
  transferServer.on("/tag/delete",      HTTP_GET,  handleTagDelete);
  transferServer.on("/note/delete",     HTTP_GET,  handleNoteDelete);
  transferServer.on("/export.txt",      HTTP_GET,  handleExportTxt);
  transferServer.on("/txt",             HTTP_GET,  [](){ sendFileByNum("txt", "text/plain", true); });
  transferServer.on("/wav",             HTTP_GET,  [](){ sendFileByNum("wav", "audio/wav",  true); });
  transferServer.on("/audio",           HTTP_GET,  handleApiNoteAudio);

  transferServer.onNotFound([](){
    transferServer.sendHeader("Access-Control-Allow-Origin", "*");
    transferServer.send(404, "text/plain", "Not found");
  });
}

void startSoftApSync() {
  syncDoneFlag = false;
  WiFi.mode(WIFI_AP);
  String ssid = getSoftApSsid();
  WiFi.softAP(ssid.c_str());
  delay(50);

  setupTransferServer();
  transferServer.begin();
  transferServerActive = true;
  transferUrl = "192.168.4.1";
  Serial.printf("[SoftAP] Started %s at %s\n", ssid.c_str(), transferUrl.c_str());
}

void stopTransferMode() {
  syncDoneFlag = false;
  if (transferServerActive) {
    transferServer.stop();
    transferServerActive = false;
  }
  WiFi.softAPdisconnect(true);
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
  transferUrl = "";
  Serial.println("[SoftAP] Stopped");
}
