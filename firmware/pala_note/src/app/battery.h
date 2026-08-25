#pragma once

void  batteryInit();
float readBatteryVoltage();
int   batteryPercentFromVoltage(float v);
int   readBatteryPercent();
void   drawThickArcDot(int cx, int cy, int r, int deg, int thickness, uint8_t color);
void   drawBatteryRing(int percent);
void   drawBatteryMicroBadge(int x, int y, int pct, uint8_t color = 0);
String estimateBatteryRuntime(int pct, float vbat);
