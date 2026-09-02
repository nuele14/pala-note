#include "Arduino.h"
#include "SD_MMC.h"
#include <WiFi.h>
#include <WebServer.h>
#include <vector>
#include "driver/i2c_master.h"
#include "esp_heap_caps.h"

extern "C" {
#include "config.h"
#include "src/i2c_bsp/i2c_bsp.h"
#include "src/audio/audio_bsp.h"
}

#include "src/power/board_power_bsp.h"
#include "src/display/epaper_driver_bsp.h"
#include "src/app/provisioning.h"
#include "logo_bitmap.h"
#include "secrets.h"
#include "sounds.h"

#include <Adafruit_GFX.h>
#include <pgmspace.h>
#include <Fonts/FreeSans9pt7b.h>
#include <Fonts/FreeSansBold9pt7b.h>
#include <Fonts/FreeSans12pt7b.h>
#include <Fonts/FreeSansBold12pt7b.h>
#include <Fonts/FreeSansBold18pt7b.h>
#include <math.h>
#include <time.h>
#include <sys/time.h>

#include "types.h"
#include "globals.h"
#include "src/app/draw.h"
#include "src/app/battery.h"
#include "src/app/rtc.h"
#include "src/app/notes.h"
#include "src/app/ui.h"
#include "src/app/buttons.h"
#include "src/app/network.h"
#include "src/app/sleep.h"
#include "src/app/record.h"
#include "src/app/led.h"
#include "src/app/md_reader.h"
#include "src/app/reader.h"

// All pin, timing, path and threshold constants live in config.h.

// ─── Content arrays ───────────────────────────────────────────────────────
const char* DEFAULT_TAGS[]    = { "Note", "Work", "Idea", "Buy", "Private", "Todo" };
const char* MENU_ITEMS[]     = { "Notes", "Reader", "Shikamaru", "Sync", "Settings" };
const char* SETTINGS_ITEMS[] = { "Sounds", "Clean Synced", "Wi-Fi", "Transfer", "Device" };

// ─── Global variable definitions ─────────────────────────────────────────
board_power_bsp_t      board(EPD_PWR_PIN, Audio_PWR_PIN, VBAT_PWR_PIN);
epaper_driver_display* display = nullptr;

std::vector<NoteEntry> noteIndex;
std::vector<ArticleEntry> articleIndex;

AppState state          = STATE_IDLE;
int      listCursor     = 0;
int      readerCursor   = 0;
int      readerArticlePage = 0;
int      tagCursor      = 2;
int      menuCursor     = 0;
int      settingsCursor = 0;
bool     soundsOn       = true;
int      activeFilter   = -1;
int      lastRecNum     = -1;

bool     shikamaruIsFocus       = true;
int      shikamaruSession       = 1;
bool     shikamaruPaused        = true;
uint32_t shikamaruRemainingSec  = 25 * 60;
uint32_t shikamaruLastTickMs    = 0;

uint32_t lastActivityMs      = 0;
bool     wokeFromUltraSleep  = false;
bool     wakeToMenuRequested = false;
bool     wakeToRecRequested  = false;

uint32_t tickerLastMs = 0;
int      tickerOffset = 0;
int      tickerCursor = -1;

WebServer transferServer(80);
bool      transferServerActive = false;
String    transferUrl          = "";

bool timeReady    = false;
bool audioPlaying = false;
bool stopPlayback = false;

int detailScrollPage = 0;
int detailTotalLines = 0;
int mdReaderPage     = 0;
int noteActionCursor = 0;

uint32_t lastBatCheckMs    = 0;
bool     batLowWarned      = false;
bool     batWarnActive     = false;
uint32_t batWarnShowUntilMs = 0;

char tags[20][32];
int  tagCount = 0;

// ─── Power latch ──────────────────────────────────────────────────────────
void keepBatteryPowerOn() {
  pinMode(PWR_HOLD_PIN, OUTPUT);
  digitalWrite(PWR_HOLD_PIN, HIGH);
}

// ─── Flow functions ───────────────────────────────────────────────────────
void startRecordFlow() {
  state = STATE_RECORDING;
  ledSetRecording(true);
  showRecording();

  palaSoundSetEnabled(false);
  bool recOk = record();
  palaSoundSetEnabled(true);
  ledSetRecording(false);

  if (!recOk) {
    showError("REC FAIL");
    delay(1600);
    state = STATE_IDLE;
    showIdle();
    return;
  }

  saveTag(lastRecNum, "Note");

  soundSaved();

  state = STATE_SAVED;
  showSaved(lastRecNum);
  delay(900);

  state = STATE_IDLE;
  showIdle();
}

void startSyncFlow() {
  state = STATE_TRANSFER;
  startSoftApSync();
  int pending = pendingSyncCount();
  showSyncMode(getSoftApSsid().c_str(), "192.168.4.1", pending);
}

void startTransferMode() {
  startSyncFlow();
}

void playShikamaruEndSound() {
  if (SD_MMC.exists("/sounds/shikamaru.wav")) {
    playWavFile("/sounds/shikamaru.wav");
  } else if (SD_MMC.exists("/sounds/pomodoro.wav")) {
    playWavFile("/sounds/pomodoro.wav");
  } else {
    soundShikamaruRelax();
  }
}

// ─── Setup ────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ES1 (Extransformer Shield Uno) " FIRMWARE_VERSION " ===");

  // Load runtime config provisioned by the browser flasher (Wi-Fi, OpenAI key,
  // Pala device key + host). Falls back to secrets.h when not provisioned.
  palaConfigInit();

  pinMode(BTN_REC, INPUT_PULLUP);
  pinMode(BTN_PWR, INPUT_PULLUP);

  board.VBAT_POWER_ON();

  wokeFromUltraSleep  = (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_EXT1);
  delay(50);

  wakeToMenuRequested = (wokeFromUltraSleep && digitalRead(BTN_PWR) == LOW);
  wakeToRecRequested  = (wokeFromUltraSleep && digitalRead(BTN_REC) == LOW);

  resetActivity();
  keepBatteryPowerOn();
  delay(20);

  board.POWEER_EPD_ON();
  board.POWEER_Audio_ON();
  delay(200);

  custom_lcd_spi_t dispCfg = {};
  dispCfg.cs       = EPD_CS_PIN;
  dispCfg.dc       = EPD_DC_PIN;
  dispCfg.rst      = EPD_RST_PIN;
  dispCfg.busy     = EPD_BUSY_PIN;
  dispCfg.mosi     = EPD_MOSI_PIN;
  dispCfg.scl      = EPD_SCK_PIN;
  dispCfg.spi_host = EPD_SPI_NUM;
  dispCfg.buffer_len = (200*200)/8;

  display = new epaper_driver_display(200, 200, dispCfg);
  display->EPD_Init();
  display->EPD_Clear();
  display->EPD_DisplayPartBaseImage();
  display->EPD_Init_Partial();

  i2c_master_Init();
  delay(50);

  audio_bsp_init();
  audio_play_init();
  ledInit();

  SD_MMC.setPins(SD_CLK, SD_CMD, SD_D0);
  if (!SD_MMC.begin("/sdcard", true)) {
    showError("SD ERR");
    while (true) delay(1000);
  }
  if (!SD_MMC.exists(NOTES_DIR)) SD_MMC.mkdir(NOTES_DIR);
  if (!SD_MMC.exists(ARTICLES_DIR)) SD_MMC.mkdir(ARTICLES_DIR);
  if (!SD_MMC.exists(SCREENSAVERS_DIR)) SD_MMC.mkdir(SCREENSAVERS_DIR);
  if (!SD_MMC.exists(SOUNDS_DIR)) SD_MMC.mkdir(SOUNDS_DIR);

  loadTags();
  loadIndex();
  loadArticleIndex();
  #if ENABLE_BOOT_SPLASH
  if (!wakeToRecRequested) {
    showBootSplash();
    delay(BOOT_SPLASH_MS);
  }
  #endif

  if (wakeToMenuRequested) {
    menuCursor = 0;
    state = STATE_MENU;
    showMenu(menuCursor);
  } else if (wakeToRecRequested) {
    startRecordFlow();
  } else {
    showIdle();
  }
}

// ─── Main loop ────────────────────────────────────────────────────────────
void loop() {

  if (state != STATE_RECORDING && state != STATE_TRANSFER && !(state == STATE_SHIKAMARU && !shikamaruPaused)) {
    if (millis() - lastActivityMs > ULTRA_SLEEP_MS) {
      enterUltraSleep();
      return;
    }
  }

  if (state == STATE_NOTE_LIST && activeTickerNeedsScroll(listCursor)) {
    if (millis() - tickerLastMs > TICKER_INTERVAL_MS) {
      tickerLastMs = millis();
      tickerOffset++;
      showNoteList(listCursor);
      return;
    }
  }

  if (transferServerActive) transferServer.handleClient();

  // Battery warning: dismiss after 2.5 s without blocking
  if (batWarnActive && millis() >= batWarnShowUntilMs) {
    batWarnActive = false;
    switch (state) {
      case STATE_IDLE:           showIdle();                     break;
      case STATE_MENU:           showMenu(menuCursor);           break;
      case STATE_NOTE_LIST:      showNoteList(listCursor);       break;
      case STATE_READER_LIST:    showReaderList(readerCursor);   break;
      case STATE_READER_ARTICLE: showReaderArticle(readerCursor, readerArticlePage); break;
      case STATE_NOTE_ACTIONS:   showNoteActions(listCursor, noteActionCursor); break;
      case STATE_NOTE_DETAIL:    showNoteDetail(listCursor);     break;
      case STATE_MD_READER:      showNoteMdReader(listCursor, mdReaderPage); break;
      case STATE_TAG_SELECT:     showTagSelect(tagCursor);       break;
      case STATE_SYNC_CONFIRM:   showSyncConfirm(lastRecNum);    break;
      case STATE_TAG_BROWSER:    showTagBrowser(tagCursor);      break;
      case STATE_SETTINGS:       showSettings(settingsCursor);   break;
      case STATE_DEVICE_INFO:    showDeviceInfo();               break;
      case STATE_DELETE_CONFIRM: {
        int idx = noteAtFilteredIndex(listCursor);
        if (idx >= 0) showDeleteConfirm(noteIndex[idx].num);
        break;
      }
      default: break;
    }
  }

  // Periodic battery check, Charging LED state & Low Battery Alert (< 15%)
  if (state != STATE_RECORDING && !audioPlaying && !batWarnActive) {
    if (millis() - lastBatCheckMs > BAT_CHECK_INTERVAL_MS) {
      lastBatCheckMs = millis();
      int pct = readBatteryPercent();
      float vbat = readBatteryVoltage();
      ledCheckChargingStatus(pct, vbat);

      if (pct >= 0 && pct <= BAT_LOW_THRESHOLD && !batLowWarned) {
        batLowWarned        = true;
        batWarnActive       = true;
        batWarnShowUntilMs  = millis() + 2500;
        soundBatteryLow();
        showBatteryLow(pct);
      } else if (pct > BAT_RECOVER_THRESHOLD) {
        batLowWarned = false;
      }
    }
  }

  // Update LED states (blinking / solid)
  ledUpdate();

  // IDLE ─────────────────────────────────────────────────────────────────
  if (state == STATE_IDLE) {
    if (handleIdleRec()) return;

    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    if (pwr == EV_SINGLE || pwr == EV_LONG) {
      soundSelect();
      menuCursor = 0;
      state = STATE_MENU;
      showMenu(menuCursor);
    }
  }

  // TAG SELECT after recording ──────────────────────────────────────────
  else if (state == STATE_TAG_SELECT) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (rec == EV_SINGLE || rec == EV_LONG) {
      soundSelect();
      saveTag(lastRecNum, tags[constrain(tagCursor, 0, max(tagCount - 1, 0))]);
      resetActivity();
      state = STATE_IDLE;
      showIdle();
    } else if (pwr == EV_SINGLE) {
      soundNext();
      if (tagCount > 0) tagCursor = (tagCursor + 1) % tagCount;
      showTagSelect(tagCursor);
    }
  }

  // SYNC CONFIRM (legacy fallback) ───────────────────────────────────────
  else if (state == STATE_SYNC_CONFIRM) {
    resetActivity();
    state = STATE_IDLE;
    showIdle();
  }

  // MENU ────────────────────────────────────────────────────────────────
  else if (state == STATE_MENU) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (pwr == EV_SINGLE) {
      soundNext();
      menuCursor = (menuCursor + 1) % MENU_COUNT;
      showMenu(menuCursor);
    } else if (rec == EV_SINGLE) {
      soundSelect();
      if (menuCursor == 0) {
        // Notes
        activeFilter = -1; listCursor = 0;
        state = STATE_NOTE_LIST;
        showNoteList(listCursor);
      } else if (menuCursor == 1) {
        // Reader
        readerCursor = 0;
        state = STATE_READER_LIST;
        showReaderList(readerCursor);
      } else if (menuCursor == 2) {
        // Shikamaru
        shikamaruIsFocus = true;
        shikamaruSession = 1;
        shikamaruPaused = true;
        shikamaruRemainingSec = 25 * 60;
        state = STATE_SHIKAMARU;
        showShikamaru(shikamaruRemainingSec, true, 1, true);
      } else if (menuCursor == 3) {
        // Sync
        startSyncFlow();
      } else {
        // Settings
        settingsCursor = 0;
        state = STATE_SETTINGS;
        showSettings(settingsCursor);
      }
    } else if (rec == EV_LONG || rec == EV_DOUBLE) {
      soundBack();
      state = STATE_IDLE;
      showIdle();
    }
  }

  // SHIKAMARU (Pomodoro Focus Timer) ───────────────────────────────────
  else if (state == STATE_SHIKAMARU) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (pwr == EV_SINGLE || pwr == EV_LONG) {
      soundBack();
      state = STATE_MENU;
      showMenu(menuCursor);
    } else if (rec == EV_SINGLE) {
      shikamaruPaused = !shikamaruPaused;
      soundSelect();
      shikamaruLastTickMs = millis();
      showShikamaru(shikamaruRemainingSec, shikamaruIsFocus, shikamaruSession, shikamaruPaused);
    } else if (rec == EV_LONG) {
      soundSelect();
      if (shikamaruIsFocus) {
        shikamaruIsFocus = false;
        shikamaruRemainingSec = 5 * 60;
      } else {
        shikamaruIsFocus = true;
        shikamaruSession = (shikamaruSession % 4) + 1;
        shikamaruRemainingSec = 25 * 60;
      }
      shikamaruPaused = true;
      showShikamaru(shikamaruRemainingSec, shikamaruIsFocus, shikamaruSession, shikamaruPaused);
    }

    // Timer countdown
    if (!shikamaruPaused) {
      resetActivity();
      if (millis() - shikamaruLastTickMs >= 1000) {
        shikamaruLastTickMs = millis();
        if (shikamaruRemainingSec > 0) {
          shikamaruRemainingSec--;
          bool needRefresh = false;
          // Quando mancano >= 60s: aggiorna ogni minuto esatto (es. 24:00, 23:00, ...)
          if (shikamaruRemainingSec >= 60 && (shikamaruRemainingSec % 60 == 0)) {
            needRefresh = true;
          }
          // Durante l'ultimo minuto (< 60s): aggiorna a :50, :40, :30, :20, :10
          else if (shikamaruRemainingSec < 60 && (shikamaruRemainingSec % 10 == 0)) {
            needRefresh = true;
          }

          if (needRefresh) {
            showShikamaru(shikamaruRemainingSec, shikamaruIsFocus, shikamaruSession, shikamaruPaused);
          }
        } else {
          // Timer completato (fine)
          showShikamaru(0, shikamaruIsFocus, shikamaruSession, true);
          playShikamaruEndSound();
          if (shikamaruIsFocus) {
            shikamaruIsFocus = false;
            shikamaruRemainingSec = 5 * 60;
          } else {
            shikamaruIsFocus = true;
            shikamaruSession = (shikamaruSession % 4) + 1;
            shikamaruRemainingSec = 25 * 60;
          }
          shikamaruPaused = true;
          showShikamaru(shikamaruRemainingSec, shikamaruIsFocus, shikamaruSession, shikamaruPaused);
        }
      }
    }
  }

  // SETTINGS ────────────────────────────────────────────────────────────
  else if (state == STATE_SETTINGS) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (pwr == EV_SINGLE) {
      soundNext();
      settingsCursor = (settingsCursor + 1) % SETTINGS_COUNT;
      showSettings(settingsCursor);
    } else if (rec == EV_SINGLE) {
      soundSelect();
      if (settingsCursor == 0) {
        palaSoundSetEnabled(!palaSoundIsEnabled());
        showSettings(settingsCursor);
      } else if (settingsCursor == 1) {
        int cleaned = cleanSyncedNotes();
        if (cleaned > 0) soundDelete();
        else             soundBack();
        showSettings(settingsCursor);
      } else if (settingsCursor == 2) {
        // Wi-Fi: switch the default network (only meaningful with two configured).
        if (palaHasSecondNet()) palaSetActiveNet(palaActiveNet() ^ 1);
        showSettings(settingsCursor);
      } else if (settingsCursor == 3) {
        startTransferMode();
      } else {
        state = STATE_DEVICE_INFO;
        showDeviceInfo();
      }
    } else if (rec == EV_DOUBLE || rec == EV_LONG) {
      soundBack();
      state = STATE_MENU;
      showMenu(menuCursor);
    }
  }

  // DEVICE INFO ─────────────────────────────────────────────────────────
  else if (state == STATE_DEVICE_INFO) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (rec == EV_DOUBLE || rec == EV_LONG || rec == EV_SINGLE || pwr == EV_SINGLE) {
      soundBack();
      state = STATE_SETTINGS;
      showSettings(settingsCursor);
    }
  }

  // TRANSFER / SYNC MODE ─────────────────────────────────────────────────
  else if (state == STATE_TRANSFER) {
    if (transferServerActive) transferServer.handleClient();

    if (isSyncDoneRequested()) {
      clearSyncDoneRequested();
      showDone();
      soundSuccess();
      delay(1500);
      stopTransferMode();
      resetActivity();
      state = STATE_IDLE;
      showIdle();
    } else {
      ButtonEvent rec = readButtonEvent(BTN_REC);
      if (rec == EV_DOUBLE || rec == EV_LONG) {
        soundBack();
        stopTransferMode();
        resetActivity();
        state = STATE_IDLE;
        showIdle();
      }
    }
  }

  // TAG BROWSER ─────────────────────────────────────────────────────────
  else if (state == STATE_TAG_BROWSER) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (pwr == EV_SINGLE) {
      soundNext();
      if (tagCount > 0) tagCursor = (tagCursor + 1) % tagCount;
      showTagBrowser(tagCursor);
    } else if (rec == EV_SINGLE) {
      soundSelect();
      activeFilter = tagCursor; listCursor = 0;
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
    } else if (rec == EV_LONG || rec == EV_DOUBLE) {
      soundBack();
      state = STATE_MENU;
      showMenu(menuCursor);
    }
  }

  // NOTE LIST ───────────────────────────────────────────────────────────
  else if (state == STATE_NOTE_LIST) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    int count = filteredCount();

    if (count <= 0) {
      if (pwr != EV_NONE || rec != EV_NONE) {
        soundBack();
        state = STATE_MENU;
        showMenu(menuCursor);
      }
      return;
    }

    if (pwr == EV_SINGLE) {
      // SCORRE LISTA NOTE
      soundNext();
      listCursor = (listCursor + 1) % count;
      showNoteList(listCursor);
    } else if (rec == EV_SINGLE) {
      // APRE MENU AZIONI PER LA NOTA SELEZIONATA
      soundSelect();
      noteActionCursor = 0;
      state = STATE_NOTE_ACTIONS;
      showNoteActions(listCursor, noteActionCursor);
    } else if (rec == EV_LONG || pwr == EV_LONG || pwr == EV_DOUBLE) {
      // TORNA AL MENU PRINCIPALE
      soundBack();
      state = STATE_MENU;
      showMenu(menuCursor);
    }
  }

  // READER ARTICLE LIST ────────────────────────────────────────────────
  else if (state == STATE_READER_LIST) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    int count = articleCount();

    if (count <= 0) {
      if (pwr != EV_NONE || rec != EV_NONE) {
        soundBack();
        state = STATE_MENU;
        showMenu(menuCursor);
      }
      return;
    }

    if (pwr == EV_SINGLE) {
      soundNext();
      readerCursor = (readerCursor + 1) % count;
      showReaderList(readerCursor);
    } else if (rec == EV_SINGLE) {
      soundSelect();
      readerArticlePage = 0;
      state = STATE_READER_ARTICLE;
      showReaderArticle(readerCursor, readerArticlePage);
    } else if (rec == EV_LONG || pwr == EV_LONG || pwr == EV_DOUBLE) {
      soundBack();
      state = STATE_MENU;
      showMenu(menuCursor);
    }
  }

  // READER ARTICLE VIEW ────────────────────────────────────────────────
  else if (state == STATE_READER_ARTICLE) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    int count = articleCount();

    if (readerCursor >= 0 && readerCursor < count) {
      String text = articleMarkdownContent(articleIndex[readerCursor].num);
      int totalPages = mdCalculateTotalPages(text);

      if (pwr == EV_SINGLE) {
        // Pagina successiva
        soundNext();
        readerArticlePage = (readerArticlePage + 1) % totalPages;
        showReaderArticle(readerCursor, readerArticlePage);
      } else if (rec == EV_SINGLE) {
        // Pagina precedente
        soundNext();
        if (readerArticlePage > 0) readerArticlePage--;
        else readerArticlePage = max(0, totalPages - 1);
        showReaderArticle(readerCursor, readerArticlePage);
      } else if (rec == EV_LONG || pwr == EV_LONG || pwr == EV_DOUBLE) {
        // Torna alla lista articoli
        soundBack();
        state = STATE_READER_LIST;
        showReaderList(readerCursor);
      }
    } else {
      state = STATE_READER_LIST;
      showReaderList(readerCursor);
    }
  }

  // READER DELETE CONFIRM ───────────────────────────────────────────────
  else if (state == STATE_READER_DELETE_CONFIRM) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (rec == EV_SINGLE) {
      if (readerCursor >= 0 && readerCursor < articleCount()) {
        deleteArticle(articleIndex[readerCursor].num);
        soundDelete();
      }
      readerCursor = constrain(readerCursor, 0, max(articleCount() - 1, 0));
      state = STATE_READER_LIST;
      showReaderList(readerCursor);
    } else if (pwr == EV_SINGLE || rec == EV_LONG || pwr == EV_LONG) {
      soundBack();
      state = STATE_READER_LIST;
      showReaderList(readerCursor);
    }
  }

  // NOTE ACTIONS MENU ───────────────────────────────────────────────────
  else if (state == STATE_NOTE_ACTIONS) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    int idx = noteAtFilteredIndex(listCursor);

    if (idx < 0) {
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
      return;
    }

    if (pwr == EV_SINGLE) {
      // SCORRE TRA LE 4 AZIONI
      soundNext();
      noteActionCursor = (noteActionCursor + 1) % 4;
      showNoteActions(listCursor, noteActionCursor);
    } else if (rec == EV_SINGLE) {
      // ESEGUE L'AZIONE SELEZIONATA
      soundSelect();
      if (noteActionCursor == 0) {
        // 1. READ (MD)
        if (noteIndex[idx].hasText || noteIndex[idx].uploaded) {
          mdReaderPage = 0;
          state = STATE_MD_READER;
          showNoteMdReader(listCursor, mdReaderPage);
        } else {
          soundNext(); // Feedback not synced yet
        }
      } else if (noteActionCursor == 1) {
        // 2. PLAY AUDIO WAV
        char wavPath[64];
        snprintf(wavPath, sizeof(wavPath), "%s/note_%03d.wav", NOTES_DIR, noteIndex[idx].num);
        showPlaybackOverlay();
        playWavFile(wavPath);
        showNoteActions(listCursor, noteActionCursor);
      } else if (noteActionCursor == 2) {
        // 3. INFO & METADATA
        state = STATE_NOTE_DETAIL;
        showNoteDetail(listCursor);
      } else if (noteActionCursor == 3) {
        // 4. DELETE NOTE
        state = STATE_DELETE_CONFIRM;
        showDeleteConfirm(noteIndex[idx].num);
      }
    } else if (rec == EV_LONG || pwr == EV_LONG || pwr == EV_DOUBLE) {
      // TORNA ALLA LISTA DELLE NOTE
      soundBack();
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
    }
  }

  // NOTE DETAIL (INFO & METADATA) ────────────────────────────────────────
  else if (state == STATE_NOTE_DETAIL) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (rec == EV_SINGLE || pwr == EV_SINGLE) {
      soundBack();
      state = STATE_NOTE_ACTIONS;
      showNoteActions(listCursor, noteActionCursor);
    } else if (rec == EV_LONG || pwr == EV_LONG) {
      soundBack();
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
    }
  }

  // MD DOCUMENT READER ───────────────────────────────────────────────────
  else if (state == STATE_MD_READER) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);
    int idx = noteAtFilteredIndex(listCursor);

    if (idx >= 0) {
      String text = noteTextContent(noteIndex[idx].num);
      int totalPages = mdCalculateTotalPages(text);

      if (pwr == EV_SINGLE) {
        // PAGINA SUCCESSIVA
        soundNext();
        mdReaderPage = (mdReaderPage + 1) % totalPages;
        showNoteMdReader(listCursor, mdReaderPage);
      } else if (rec == EV_SINGLE) {
        // PAGINA PRECEDENTE
        soundNext();
        if (mdReaderPage > 0) mdReaderPage--;
        else mdReaderPage = max(0, totalPages - 1);
        showNoteMdReader(listCursor, mdReaderPage);
      } else if (rec == EV_LONG || pwr == EV_LONG || pwr == EV_DOUBLE) {
        // ESCI DAL LETTORE E TORNA AL MENU AZIONI
        soundBack();
        state = STATE_NOTE_ACTIONS;
        showNoteActions(listCursor, noteActionCursor);
      }
    } else {
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
    }
  }

  // DELETE CONFIRM ──────────────────────────────────────────────────────
  else if (state == STATE_DELETE_CONFIRM) {
    ButtonEvent rec = readButtonEvent(BTN_REC);
    ButtonEvent pwr = readButtonEvent(BTN_PWR);

    if (rec == EV_SINGLE) {
      int idx = noteAtFilteredIndex(listCursor);
      if (idx >= 0) {
        deleteNote(noteIndex[idx].num);
        soundDelete();
      }
      listCursor = constrain(listCursor, 0, max(filteredCount() - 1, 0));
      state = STATE_NOTE_LIST;
      showNoteList(listCursor);
    } else if (pwr == EV_SINGLE || rec == EV_LONG || rec == EV_DOUBLE || pwr == EV_LONG) {
      soundBack();
      state = STATE_NOTE_ACTIONS;
      showNoteActions(listCursor, noteActionCursor);
    }
  }

  delay(15);
}
