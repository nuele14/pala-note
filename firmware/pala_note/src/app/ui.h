#pragma once
#include <Arduino.h>

// Icons
void iconMicWhite(int cx, int cy);
void iconRecordBig(int cx, int cy);
void iconCheck(int cx, int cy, bool filled);
void iconError(int cx, int cy);
void iconThinking(int cx, int cy);
void iconTag(int cx, int cy);
void iconSync(int cx, int cy);
void iconWifi(int cx, int cy);
void iconNoteLines(int cx, int cy);

// Layout helpers
void drawHeader(const char* title, const char* rightInfo = nullptr);
void drawHints(const char* recLabel, const char* pwrLabel);
void drawBadge(int cx, int cy, const char* text, bool filled);
void drawPageDots(int cur, int total);
void drawChevronRight(int x, int cy, uint8_t c);
void drawTinyHint(const char* left, const char* right);
void drawKicker(const char* txt, int y);
void drawSoftFrame();
void drawProductWordmark(int cx, int y, uint8_t color);
void drawModernPill(int x, int y, int w, int h, const char* label, bool active);
void drawDotSelector(int cur, int total, int y);
void drawCheckSmall(int cx, int cy, uint8_t color);
void drawMinimalDocIcon(int cx, int cy, uint8_t color);
void drawMinimalTagIcon(int cx, int cy, uint8_t color);
void drawMinimalCloudIcon(int cx, int cy, uint8_t color);
void drawIconPower(int x, int y, uint8_t color);
void drawIconTriangle(int x, int y, uint8_t color);
void drawIconHoldBack(int x, int y, uint8_t color);
void drawFooterNav(const char* pwrAction, const char* actAction, const char* holdAction = nullptr);

// Screens
void showBootSplash();
void showIdle();
void showBatteryLow(int pct);
void showRecording();
void showSaved(int num);
void showTagSelect(int cursor);
void showMenu(int cursor);
void showShikamaru(int remainingSec, bool isFocus, int sessionNum, bool isPaused);
void showTagBrowser(int cursor);
void showNoteList(int cursor);
void showReaderList(int cursor);
void showReaderArticle(int cursor, int pageIndex);
void showReaderDeleteConfirm(int articleNum, const char* title);
void showNoteActions(int cursor, int actionCursor);
void showNoteDetail(int cursor);
void showNoteMdReader(int cursor, int pageIndex);
void showDeleteConfirm(int noteNum);
void showSyncConfirm(int noteNum);
void showTranscribing(int done, int total);
void showUploading(int done, int total);
void showWifiConnecting(int attempt, int maxA);
void showDone();
void showError(const char* msg);
void showUltraSleepScreen();
void showPlaybackOverlay();
void showTransferConnecting();
void showTransferMode(const char* ip);
void showSyncMode(const char* ssid, const char* ip, int pending);
void showSyncSelect(int cursor);
void showSyncBleMode(const char* bleName, int pending);
void showSyncProgress(const char* proto, const char* statusMsg, int currentItem, int totalItems, int progressPct);
void showSettings(int cursor);
void showDeviceInfo();
