#pragma once
#include <Arduino.h>

#define ES1_SERVICE_UUID        "0000e501-0000-1000-8000-00805f9b34fb"
#define ES1_CHAR_CMD_UUID       "0000e502-0000-1000-8000-00805f9b34fb"
#define ES1_CHAR_DATA_UUID      "0000e503-0000-1000-8000-00805f9b34fb"

void startBleSync();
void stopBleSync();
bool isBleSyncActive();
bool isBleSyncDone();
bool isBleDeviceConnected();
void handleBleSyncLoop();
