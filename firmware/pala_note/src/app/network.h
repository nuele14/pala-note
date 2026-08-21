#pragma once
#include <Arduino.h>

// Device identity & SoftAP info
String getDeviceId();
String getSoftApSsid();

// HTML / String helpers
String htmlEscape(const String& s);
String readSmallFile(const char* path, size_t maxLen = 1600);
String urlDecodeSimple(String s);
String portalCss();

// REST API handlers (for Mobile App / PC sync)
void handleApiInfo();
void handleApiNotes();
void handleApiNoteAudio();
void handleApiNoteAck();
void handleApiSyncDone();

// Web Portal handlers (for direct browser access)
void handlePortalRoot();
void handleExportTxt();
void sendFileByNum(const char* ext, const char* mime, bool attachment);
void handleTagsPage();
void handleTagAdd();
void handleTagDelete();
void handleNoteDelete();

// Server lifecycle
void setupTransferServer();
void startSoftApSync();
void stopTransferMode();

// Sync status flag (triggered when client finishes sync)
bool isSyncDoneRequested();
void clearSyncDoneRequested();
