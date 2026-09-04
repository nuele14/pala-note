#ifndef CONFIG_H
#define CONFIG_H

#define EPD_SPI_NUM        SPI2_HOST
#define ESP32_I2C_DEV_NUM  I2C_NUM_0

#define EPD_WIDTH  200
#define EPD_HEIGHT 200
#define LVGL_SPIRAM_BUFF_LEN (EPD_WIDTH * EPD_HEIGHT * 2)

/* EPD SPI pins */
#define EPD_DC_PIN    GPIO_NUM_10
#define EPD_CS_PIN    GPIO_NUM_11
#define EPD_SCK_PIN   GPIO_NUM_12
#define EPD_MOSI_PIN  GPIO_NUM_13
#define EPD_RST_PIN   GPIO_NUM_9
#define EPD_BUSY_PIN  GPIO_NUM_8


/* Power control pins */
#define EPD_PWR_PIN     GPIO_NUM_6
#define Audio_PWR_PIN   GPIO_NUM_42
#define VBAT_PWR_PIN    GPIO_NUM_17
#define BAT_ADC_PIN     4   // ADC1_CHANNEL_3 on ESP32-S3

#define BOOT_BUTTON_PIN GPIO_NUM_0
#define PWR_BUTTON_PIN  GPIO_NUM_18

/* Deep-sleep wake-up pin */
#define ext_wakeup_pin_1 GPIO_NUM_0

/* I2C bus */
#define ESP32_I2C_SDA_PIN GPIO_NUM_47
#define ESP32_I2C_SCL_PIN GPIO_NUM_48

/* LVGL tick timing */
#define EXAMPLE_LVGL_TICK_PERIOD_MS    5
#define EXAMPLE_LVGL_TASK_MAX_DELAY_MS 500
#define EXAMPLE_LVGL_TASK_MIN_DELAY_MS 100

/* I2C peripheral addresses */
#define I2C_RTC_DEV_Address        0x51
#define I2C_SHTC3_DEV_Address      0x70

/* Button GPIO aliases */
#define BTN_REC      0
#define BTN_PWR      18
#define PWR_HOLD_PIN 17

/* SD-MMC pins */
#define SD_CLK  39
#define SD_CMD  41
#define SD_D0   40

/* Audio */
#define SAMPLE_RATE  16000
#define REC_BUF      (8 * 1024)

/* Storage paths */
#define NOTES_DIR        "/notes"
#define SCREENSAVERS_DIR "/screensavers"
#define SOUNDS_DIR       "/sounds"
#define INDEX_FILE       "/notes/index.csv"
#define TAG_FILE         "/notes/tags.txt"
#define MAX_TAGS         20

/* UI timing */
#define REC_HOLD_MS         350
#define BTN_LONG_MS         600
#define DOUBLE_MS           280
#define ULTRA_SLEEP_MS      120000UL
#define TICKER_INTERVAL_MS  950

/* Boot splash configuration */
#define ENABLE_BOOT_SPLASH           true
#define BOOT_SPLASH_MS               1200

/* Custom Bitmaps UI (ready_bitmap.h, recording_bitmap.h, pomodoro_bitmap.h) */
#define USE_CUSTOM_READY_BITMAP      true   // Mostra ready_bitmap in showIdle()
#define USE_CUSTOM_RECORDING_BITMAP  true   // Mostra recording_bitmap in showRecording()
#define USE_CUSTOM_POMODORO_BITMAP   true   // Mostra pomodoro_bitmap in showShikamaru()
#define SHOW_BATTERY_ON_READY        false  // true: mostra batteria in angolo | false: grafica 100% pulita

/* Clean Synced Notes */
#define AUTO_CLEAN_SYNCED_NOTES      false  // true: elimina subito da SD dopo sincronizzazione ACK

/* LED Status & Battery Indicators */
#define ENABLE_STATUS_LEDS           true
#define LED_RED_PIN                  GPIO_NUM_1   // Imposta pin GPIO del LED Rosso
#define LED_GREEN_PIN                GPIO_NUM_2   // Imposta pin GPIO del LED Verde
#define LED_ACTIVE_LEVEL             HIGH         // HIGH = acceso a 3.3V, LOW = active-low
#define LED_REC_BLINK_INTERVAL_MS    350          // Velocità lampeggio registrazione (ms)

/* Battery warning & Low Power LED Beacon */
#define BAT_CHECK_INTERVAL_MS        30000
#define BAT_LOW_THRESHOLD            15
#define BAT_RECOVER_THRESHOLD        20
#define BAT_LOW_LED_PULSE_MS         60000UL // Beacon LED ogni 60 secondi
#define BAT_LOW_LED_FLASH_MS         20      // Micro-impulso di 20ms a bassissimo consumo

/* Time & firmware */
#define LOCAL_TIME_OFFSET_MIN  120   // UTC+2 (Italy summer). Set to your offset.
#define FIRMWARE_VERSION       "ES1 v2.0"
#define FW_VERSION             "ES1 v2.0"

#endif // CONFIG_H