#pragma once
#include <Arduino.h>

enum LedState {
  LED_OFF,
  LED_CHARGING,      // Rosso fisso durante la ricarica
  LED_CHARGED_FULL,  // Verde fisso a carica completata
  LED_RECORDING      // Rosso lampeggiante durante la registrazione
};

void ledInit();
void ledSetState(LedState state);
void ledSetRecording(bool isRecording);
void ledCheckChargingStatus(int battPct, float vbat);
void ledUpdate();
