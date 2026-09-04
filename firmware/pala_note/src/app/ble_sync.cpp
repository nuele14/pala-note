#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <SD_MMC.h>

#include "ble_sync.h"
#include "network.h"
#include "notes.h"
#include "reader.h"
#include "battery.h"
#include "ui.h"
#include "../../sounds.h"
#include "../../config.h"
#include "../../globals.h"

static BLEServer*         pServer        = nullptr;
static BLECharacteristic* pCmdChar       = nullptr;
static BLECharacteristic* pDataChar      = nullptr;

static bool bleActive          = false;
static bool deviceConnected    = false;
static bool oldDeviceConnected = false;
static bool syncDone           = false;

// Audio Streaming State (ES1 -> Companion)
static bool   streamingNote    = false;
static int    streamNoteNum    = -1;
static File   streamFile;
static size_t streamFileSize   = 0;
static size_t streamBytesSent  = 0;
static int    streamItemIdx    = 0;
static int    streamTotalItems = 0;
static int    lastDrawnPct     = 0;
static unsigned long lastScreenUpdateMs = 0;

// Article Receiving State (Companion -> ES1)
static bool   receivingArticle  = false;
static String articleTitle      = "";
static String articleSource     = "";
static size_t articleTargetSize = 0;
static size_t articleBytesRx    = 0;
static File   articleFile;
static int    articleNum        = 0;

class ES1ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("[BLE] Companion connected");
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("[BLE] Companion disconnected");
  }
};

static void sendCmdResponse(const String& json) {
  if (pCmdChar && deviceConnected) {
    pCmdChar->setValue((uint8_t*)json.c_str(), json.length());
    pCmdChar->notify();
    delay(5);
  }
}

class CmdCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String rx = String(pChar->getValue().c_str());
    if (rx.length() == 0) return;
    Serial.printf("[BLE CMD RX] %s\n", rx.c_str());

    // 1. INFO command
    if (rx.indexOf("\"INFO\"") >= 0) {
      int pending = pendingSyncCount();
      size_t bytes = pendingSyncBytes();
      String resp = "{\"type\":\"INFO\",\"device\":\"" + getSoftApSsid() +
                    "\",\"fw\":\"" + String(FIRMWARE_VERSION) +
                    "\",\"bat\":" + String(readBatteryPercent()) +
                    ",\"pending\":" + String(pending) +
                    ",\"bytes\":" + String(bytes) + "}";
      sendCmdResponse(resp);
      return;
    }

    // 2. LIST command (Streams list of notes)
    if (rx.indexOf("\"LIST\"") >= 0) {
      int total = (int)noteIndex.size();
      sendCmdResponse("{\"type\":\"LIST_START\",\"total\":" + String(total) + "}");
      for (int i = 0; i < total; i++) {
        int num = noteIndex[i].num;
        size_t sz = noteAudioFileSize(num);
        float dur = noteAudioDurationSec(num);
        String item = "{\"type\":\"NOTE_ITEM\",\"num\":" + String(num) +
                      ",\"tag\":\"" + String(noteIndex[i].tag) +
                      "\",\"dur\":" + String(dur, 1) +
                      ",\"size\":" + String(sz) +
                      ",\"up\":" + (noteIndex[i].uploaded ? "true" : "false") + "}";
        sendCmdResponse(item);
      }
      sendCmdResponse("{\"type\":\"LIST_END\"}");
      return;
    }

    // 3. GET_NOTE command
    int getNoteIdx = rx.indexOf("\"GET_NOTE\"");
    if (getNoteIdx >= 0) {
      int numIdx = rx.indexOf("\"num\":");
      if (numIdx >= 0) {
        int n = rx.substring(numIdx + 6).toInt();
        char path[64];
        snprintf(path, sizeof(path), "%s/note_%03d.wav", NOTES_DIR, n);
        if (SD_MMC.exists(path)) {
          streamFile = SD_MMC.open(path, FILE_READ);
          if (streamFile) {
            streamNoteNum = n;
            streamFileSize = streamFile.size();
            streamBytesSent = 0;
            streamingNote = true;

            int idxPos = rx.indexOf("\"idx\":");
            if (idxPos >= 0) {
              streamItemIdx = rx.substring(idxPos + 6).toInt();
            } else {
              streamItemIdx++;
            }

            int totPos = rx.indexOf("\"total\":");
            if (totPos >= 0) {
              streamTotalItems = rx.substring(totPos + 8).toInt();
            } else if (streamTotalItems < streamItemIdx) {
              streamTotalItems = streamItemIdx;
            }

            lastDrawnPct = 0;
            lastScreenUpdateMs = millis();

            char msg[32];
            snprintf(msg, sizeof(msg), "Note #%03d (%u KB)", n, (unsigned int)(streamFileSize / 1024));
            showSyncProgress("BLE", msg, streamItemIdx, streamTotalItems, 0);

            String resp = "{\"type\":\"NOTE_START\",\"num\":" + String(n) +
                          ",\"size\":" + String(streamFileSize) + "}";
            sendCmdResponse(resp);
            Serial.printf("[BLE] Starting note #%03d audio stream (%u bytes) [%d/%d]\n",
                          n, (unsigned int)streamFileSize, streamItemIdx, streamTotalItems);
            return;
          }
        }
        sendCmdResponse("{\"type\":\"ERROR\",\"msg\":\"File not found\"}");
      }
      return;
    }

    // 4. ACK command
    int ackIdx = rx.indexOf("\"ACK\"");
    if (ackIdx >= 0) {
      int numIdx = rx.indexOf("\"num\":");
      if (numIdx >= 0) {
        int n = rx.substring(numIdx + 6).toInt();
        markUploaded(n);
        Serial.printf("[BLE] Note #%03d marked as uploaded\n", n);
        sendCmdResponse("{\"type\":\"ACK_OK\",\"num\":" + String(n) + "}");
      }
      return;
    }

    // 5. PUSH_ARTICLE_START
    int pushIdx = rx.indexOf("\"PUSH_ARTICLE_START\"");
    if (pushIdx >= 0) {
      // Parse title
      int tStart = rx.indexOf("\"title\":\"");
      if (tStart >= 0) {
        int tEnd = rx.indexOf("\"", tStart + 9);
        if (tEnd > tStart) articleTitle = rx.substring(tStart + 9, tEnd);
      }
      // Parse source / tag
      int sStart = rx.indexOf("\"source\":\"");
      if (sStart >= 0) {
        int sEnd = rx.indexOf("\"", sStart + 10);
        if (sEnd > sStart) articleSource = rx.substring(sStart + 10, sEnd);
      }
      // Parse target size
      int szIdx = rx.indexOf("\"size\":");
      if (szIdx >= 0) {
        articleTargetSize = (size_t)rx.substring(szIdx + 7).toInt();
      }

      if (!SD_MMC.exists(ARTICLES_DIR)) SD_MMC.mkdir(ARTICLES_DIR);
      articleNum = nextArticleNumber();
      char artPath[64];
      snprintf(artPath, sizeof(artPath), "%s/art_%03d.md", ARTICLES_DIR, articleNum);
      articleFile = SD_MMC.open(artPath, FILE_WRITE);
      articleBytesRx = 0;
      receivingArticle = true;

      sendCmdResponse("{\"type\":\"ARTICLE_READY\",\"num\":" + String(articleNum) + "}");
      Serial.printf("[BLE] Ready to receive article #%03d '%s' (%u bytes)\n",
                    articleNum, articleTitle.c_str(), (unsigned int)articleTargetSize);
      return;
    }

    // 6. PUSH_ARTICLE_END
    if (rx.indexOf("\"PUSH_ARTICLE_END\"") >= 0) {
      if (receivingArticle) {
        if (articleFile) articleFile.close();
        receivingArticle = false;

        // Save metadata
        char metaPath[64];
        snprintf(metaPath, sizeof(metaPath), "%s/art_%03d.meta", ARTICLES_DIR, articleNum);
        File fMeta = SD_MMC.open(metaPath, FILE_WRITE);
        if (fMeta) {
          fMeta.printf("title=%s\nsource=%s\ndate=%s\n",
                       articleTitle.c_str(), articleSource.c_str(), currentUtcIso().c_str());
          fMeta.close();
        }

        addArticleToIndex(articleNum, articleTitle.c_str(), articleSource.c_str(), currentUtcIso().c_str(), false);
        Serial.printf("[BLE] Article #%03d saved successfully\n", articleNum);
        sendCmdResponse("{\"type\":\"ARTICLE_SAVED\",\"num\":" + String(articleNum) + "}");
      }
      return;
    }

    // 7. DONE command
    if (rx.indexOf("\"DONE\"") >= 0) {
      syncDone = true;
      sendCmdResponse("{\"type\":\"DONE_OK\"}");
      Serial.println("[BLE] Sync completed by Companion");
      return;
    }
  }
};

class DataCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    if (receivingArticle && articleFile) {
      uint8_t* pData = pChar->getData();
      size_t len = pChar->getLength();
      if (len > 0) {
        articleFile.write(pData, len);
        articleBytesRx += len;
        if (articleTargetSize > 0) {
          int pct = (int)((articleBytesRx * 100) / articleTargetSize);
          int milestone = (pct / 25) * 25;
          if (milestone > lastDrawnPct && milestone < 100 && (millis() - lastScreenUpdateMs >= 1200)) {
            lastDrawnPct = milestone;
            lastScreenUpdateMs = millis();
            showSyncProgress("BLE", articleTitle.c_str(), 1, 1, milestone);
          }
        }
      }
    }
  }
};

void startBleSync() {
  if (bleActive) return;

  syncDone = false;
  streamingNote = false;
  receivingArticle = false;
  streamItemIdx = 0;
  int pending = pendingSyncCount();
  streamTotalItems = (pending > 0) ? pending : (int)noteIndex.size();
  lastDrawnPct = 0;
  lastScreenUpdateMs = 0;

  String devName = getSoftApSsid();
  BLEDevice::init(devName.c_str());
  BLEDevice::setMTU(517); // Allow max BLE ATT MTU for high throughput

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ES1ServerCallbacks());

  BLEService* pService = pServer->createService(ES1_SERVICE_UUID);

  pCmdChar = pService->createCharacteristic(
    ES1_CHAR_CMD_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_WRITE |
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCmdChar->addDescriptor(new BLE2902());
  pCmdChar->setCallbacks(new CmdCallbacks());

  pDataChar = pService->createCharacteristic(
    ES1_CHAR_DATA_UUID,
    BLECharacteristic::PROPERTY_WRITE_NR |
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pDataChar->addDescriptor(new BLE2902());
  pDataChar->setCallbacks(new DataCallbacks());

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(ES1_SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // 7.5ms interval
  pAdvertising->setMaxPreferred(0x12); // 22.5ms interval
  BLEDevice::startAdvertising();

  bleActive = true;
  Serial.printf("[BLE] Server started, advertising as '%s'\n", devName.c_str());
  showSyncBleMode(devName.c_str(), pendingSyncCount());
}

void stopBleSync() {
  if (!bleActive) return;

  if (streamingNote && streamFile) {
    streamFile.close();
    streamingNote = false;
  }
  if (receivingArticle && articleFile) {
    articleFile.close();
    receivingArticle = false;
  }

  BLEDevice::deinit(true);
  pServer = nullptr;
  pCmdChar = nullptr;
  pDataChar = nullptr;
  bleActive = false;
  deviceConnected = false;
  syncDone = false;
  Serial.println("[BLE] Server stopped");
}

bool isBleSyncActive() {
  return bleActive;
}

bool isBleSyncDone() {
  return syncDone;
}

bool isBleDeviceConnected() {
  return deviceConnected;
}

void handleBleSyncLoop() {
  if (!bleActive) return;

  // Handle connection state changes
  if (deviceConnected != oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
    if (deviceConnected) {
      showSyncProgress("BLE", "Connected!", 0, 0, 0);
    } else {
      if (!syncDone) {
        showSyncBleMode(getSoftApSsid().c_str(), pendingSyncCount());
      }
    }
  }

  // Handle active audio note streaming (ES1 -> Companion)
  if (streamingNote && streamFile && deviceConnected) {
    uint8_t chunk[480];
    int readBytes = streamFile.read(chunk, sizeof(chunk));
    if (readBytes > 0) {
      pDataChar->setValue(chunk, readBytes);
      pDataChar->notify();
      streamBytesSent += readBytes;
      delay(10); // 10ms pacing: ~48 KB/s smooth transmission without overflowing BLE buffers

      if (streamFileSize > 0) {
        int pct = (int)((streamBytesSent * 100) / streamFileSize);
        int milestone = (pct / 25) * 25;
        if (milestone > lastDrawnPct && milestone < 100 && (millis() - lastScreenUpdateMs >= 1200)) {
          lastDrawnPct = milestone;
          lastScreenUpdateMs = millis();
          char msg[32];
          snprintf(msg, sizeof(msg), "Note #%03d (%u KB)", streamNoteNum, (unsigned int)(streamFileSize / 1024));
          showSyncProgress("BLE", msg, streamItemIdx, streamTotalItems, milestone);
        }
      }
    } else {
      // File finished
      streamFile.close();
      streamingNote = false;
      Serial.printf("[BLE] Note #%03d audio stream finished (%u bytes sent)\n",
                    streamNoteNum, (unsigned int)streamBytesSent);
      char msg[32];
      snprintf(msg, sizeof(msg), "Note #%03d (Sent)", streamNoteNum);
      showSyncProgress("BLE", msg, streamItemIdx, streamTotalItems, 100);
      lastDrawnPct = 100;
      lastScreenUpdateMs = millis();
      delay(150);
      String endJson = "{\"type\":\"NOTE_END\",\"num\":" + String(streamNoteNum) + "}";
      sendCmdResponse(endJson);
    }
  }
}
