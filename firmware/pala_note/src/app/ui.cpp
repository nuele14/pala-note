#include "Arduino.h"
#include "../../config.h"
#include "../../globals.h"
#include "../../types.h"
#include "ui.h"
#include "draw.h"
#include "notes.h"
#include "battery.h"
#include "provisioning.h"
#include "rtc.h"
#include "md_reader.h"
#include "reader.h"
#include "../../logo_bitmap.h"
#include "../../ready_bitmap.h"
#include "../../recording_bitmap.h"
#include "../../pomodoro_bitmap.h"
#include "../../sounds.h"
#include "SD_MMC.h"

#define W   200
#define H   200

// ─── Icons ────────────────────────────────────────────────────────────────

void iconMicWhite(int cx, int cy) {
  fillRect(cx-13, cy-36, 26, 44, WHITE);
  fillCircle(cx, cy-36, 13, WHITE);
  fillCircle(cx, cy+8,  13, WHITE);
  strokeCircle(cx, cy-4, 40, 5, WHITE);
  fillRect(cx-50, cy-50, 100, 50, BLACK);
  fillRect(cx-3,  cy+38, 6,  18, WHITE);
  fillRect(cx-24, cy+54, 48,  5, WHITE);
}

void iconRecordBig(int cx, int cy) {
  fillCircle(cx, cy, 36, WHITE);
  strokeCircle(cx, cy, 52, 5, WHITE);
  strokeCircle(cx, cy, 68, 2, WHITE);
}

void iconCheck(int cx, int cy, bool filled) {
  if (filled) {
    fillCircle(cx, cy, 44, BLACK);
    for (int t=-3;t<=3;t++) {
      line(cx-22, cy-2+t, cx-6, cy+17+t, WHITE);
      line(cx-6,  cy+17+t, cx+30, cy-22+t, WHITE);
    }
  } else {
    strokeCircle(cx, cy, 44, 3, BLACK);
    for (int t=-2;t<=2;t++) {
      line(cx-22, cy-2+t, cx-6, cy+17+t, BLACK);
      line(cx-6,  cy+17+t, cx+30, cy-22+t, BLACK);
    }
  }
}

void iconError(int cx, int cy) {
  strokeCircle(cx, cy, 44, 3, BLACK);
  for (int t=-3;t<=3;t++) {
    line(cx-22, cy-22+t, cx+22, cy+22+t, BLACK);
    line(cx+22, cy-22+t, cx-22, cy+22+t, BLACK);
  }
}

void iconThinking(int cx, int cy) {
  fillCircle(cx-28, cy, 8, BLACK);
  fillCircle(cx,    cy, 8, BLACK);
  fillCircle(cx+28, cy, 8, BLACK);
}

void iconTag(int cx, int cy) {
  const int pts[5][2] = {
    {cx-26, cy+4}, {cx-4, cy-18}, {cx+32, cy-18},
    {cx+32, cy+12}, {cx+4,  cy+36}
  };
  for(int i=0;i<4;i++) thickLine(pts[i][0],pts[i][1],pts[i+1][0],pts[i+1][1],4,BLACK);
  thickLine(pts[4][0],pts[4][1],pts[0][0],pts[0][1],4,BLACK);
  fillCircle(cx-2, cy-4, 5, BLACK);
}

void iconSync(int cx, int cy) {
  strokeCircle(cx, cy, 40, 4, BLACK);
  fillRect(cx+16, cy-46, 20, 20, WHITE);
  thickLine(cx+16, cy-36, cx+36, cy-36, 3, BLACK);
  thickLine(cx+36, cy-36, cx+26, cy-46, 3, BLACK);
  thickLine(cx+36, cy-36, cx+26, cy-26, 3, BLACK);
  fillRect(cx-36, cy+26, 20, 20, WHITE);
  thickLine(cx-36, cy+36, cx-16, cy+36, 3, BLACK);
  thickLine(cx-16, cy+36, cx-26, cy+26, 3, BLACK);
  thickLine(cx-16, cy+36, cx-26, cy+46, 3, BLACK);
}

void iconWifi(int cx, int cy) {
  int base = cy + 26;
  strokeCircle(cx, base, 50, 5, BLACK);
  strokeCircle(cx, base, 32, 5, BLACK);
  strokeCircle(cx, base, 14, 5, BLACK);
  fillRect(0, base, W, H - base, WHITE);
  fillCircle(cx, base, 5, BLACK);
}

void iconNoteLines(int cx, int cy) {
  fillRect(cx-32, cy-12, 64, 6, BLACK);
  fillRect(cx-32, cy+2,  64, 6, BLACK);
  fillRect(cx-32, cy+16, 44, 6, BLACK);
}

// ─── Layout helpers ────────────────────────────────────────────────────────

void drawHeader(const char* title, const char* rightInfo) {
  fillRect(0, 0, W, 28, BLACK);
  drawStrC(W/2, 10, title, 1, WHITE);
  if (rightInfo) {
    int rw = textW(rightInfo, 1);
    drawStr(W - 8 - rw, 10, rightInfo, 1, WHITE);
  }
}

void drawHints(const char* recLabel, const char* pwrLabel) {
  hline(0, 179, W, BLACK);
  fillRect(0, 180, W, 20, WHITE);
  drawStr(8, 186, recLabel, 1, BLACK);
  int rw = textW(pwrLabel, 1);
  drawStr(W - 8 - rw, 186, pwrLabel, 1, BLACK);
}

void drawBadge(int cx, int cy, const char* text, bool filled) {
  char up[32]; uppercaseCopy(up, text, sizeof(up));
  int tw = textW(up, 1);
  int bw = tw + 20, bh = 20;
  int bx = cx - bw/2, by = cy - bh/2;
  if (filled) {
    fillRoundRect(bx, by, bw, bh, 9, BLACK);
    drawStrC(cx, by + 6, up, 1, WHITE);
  } else {
    strokeRoundRect(bx, by, bw, bh, 9, 2, BLACK);
    drawStrC(cx, by + 6, up, 1, BLACK);
  }
}

void drawPageDots(int cur, int total) {
  if (total <= 1) return;
  int n = min(total, 7);
  int gap = 16;
  int startX = W/2 - ((n-1)*gap)/2;
  for (int i = 0; i < n; i++) {
    int x = startX + i*gap, y = 168;
    if (i == cur % n) fillCircle(x, y, 5, BLACK);
    else              strokeCircle(x, y, 4, 1, BLACK);
  }
}

void drawChevronRight(int x, int cy, uint8_t c) {
  thickLine(x,   cy-8, x+8, cy,   2, c);
  thickLine(x+8, cy,   x,   cy+8, 2, c);
}

void drawTinyHint(const char* left, const char* right) {
  (void)left; (void)right;
}

void drawKicker(const char* txt, int y) {
  char up[40]; uppercaseCopy(up, txt, sizeof(up));
  drawStrC(W/2, y, up, 1, BLACK);
}

void drawSoftFrame() {
  strokeRoundRect(12, 12, W-24, H-24, 10, 1, BLACK);
}

void drawProductWordmark(int cx, int y, uint8_t color) {
  drawStr(cx - textW("ES1", 2) / 2, y,      "ES1", 2, color);
  drawStr(cx - textW("uno", 1) / 2, y + 24, "uno", 1, color);
}

void drawModernPill(int x, int y, int w, int h, const char* label, bool active) {
  if (active) {
    fillRoundRect(x, y, w, h, h/2, BLACK);
    drawStrInBox(x, y, w, h, label, 1, WHITE);
  } else {
    strokeRoundRect(x, y, w, h, h/2, 1, BLACK);
    drawStrInBox(x, y, w, h, label, 1, BLACK);
  }
}

void drawDotSelector(int cur, int total, int y) {
  int gap = 17, startX = W/2 - ((total-1)*gap)/2;
  for (int i=0; i<total; i++) {
    int x = startX + i*gap;
    if (i == cur) fillCircle(x, y, 4, BLACK);
    else          strokeCircle(x, y, 4, 1, BLACK);
  }
}

void drawCheckSmall(int cx, int cy, uint8_t color) {
  strokeCircle(cx, cy, 13, 1, color);
  thickLine(cx-6, cy, cx-1, cy+5, 2, color);
  thickLine(cx-1, cy+5, cx+8, cy-6, 2, color);
}

void drawMinimalDocIcon(int cx, int cy, uint8_t color) {
  strokeRoundRect(cx-13, cy-16, 26, 32, 3, 2, color);
  hline(cx-7, cy-5, 14, color);
  hline(cx-7, cy+4, 14, color);
  hline(cx-7, cy+13, 9, color);
}

void drawMinimalTagIcon(int cx, int cy, uint8_t color) {
  thickLine(cx-13, cy, cx-2, cy-13, 2, color);
  thickLine(cx-2, cy-13, cx+14, cy-13, 2, color);
  thickLine(cx+14, cy-13, cx+14, cy+2, 2, color);
  thickLine(cx+14, cy+2, cx+2, cy+15, 2, color);
  thickLine(cx+2, cy+15, cx-13, cy, 2, color);
  fillCircle(cx+4, cy-5, 3, color);
}

void drawMinimalCloudIcon(int cx, int cy, uint8_t color) {
  strokeCircle(cx-8, cy+2, 10, 2, color);
  strokeCircle(cx+4, cy-4, 13, 2, color);
  strokeCircle(cx+15, cy+4, 9, 2, color);
  fillRect(cx-22, cy+4, 47, 16, WHITE);
  hline(cx-21, cy+10, 44, color);
}

void drawMenuTile(int x, int y, int w, int h, const char* label, int icon, bool active) {
  if (active) fillRoundRect(x, y, w, h, 12, BLACK);
  else        strokeRoundRect(x, y, w, h, 12, 1, BLACK);
  uint8_t col = active ? WHITE : BLACK;
  int cx = x + w/2;
  fillCircle(cx, y + 17, 4, col);
  drawStrInBox(x + 4, y + 29, w - 8, 18, label, 1, col);
}

void drawNoteCard(int y, int idx, bool active) {
  const int x = 16, w = 168, h = 39;
  if (active) fillRoundRect(x, y, w, h, 8, BLACK);
  else        strokeRoundRect(x, y, w, h, 8, 1, BLACK);
  uint8_t col = active ? WHITE : BLACK;

  char n[8]; snprintf(n, sizeof(n), "#%03d", noteIndex[idx].num);
  String tagLabel = normalizeForDisplay(String(noteIndex[idx].tag));
  drawStr(x + 10, y + 5, n, 1, col);
  drawStrFit(x + 66, y + 5, 88, tagLabel.c_str(), 1, col);
  String ticker = noteTickerText(idx);
  drawTickerText(x + 10, y + 22, 145, ticker, active, col);
}

void drawListMenuCard(int y, const char* title, const char* meta, bool active) {
  const int x = 16, w = 168, h = 32;
  if (active) fillRoundRect(x, y, w, h, 8, BLACK);
  else        strokeRoundRect(x, y, w, h, 8, 1, BLACK);
  uint8_t col = active ? WHITE : BLACK;
  drawStrFit(x + 10, y + 8, meta ? 92 : 140, title, 1, col);
  if (meta && strlen(meta) > 0) {
    int mw = min(textW(meta, 1), 56);
    drawStrFit(x + w - 10 - mw, y + 8, 56, meta, 1, col);
  }
}

void drawIconPower(int x, int y, uint8_t color) {
  int cx = x + 3, cy = y + 4;
  strokeCircle(cx, cy, 3, 1, color);
  fillRect(cx - 1, y, 3, 2, WHITE);
  vline(cx, y, 4, color);
}

void drawIconTriangle(int x, int y, uint8_t color) {
  fillTriangle(x, y, x, y + 6, x + 5, y + 3, color);
}

void drawFooterNav(const char* pwrAction, const char* actAction, const char* holdAction) {
  hline(0, 166, W, BLACK);
  fillRect(0, 167, W, 33, WHITE);

  if (pwrAction) {
    drawIconPower(12, 172, BLACK);
    drawStr(22, 172, pwrAction, 1, BLACK);
  }

  if (actAction) {
    int actW = textW(actAction, 1);
    drawIconTriangle(W - 12 - actW - 8, 172, BLACK);
    drawStr(W - 12 - actW, 172, actAction, 1, BLACK);
  }

  if (holdAction) {
    drawStr(12, 186, "hold", 1, BLACK);
    drawIconTriangle(38, 186, BLACK);
    char buf[32];
    snprintf(buf, sizeof(buf), ": %s", holdAction);
    drawStr(46, 186, buf, 1, BLACK);
  }
}

// ─── Screens ──────────────────────────────────────────────────────────────

void showBootSplash() {
  clearWhite();
  drawBitmap1BPP(0, 0, logo_bitmap, 200, 200, BLACK);
  refresh();
}

void showIdle() {
  clearWhite();
  #if USE_CUSTOM_READY_BITMAP
  drawBitmap1BPP(0, 0, ready_bitmap, 200, 200, BLACK);
  #if SHOW_BATTERY_ON_READY
  int batt = readBatteryPercent();
  char bbuf[8]; snprintf(bbuf, sizeof(bbuf), "%d%%", batt);
  drawStr(165, 8, bbuf, 1, BLACK);
  #endif
  #else
  int batt = readBatteryPercent();
  drawBatteryRing(batt);
  drawProductWordmark(100, 58, BLACK);
  fillCircle(100, 123, 5, BLACK);
  drawStrC(100, 144, "ready", 1, BLACK);
  #endif
  refresh();
}

void showBatteryLow(int pct) {
  fillRect(0, 0, W, H, BLACK);
  fillRect(95, 48, 10, 50, WHITE);
  fillRect(95, 108, 10, 10, WHITE);
  char buf[8]; snprintf(buf, sizeof(buf), "%d%%", pct);
  drawStrC(100, 132, buf,       2, WHITE);
  drawStrC(100, 160, "battery", 1, WHITE);
  drawStrC(100, 176, "low",     1, WHITE);
  refresh();
}

void showRecording() {
  #if USE_CUSTOM_RECORDING_BITMAP
  clearWhite();
  drawBitmap1BPP(0, 0, recording_bitmap, 200, 200, BLACK);
  #else
  fillRect(0, 0, W, H, BLACK);
  fillCircle(W/2, H/2, 27, WHITE);
  #endif
  refresh();
}

void showSaved(int num) {
  clearWhite();
  drawCheckSmall(100, 46, BLACK);
  drawStrC(100, 76, "saved", 1, BLACK);
  char b[8]; snprintf(b, sizeof(b), "#%03d", num);
  drawStrC(100, 105, b, 2, BLACK);
  refresh();
}

void showTagSelect(int cursor) {
  clearWhite();
  if (tagCount <= 0) {
    drawKicker("no tags", 34);
    drawStrC(100, 100, "open portal", 1, BLACK);
    refresh();
    return;
  }
  drawKicker("choose tag", 17);
  const int x = 36, w = 128, h = 21, gap = 7;
  int y0 = 40;
  cursor = constrain(cursor, 0, max(tagCount - 1, 0));
  for (int i=0; i<tagCount; i++) {
    int y = y0 + i*(h+gap);
    drawModernPill(x, y, w, h, tags[i], i == cursor);
  }
  drawFooterNav("next", "select");
  refresh();
}

void showMenu(int cursor) {
  clearWhite();
  drawStr(16, 11, "menu", 1, BLACK);
  drawBatteryMicroBadge(154, 11, readBatteryPercent(), BLACK);
  hline(16, 24, W-32, BLACK);
  const int y0 = 30, step = 26, itemH = 22;
  for (int row = 0; row < MENU_COUNT; row++) {
    bool active = (row == cursor);
    int y = y0 + row * step;
    if (active) fillRoundRect(16, y, 168, itemH, 4, BLACK);
    else        strokeRoundRect(16, y, 168, itemH, 4, 1, BLACK);
    uint8_t col = active ? WHITE : BLACK;
    drawStrInBox(16, y, 168, itemH, MENU_ITEMS[row], 1, col);
  }
  drawFooterNav("next", "select", "standby");
  refresh();
}

void showShikamaru(int remainingSec, bool isFocus, int sessionNum, bool isPaused) {
  clearWhite();

  // 1. Immagine Pomodoro al centro (200x200 1-bit)
  #if USE_CUSTOM_POMODORO_BITMAP
  drawBitmap1BPP(0, 0, pomodoro_bitmap, 200, 200, BLACK);
  #else
  fillCircle(100, 75, 36, BLACK);
  fillRect(96, 32, 8, 12, BLACK);
  #endif

  // 2. Formattazione tempo (in basso a sinistra in grande)
  char timeBuf[12];
  if (remainingSec >= 60) {
    int mins = (remainingSec + 59) / 60;
    snprintf(timeBuf, sizeof(timeBuf), "%dm", mins);
  } else if (remainingSec > 0) {
    if (remainingSec >= 50)      snprintf(timeBuf, sizeof(timeBuf), ":50");
    else if (remainingSec >= 40) snprintf(timeBuf, sizeof(timeBuf), ":40");
    else if (remainingSec >= 30) snprintf(timeBuf, sizeof(timeBuf), ":30");
    else if (remainingSec >= 20) snprintf(timeBuf, sizeof(timeBuf), ":20");
    else if (remainingSec >= 10) snprintf(timeBuf, sizeof(timeBuf), ":10");
    else                         snprintf(timeBuf, sizeof(timeBuf), ":%02d", remainingSec);
  } else {
    snprintf(timeBuf, sizeof(timeBuf), "fine");
  }

  // 3. Maschera bianca inferiore per leggibilità e contrasto perfetto dei testi
  fillRect(0, 154, W, 46, WHITE);
  hline(0, 154, W, BLACK);

  // In basso a sinistra in grande (timeBuf)
  drawStr(12, 160, timeBuf, 2, BLACK);

  // In basso a destra: 4 pallini per i round
  int startX = 146, gap = 11, dy = 168;
  for (int i = 1; i <= 4; i++) {
    int x = startX + (i - 1) * gap;
    if (i < sessionNum || (i == sessionNum && !isFocus)) {
      fillCircle(x, dy, 4, BLACK);
    } else if (i == sessionNum && isFocus) {
      fillCircle(x, dy, 4, BLACK);
      strokeCircle(x, dy, 6, 1, BLACK);
    } else {
      strokeCircle(x, dy, 4, 1, BLACK);
    }
  }

  // In centro in piccolo sotto (stato)
  const char* statusStr;
  if (isPaused) {
    statusStr = "pause";
  } else if (isFocus) {
    statusStr = "...deep focus...";
  } else {
    statusStr = "...break...";
  }
  drawStrC(100, 184, statusStr, 1, BLACK);

  refresh();
}

void showTagBrowser(int cursor) {
  clearWhite();
  if (tagCount <= 0) {
    drawKicker("tags", 16);
    drawStrC(100, 100, "no tags", 1, BLACK);
    refresh();
    return;
  }
  drawKicker("tags", 16);
  fillRoundRect(28, 56, 144, 54, 17, BLACK);
  cursor = constrain(cursor, 0, max(tagCount - 1, 0));
  drawStrInBox(28, 56, 144, 54, tags[cursor], 2, WHITE);
  int cnt = 0;
  for (int i=0; i<(int)noteIndex.size(); i++)
    if (strcmp(noteIndex[i].tag, tags[cursor])==0) cnt++;
  char cb[20]; snprintf(cb, sizeof(cb), "%d notes", cnt);
  drawStrC(100, 130, cb, 1, BLACK);
  drawFooterNav("next", "select", "menu");
  refresh();
}

void showNoteList(int cursor) {
  clearWhite();
  int count = filteredCount();
  char cb[20]; snprintf(cb, sizeof(cb), "notes (%d)", count);
  drawStr(16, 12, cb, 1, BLACK);
  drawBatteryMicroBadge(154, 12, readBatteryPercent(), BLACK);
  hline(16, 26, W - 32, BLACK);

  if (count <= 0) {
    drawMinimalDocIcon(100, 76, BLACK);
    drawStrC(100, 116, "no notes yet", 1, BLACK);
    drawFooterNav("back", "back", "menu");
    refresh();
    return;
  }

  const int pageSize = 5;
  int pageStart = (cursor / pageSize) * pageSize;
  int activeRow = cursor - pageStart;
  const int y0 = 32, step = 25, itemH = 22;
  int shown = min(pageSize, count - pageStart);

  for (int row = 0; row < shown; row++) {
    int vis = pageStart + row;
    int idx = noteAtFilteredIndex(vis);
    if (idx < 0) continue;

    bool active = (row == activeRow);
    int y = y0 + row * step;
    uint8_t col = active ? WHITE : BLACK;

    if (active) {
      fillRoundRect(16, y, 168, itemH, 5, BLACK);
    }

    char n[8]; snprintf(n, sizeof(n), "#%03d", noteIndex[idx].num);
    drawStr(active ? 22 : 18, y + 5, n, 1, col);

    String tagLabel = normalizeForDisplay(String(noteIndex[idx].tag));
    drawStrFit(active ? 66 : 62, y + 5, 68, tagLabel.c_str(), 1, col);

    if (noteIndex[idx].uploaded) {
      drawStr(W - 46, y + 5, "ok", 1, col);
    } else {
      drawStr(W - 46, y + 5, "--", 1, col);
    }

    if (noteIndex[idx].hasText) {
      drawStr(W - 26, y + 5, "md", 1, col);
    } else {
      drawStr(W - 26, y + 5, "wav", 1, col);
    }
  }

  drawFooterNav("next", "select", "menu");
  refresh();
}

void showReaderList(int cursor) {
  clearWhite();
  int total = articleCount();

  drawStr(16, 11, "reader", 1, BLACK);
  drawBatteryMicroBadge(154, 11, readBatteryPercent(), BLACK);

  char countStr[16];
  if (total > 0) {
    snprintf(countStr, sizeof(countStr), "%d/%d", cursor + 1, total);
  } else {
    snprintf(countStr, sizeof(countStr), "0/0");
  }
  int cw = textW(countStr, 1);
  drawStr(146 - cw, 11, countStr, 1, BLACK);

  hline(16, 24, W - 32, BLACK);

  if (total == 0) {
    iconThinking(100, 75);
    drawStrC(100, 110, "No articles", 1, BLACK);
    drawStrC(100, 126, "Push articles from", 1, BLACK);
    drawStrC(100, 138, "the mobile app!", 1, BLACK);
    drawFooterNav("back", "menu", "standby");
    refresh();
    return;
  }

  // Display 4 items per page window
  int pageSize = 4;
  int startIdx = (cursor / pageSize) * pageSize;
  const int y0 = 30, step = 31, itemH = 27;

  for (int i = 0; i < pageSize && (startIdx + i) < total; i++) {
    int idx = startIdx + i;
    bool active = (idx == cursor);
    int y = y0 + i * step;
    uint8_t col = active ? WHITE : BLACK;

    if (active) {
      fillRoundRect(16, y, 168, itemH, 5, BLACK);
    } else {
      strokeRoundRect(16, y, 168, itemH, 5, 1, BLACK);
    }

    // Source badge / prefix
    String src = articleIndex[idx].source;
    if (src.length() > 6) src = src.substring(0, 6);
    char srcTag[12];
    snprintf(srcTag, sizeof(srcTag), "[%s]", src.c_str());
    drawStr(active ? 20 : 20, y + 5, srcTag, 1, col);

    // Title (fitted)
    int tagWidth = textW(srcTag, 1) + 4;
    int maxTitleWidth = 168 - tagWidth - 28;
    String cleanTitle = articleIndex[idx].title;
    drawStrFit(20 + tagWidth, y + 5, maxTitleWidth, cleanTitle.c_str(), 1, col);

    // Unread / Read indicator badge
    if (!articleIndex[idx].isRead) {
      drawStr(W - 36, y + 5, "new", 1, col);
    } else {
      drawStr(W - 36, y + 5, "ok", 1, col);
    }
  }

  drawFooterNav("next", "read", "menu");
  refresh();
}

void showReaderArticle(int cursor, int pageIndex) {
  int total = articleCount();
  if (cursor < 0 || cursor >= total) return;

  int num = articleIndex[cursor].num;
  String text = articleMarkdownContent(num);
  if (text.length() == 0) {
    text = "# " + String(articleIndex[cursor].title) + "\n\n_Empty article content._";
  }

  char header[64];
  String src = articleIndex[cursor].source;
  if (src.length() > 8) src = src.substring(0, 8);
  snprintf(header, sizeof(header), "#%03d [%s]", num, src.c_str());

  // Render markdown document
  showMdDocument(header, text, pageIndex, false);

  // Mark as read
  if (!articleIndex[cursor].isRead) {
    markArticleRead(num, true);
  }
}

void showReaderDeleteConfirm(int articleNum, const char* title) {
  clearWhite();
  fillRect(0, 0, W, 26, BLACK);
  drawStrC(W/2, 8, "DELETE ARTICLE", 1, WHITE);

  char label[16];
  snprintf(label, sizeof(label), "#%03d", articleNum);
  drawStrC(W/2, 44, label, 2, BLACK);

  drawStrFit(16, 75, W - 32, title, 1, BLACK);
  drawStrC(W/2, 105, "Remove from Reader?", 1, BLACK);

  drawFooterNav("cancel", "delete", "cancel");
  refresh();
}

void showNoteActions(int cursor, int actionCursor) {
  clearWhite();
  int idx = noteAtFilteredIndex(cursor);
  if (idx < 0) {
    drawStrC(100, 96, "not found", 1, BLACK);
    refresh();
    return;
  }

  int num = noteIndex[idx].num;
  bool isSynced = noteIndex[idx].uploaded;
  bool hasText  = noteIndex[idx].hasText;

  char n[24]; snprintf(n, sizeof(n), "#%03d  %s", num, noteIndex[idx].tag);
  drawStr(16, 12, n, 1, BLACK);
  drawBatteryMicroBadge(154, 12, readBatteryPercent(), BLACK);
  hline(16, 26, W - 32, BLACK);

  const char* actionNames[4] = { "Read (MD)", "Play Audio", "Info & Meta", "Delete Note" };
  const int y0 = 34, step = 27, itemH = 22;

  for (int i = 0; i < 4; i++) {
    bool active = (i == actionCursor);
    int y = y0 + i * step;
    uint8_t col = active ? WHITE : BLACK;

    if (active) {
      fillRoundRect(16, y, 168, itemH, 4, BLACK);
    }

    drawStr(active ? 22 : 18, y + 5, actionNames[i], 1, col);

    if (i == 0) {
      if (hasText || isSynced) {
        drawStr(W - 48, y + 5, "ready", 1, col);
      } else {
        drawStr(W - 48, y + 5, "sync>", 1, col);
      }
    } else if (i == 1) {
      float dur = noteAudioDurationSec(num);
      char durStr[12]; snprintf(durStr, sizeof(durStr), "%.1fs", dur);
      int dw = textW(durStr, 1);
      drawStr(W - 20 - dw, y + 5, durStr, 1, col);
    } else if (i == 2) {
      drawStr(W - 28, y + 5, ">", 1, col);
    } else if (i == 3) {
      drawStr(W - 38, y + 5, "del", 1, col);
    }
  }

  drawFooterNav("next", "select", "list");
  refresh();
}

void showNoteDetail(int cursor) {
  clearWhite();
  int idx = noteAtFilteredIndex(cursor);
  if (idx < 0) {
    drawStrC(100, 96, "not found", 1, BLACK);
    refresh();
    return;
  }

  int num = noteIndex[idx].num;
  bool isSynced = noteIndex[idx].uploaded;
  bool hasText = noteIndex[idx].hasText;

  char n[16]; snprintf(n, sizeof(n), "#%03d", num);
  drawStr(16, 12, n, 1, BLACK);
  String tagLabel = normalizeForDisplay(String(noteIndex[idx].tag));
  drawStrFit(56, 12, 90, tagLabel.c_str(), 1, BLACK);
  drawBatteryMicroBadge(154, 12, readBatteryPercent(), BLACK);
  hline(16, 26, W - 32, BLACK);

  float dur = noteAudioDurationSec(num);
  size_t sz = noteAudioFileSize(num);

  char durBuf[32];
  snprintf(durBuf, sizeof(durBuf), "dur: %.1fs", dur);
  drawStr(18, 34, durBuf, 1, BLACK);

  char szBuf[32];
  snprintf(szBuf, sizeof(szBuf), "size: %u KB", (unsigned int)(sz / 1024));
  drawStr(104, 34, szBuf, 1, BLACK);

  String createdLabel = noteCreatedDeviceLabel(num);
  char crBuf[48];
  snprintf(crBuf, sizeof(crBuf), "rec: %s", createdLabel.c_str());
  drawStrFit(18, 52, 164, crBuf, 1, BLACK);

  if (isSynced) {
    String syncedLabel = noteSyncedDeviceLabel(num);
    char syBuf[48];
    snprintf(syBuf, sizeof(syBuf), "sync: %s", syncedLabel.c_str());
    drawStrFit(18, 70, 164, syBuf, 1, BLACK);
  } else {
    drawStr(18, 70, "sync: not synced (pending)", 1, BLACK);
  }

  fillRoundRect(16, 88, 168, 68, 4, WHITE);
  strokeRoundRect(16, 88, 168, 68, 4, 1, BLACK);

  if (isSynced || hasText) {
    drawStr(22, 94, "Transcript (Markdown):", 1, BLACK);
    String preview = notePreviewText(num, 60);
    if (preview.length() == 0) preview = "Text available on SD card.";
    drawStrFit(22, 110, 156, preview.c_str(), 1, BLACK);
    drawStr(22, 134, "Status: Ready to read", 1, BLACK);
  } else {
    iconThinking(100, 110);
    drawStrC(100, 130, "Audio only (sync for text)", 1, BLACK);
  }

  drawFooterNav("back", "back", "list");
  refresh();
}

void showNoteMdReader(int cursor, int pageIndex) {
  int idx = noteAtFilteredIndex(cursor);
  if (idx < 0) return;

  int num = noteIndex[idx].num;
  String text = noteTextContent(num);
  if (text.length() == 0) {
    text = "# Note #" + String(num) + "\n\n_No transcription available yet._\n\nPlease sync with mobile or desktop client.";
  }

  char title[32];
  snprintf(title, sizeof(title), "#%03d [%s]", num, noteIndex[idx].tag);

  showMdDocument(title, text, pageIndex, true);
}

void showDeleteConfirm(int noteNum) {
  clearWhite();
  fillRect(0, 0, W, 26, BLACK);
  drawStrC(W/2, 8, "DELETE", 1, WHITE);
  char label[16]; snprintf(label, sizeof(label), "#%03d", noteNum);
  drawStrC(W/2, 48, label, 2, BLACK);
  drawStrC(W/2, 80, "Delete this note?", 1, BLACK);
  drawStrC(W/2, 104, "WAV + TXT + meta", 1, BLACK);

  drawFooterNav("cancel", "confirm", "cancel");
  refresh();
}

void showSyncConfirm(int noteNum) {
  clearWhite();
  fillRect(0, 0, W, 28, BLACK);
  drawStrC(W/2, 10, "SYNC", 1, WHITE);
  char label[16]; snprintf(label, sizeof(label), "#%03d", noteNum);
  drawStrC(W/2, 52, label, 2, BLACK);
  drawStrC(W/2, 88, "Sync notes now?", 1, BLACK);
  drawStrC(W/2, 108, "transcribe + upload", 1, BLACK);
  hline(0, 179, W, BLACK);
  fillRect(0, 180, W, 20, WHITE);
  drawStr(8, 186, "sync", 1, BLACK);
  int rw = textW("later", 1);
  drawStr(W - 8 - rw, 186, "later", 1, BLACK);
  refresh();
}

void showTranscribing(int done, int total) {
  clearWhite();
  drawKicker("syncing", 20);
  iconThinking(100, 76);
  int barW = 144, barH = 10, barX = 28, barY = 116;
  strokeRoundRect(barX, barY, barW, barH, 5, 1, BLACK);
  if (total > 0) {
    int fill = (done * (barW - 4)) / max(total, 1);
    if (fill > 0) fillRoundRect(barX+2, barY+2, fill, barH-4, 3, BLACK);
    char b[20]; snprintf(b, sizeof(b), "%d / %d", done, total);
    drawStrC(100, 142, b, 1, BLACK);
  } else {
    drawStrC(100, 142, "please wait", 1, BLACK);
  }
  refresh();
}

void showUploading(int done, int total) {
  clearWhite();
  drawKicker("uploading", 20);
  iconThinking(100, 76);
  int barW = 144, barH = 10, barX = 28, barY = 116;
  strokeRoundRect(barX, barY, barW, barH, 5, 1, BLACK);
  if (total > 0) {
    int fill = (done * (barW - 4)) / max(total, 1);
    if (fill > 0) fillRoundRect(barX+2, barY+2, fill, barH-4, 3, BLACK);
    char b[20]; snprintf(b, sizeof(b), "%d / %d", done, total);
    drawStrC(100, 142, b, 1, BLACK);
  } else {
    drawStrC(100, 142, "please wait", 1, BLACK);
  }
  refresh();
}

void showWifiConnecting(int attempt, int maxA) {
  clearWhite();
  drawKicker("wifi", 20);
  iconWifi(100, 84);
  int barW = 130, barH = 10, barX = 35, barY = 140;
  strokeRoundRect(barX, barY, barW, barH, 5, 1, BLACK);
  int fill = (attempt * (barW - 4)) / max(maxA, 1);
  if (fill > 0) fillRoundRect(barX+2, barY+2, fill, barH-4, 3, BLACK);
  char b[20]; snprintf(b, sizeof(b), "%d / %d", attempt, maxA);
  drawStrC(100, 164, b, 1, BLACK);
  refresh();
}

void showDone() {
  clearWhite();
  drawCheckSmall(100, 70, BLACK);
  drawStrC(100, 105, "all done", 1, BLACK);
  refresh();
}

void showError(const char* msg) {
  clearWhite();
  iconError(100, 70);
  if (msg && strlen(msg) > 0) drawStrC(100, 118, msg, 1, BLACK);
  else drawStrC(100, 118, "error", 1, BLACK);
  refresh();
}

void showUltraSleepScreen() {
  clearWhite();
  bool drawn = false;

  if (SD_MMC.exists(SCREENSAVERS_DIR)) {
    File dir = SD_MMC.open(SCREENSAVERS_DIR);
    if (dir && dir.isDirectory()) {
      std::vector<String> fileList;
      File entry = dir.openNextFile();
      while (entry) {
        String fname = entry.name();
        if (!entry.isDirectory() && (fname.endsWith(".bin") || fname.endsWith(".BIN"))) {
          String fullPath;
          if (fname.startsWith("/")) {
            fullPath = fname;
          } else {
            fullPath = String(SCREENSAVERS_DIR) + "/" + fname;
          }
          fileList.push_back(fullPath);
        }
        entry = dir.openNextFile();
      }
      dir.close();

      if (!fileList.empty()) {
        int r = random((int)fileList.size());
        File sf = SD_MMC.open(fileList[r].c_str());
        if (sf) {
          uint8_t* sBuf = (uint8_t*)malloc(5000);
          if (sBuf) {
            size_t rb = sf.read(sBuf, 5000);
            if (rb == 5000) {
              drawBitmap1BPP(0, 0, sBuf, 200, 200, BLACK);
              drawn = true;
            }
            free(sBuf);
          }
          sf.close();
        }
      }
    }
  }

  if (!drawn) {
    drawBitmap1BPP(0, 0, logo_bitmap, 200, 200, BLACK);
  }
  refresh();
}

void showPlaybackOverlay() {
  fillRoundRect(75, 145, 50, 34, 11, BLACK);
  fillTriangle(95, 154, 95, 170, 110, 162, WHITE);
  refresh();
}

void showTransferConnecting() {
  clearWhite();
  drawKicker("transfer", 18);
  iconWifi(100, 82);
  drawStrC(100, 138, "connecting", 1, BLACK);
  refresh();
}

void showTransferMode(const char* ip) {
  clearWhite();
  drawKicker("transfer", 16);
  fillRoundRect(26, 48, 148, 58, 16, BLACK);
  drawStrInBox(26, 48, 148, 24, "pala portal", 1, WHITE);
  drawStrInBox(26, 74, 148, 24, "active", 1, WHITE);
  drawStrC(100, 124, "open browser", 1, BLACK);
  drawStrC(100, 146, ip, 1, BLACK);
  drawStrC(100, 169, "double rec to exit", 1, BLACK);
  refresh();
}

void showSyncMode(const char* ssid, const char* ip, int pending) {
  clearWhite();
  drawKicker("sync mode", 14);
  fillRoundRect(16, 38, 168, 64, 12, BLACK);
  drawStrInBox(16, 44, 168, 20, ssid, 1, WHITE);
  drawStrInBox(16, 68, 168, 20, ip, 1, WHITE);

  if (pending > 0) {
    char b[32];
    snprintf(b, sizeof(b), "%d pending note%s", pending, pending == 1 ? "" : "s");
    drawStrC(100, 118, b, 1, BLACK);
  } else {
    drawStrC(100, 118, "all notes synced", 1, BLACK);
  }

  drawStrC(100, 142, "connect app or browser", 1, BLACK);
  drawStrC(100, 170, "double rec to exit", 1, BLACK);
  refresh();
}

void showSettings(int cursor) {
  clearWhite();
  drawStr(16, 12, "settings", 1, BLACK);
  drawBatteryMicroBadge(154, 12, readBatteryPercent(), BLACK);
  hline(16, 26, W-32, BLACK);

  const int y0 = 32, step = 25, itemH = 22;
  for (int row = 0; row < SETTINGS_COUNT; row++) {
    bool active = (row == cursor);
    int y = y0 + row * step;
    uint8_t col = active ? WHITE : BLACK;

    if (active) {
      fillRoundRect(16, y, 168, itemH, 5, BLACK);
    }

    if (row == 0) {
      drawStr(active ? 22 : 18, y + 5, "sounds", 1, col);
      drawStr(W - 48, y + 5, palaSoundIsEnabled() ? "on" : "off", 1, col);
    } else if (row == 1) {
      drawStr(active ? 22 : 18, y + 5, "clean synced", 1, col);
      char b[12];
      snprintf(b, sizeof(b), "%d ready", syncedNotesCount());
      int bw = textW(b, 1);
      drawStr(W - 22 - bw, y + 5, b, 1, col);
    } else if (row == 2) {
      drawStr(active ? 22 : 18, y + 5, "wi-fi", 1, col);
      char b[10];
      if (palaHasSecondNet()) snprintf(b, sizeof(b), "net %d", palaActiveNet() + 1);
      else                    snprintf(b, sizeof(b), "net 1");
      int bw = textW(b, 1);
      drawStr(W - 22 - bw, y + 5, b, 1, col);
    } else if (row == 3) {
      drawStr(active ? 22 : 18, y + 5, "transfer", 1, col);
      drawStr(W - 68, y + 5, "sd-mmc", 1, col);
    } else {
      drawStr(active ? 22 : 18, y + 5, "device", 1, col);
      drawStr(W - 58, y + 5, "info >", 1, col);
    }
  }

  drawFooterNav("next", "toggle", "menu");
  refresh();
}

void showDeviceInfo() {
  clearWhite();
  drawStr(16, 12, "device", 1, BLACK);
  int batt = readBatteryPercent();
  float vbat = readBatteryVoltage();
  drawBatteryMicroBadge(154, 12, batt, BLACK);
  hline(16, 26, W-32, BLACK);

  char fwBuf[40];
  snprintf(fwBuf, sizeof(fwBuf), "firmware: %s", FIRMWARE_VERSION);
  drawStrFit(18, 34, 164, fwBuf, 1, BLACK);

  char batBuf[40];
  snprintf(batBuf, sizeof(batBuf), "battery: %d%% (%.2fV)", batt, vbat);
  drawStrFit(18, 52, 164, batBuf, 1, BLACK);

  String runtime = estimateBatteryRuntime(batt, vbat);
  char runBuf[40];
  snprintf(runBuf, sizeof(runBuf), "runtime: %s", runtime.c_str());
  drawStrFit(18, 70, 164, runBuf, 1, BLACK);

  drawStrFit(18, 88, 164, "board: ESP32-S3 ePaper", 1, BLACK);

  char b[32]; snprintf(b, sizeof(b), "notes: %d stored", (int)noteIndex.size());
  drawStr(18, 106, b, 1, BLACK);

  drawStr(18, 124, palaSoundIsEnabled() ? "sounds: on" : "sounds: off", 1, BLACK);
  drawStr(18, 142, rtcUtcIso().length() ? "rtc: synchronized" : "rtc: local time", 1, BLACK);

  drawFooterNav("back", "back", "settings");
  refresh();
}
