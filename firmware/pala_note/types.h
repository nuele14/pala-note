#pragma once
#include <vector>

typedef enum {
  STATE_IDLE,
  STATE_RECORDING,
  STATE_SAVED,
  STATE_TAG_SELECT,
  STATE_SYNC_CONFIRM,
  STATE_MENU,
  STATE_SHIKAMARU,
  STATE_TAG_BROWSER,
  STATE_NOTE_LIST,
  STATE_READER_LIST,
  STATE_READER_ARTICLE,
  STATE_READER_DELETE_CONFIRM,
  STATE_NOTE_ACTIONS,
  STATE_NOTE_DETAIL,
  STATE_MD_READER,
  STATE_DELETE_CONFIRM,
  STATE_SETTINGS,
  STATE_DEVICE_INFO,
  STATE_TRANSFER,
  STATE_ERROR
} AppState;

enum ButtonEvent { EV_NONE, EV_SINGLE, EV_LONG, EV_DOUBLE };

struct NoteEntry { int num; char tag[32]; bool hasText; bool uploaded; };
struct ArticleEntry { int num; char title[64]; char source[32]; char date[20]; bool isRead; };

// Content array sizes — used across notes, ui, and main loop.
#define DEFAULT_TAG_COUNT 6
#define MENU_COUNT        5
#define SETTINGS_COUNT    5

extern const char* DEFAULT_TAGS[];
extern const char* MENU_ITEMS[];
extern const char* SETTINGS_ITEMS[];
