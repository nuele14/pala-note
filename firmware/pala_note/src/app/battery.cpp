#include "Arduino.h"
#include "../../globals.h"
#include "../../types.h"
#include "battery.h"
#include "draw.h"
#include "../../config.h"
#include <math.h>

static bool palaAdcReady = false;

void batteryInit() {
  if (palaAdcReady) return;
  pinMode(BAT_ADC_PIN, INPUT);
  analogSetPinAttenuation(BAT_ADC_PIN, ADC_11db);
  analogReadMilliVolts(BAT_ADC_PIN);
  palaAdcReady = true;
}

float readBatteryVoltage() {
  if (!palaAdcReady) batteryInit();
  const int samples = 16;
  uint32_t sum = 0;
  for (int i = 0; i < samples; i++) { sum += analogReadMilliVolts(BAT_ADC_PIN); delay(2); }
  float mv = (float)sum / (float)samples;
  return (mv / 1000.0f) * 2.0f;
}

int batteryPercentFromVoltage(float v) {
  if (v >= 4.35f) return 100;
  if (v <= 3.20f) return 0;
  if (v >= 4.20f) return 100;
  const float volts[] = {3.20f, 3.40f, 3.70f, 3.90f, 4.20f};
  const int   pct[]   = {0,     25,    50,    75,    100};
  for (int i = 1; i < 5; i++) {
    if (v <= volts[i]) {
      float t = (v - volts[i-1]) / (volts[i] - volts[i-1]);
      int p = pct[i-1] + (int)((pct[i] - pct[i-1]) * t + 0.5f);
      p = ((p + 2) / 5) * 5;
      return constrain(p, 0, 100);
    }
  }
  return 100;
}

int readBatteryPercent() {
  float v = readBatteryVoltage();
  if (v <= 0.1f) return -1;
  return batteryPercentFromVoltage(v);
}

void drawThickArcDot(int cx, int cy, int r, int deg, int thickness, uint8_t color) {
  float a = ((float)deg - 90.0f) * PI / 180.0f;
  int x = cx + (int)roundf(cosf(a) * r);
  int y = cy + (int)roundf(sinf(a) * r);
  if (thickness <= 1) px(x, y, color);
  else fillCircle(x, y, thickness / 2, color);
}

void drawBatteryRing(int percent) {
  const int cx = 100, cy = 100, r = 82;
  strokeCircle(cx, cy, r, 1, BLACK);
  if (percent < 0) return;
  percent = constrain(percent, 0, 100);
  int endDeg = (360 * percent) / 100;
  for (int deg = 0; deg <= endDeg; deg += 2)
    drawThickArcDot(cx, cy, r, deg, 3, BLACK);
}

// Micro-font 3×5 pixel per cifre '0'-'9' e '%'
static const uint16_t MICRO_DIGITS[11] = {
  0b111101101101111, // 0
  0b010110010010111, // 1
  0b111001111100111, // 2
  0b111001111001111, // 3
  0b101101111001001, // 4
  0b111100111001111, // 5
  0b111100111101111, // 6
  0b111001001010010, // 7
  0b111101111101111, // 8
  0b111101111001111, // 9
  0b101001010100101  // % (idx 10)
};

static void drawMicroChar(int x, int y, char c, uint8_t color) {
  int idx = -1;
  if (c >= '0' && c <= '9') idx = c - '0';
  else if (c == '%')        idx = 10;
  if (idx < 0) return;

  uint16_t mask = MICRO_DIGITS[idx];
  for (int row = 0; row < 5; row++) {
    for (int col = 0; col < 3; col++) {
      int bit = 14 - (row * 3 + col);
      if ((mask >> bit) & 1) {
        px(x + col, y + row, color);
      }
    }
  }
}

static int drawMicroStr(int x, int y, const char* s, uint8_t color) {
  int curX = x;
  while (*s) {
    drawMicroChar(curX, y, *s, color);
    curX += 4; // 3 pixel width + 1 pixel gap
    s++;
  }
  return curX - x;
}

void drawBatteryMicroBadge(int x, int y, int pct, uint8_t color) {
  if (pct < 0) return;
  pct = constrain(pct, 0, 100);

  // 1. Numero percentuale a sinistra con micro-font 3×5 (altezza 5px)
  char pbuf[8];
  snprintf(pbuf, sizeof(pbuf), "%d%%", pct);
  int charCount = strlen(pbuf);
  int strW = charCount * 4 - 1;
  drawMicroStr(x - 4 - strW, y + 2, pbuf, color);

  // 2. Icona batteria compatta: rettangolo 16x9 + polo 2x5
  strokeRect(x, y, 16, 9, 1, color);
  fillRect(x + 16, y + 2, 2, 5, color);

  // 3. Le 3 tacche interne graduate
  if (pct >= 70) {
    // 3 tacche piene
    fillRect(x + 2,  y + 2, 3, 5, color);
    fillRect(x + 6,  y + 2, 3, 5, color);
    fillRect(x + 10, y + 2, 3, 5, color);
  } else if (pct >= 35) {
    // 2 tacche piene
    fillRect(x + 2,  y + 2, 3, 5, color);
    fillRect(x + 6,  y + 2, 3, 5, color);
  } else if (pct >= 15) {
    // 1 sola tacca
    fillRect(x + 2,  y + 2, 3, 5, color);
  }
  // Sotto il 15%: tutte le 3 tacche sono vuote [□□□] per alert visivo
}

String estimateBatteryRuntime(int pct, float vbat) {
  if (pct <= 0) return "0 ore";
  if (pct >= 20) {
    float days = (pct * 7.0f) / 100.0f;
    char b[16];
    snprintf(b, sizeof(b), "~%.1f gg", days);
    return String(b);
  } else {
    int hours = (pct * 24 * 7) / 100;
    if (hours < 1) hours = 1;
    char b[16];
    snprintf(b, sizeof(b), "~%d ore", hours);
    return String(b);
  }
}
