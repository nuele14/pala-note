#include "Arduino.h"
#include "../../config.h"
#include "../../globals.h"
#include "led.h"
#include "battery.h"

static LedState currentLedState = LED_OFF;
static uint32_t lastBlinkMs = 0;
static bool blinkState = false;
static bool ledRecordingActive = false;

void ledInit() {
  #if defined(LED_RED_PIN) && (LED_RED_PIN >= 0)
  pinMode(LED_RED_PIN, OUTPUT);
  digitalWrite(LED_RED_PIN, !LED_ACTIVE_LEVEL);
  #endif

  #if defined(LED_GREEN_PIN) && (LED_GREEN_PIN >= 0)
  pinMode(LED_GREEN_PIN, OUTPUT);
  digitalWrite(LED_GREEN_PIN, !LED_ACTIVE_LEVEL);
  #endif
}

static void setPhysicalLeds(bool redOn, bool greenOn) {
  #if defined(LED_RED_PIN) && (LED_RED_PIN >= 0)
  digitalWrite(LED_RED_PIN, redOn ? LED_ACTIVE_LEVEL : !LED_ACTIVE_LEVEL);
  #endif

  #if defined(LED_GREEN_PIN) && (LED_GREEN_PIN >= 0)
  digitalWrite(LED_GREEN_PIN, greenOn ? LED_ACTIVE_LEVEL : !LED_ACTIVE_LEVEL);
  #endif
}

void ledSetState(LedState state) {
  currentLedState = state;
}

void ledSetRecording(bool isRecording) {
  ledRecordingActive = isRecording;
  if (isRecording) {
    currentLedState = LED_RECORDING;
    lastBlinkMs = millis();
    blinkState = true;
    setPhysicalLeds(true, false);
  } else {
    currentLedState = LED_OFF;
    setPhysicalLeds(false, false);
  }
}

void ledCheckChargingStatus(int battPct, float vbat) {
  if (ledRecordingActive) return; // Massima priorità alla registrazione vocale

  // Rileva stato di carica (es. tensione sopra 4.15V o 98%+)
  if (vbat >= 4.15f) {
    if (battPct >= 98 || vbat >= 4.22f) {
      currentLedState = LED_CHARGED_FULL; // Verde fisso
    } else {
      currentLedState = LED_CHARGING;     // Rosso fisso
    }
  } else {
    currentLedState = LED_OFF;
  }
}

void ledUpdate() {
  uint32_t now = millis();

  switch (currentLedState) {
    case LED_RECORDING: {
      if (now - lastBlinkMs >= LED_REC_BLINK_INTERVAL_MS) {
        lastBlinkMs = now;
        blinkState = !blinkState;
        setPhysicalLeds(blinkState, false); // Rosso lampeggiante
      }
      break;
    }
    case LED_CHARGING:
      setPhysicalLeds(true, false); // Rosso fisso
      break;

    case LED_CHARGED_FULL:
      setPhysicalLeds(false, true); // Verde fisso
      break;

    case LED_OFF:
    default:
      setPhysicalLeds(false, false);
      break;
  }
}
