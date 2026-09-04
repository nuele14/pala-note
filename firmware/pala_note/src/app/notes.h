#pragma once
#include <Arduino.h>

void   loadIndex();
void   saveIndex();
void   addToIndex(int num, const char* tag, bool hasText);
void   updateIndexHasText(int num);
void   markUploaded(int num);
int    pendingSyncCount();
size_t pendingSyncBytes();
size_t noteAudioFileSize(int num);
float  noteAudioDurationSec(int num);
void   deleteNote(int num);
int    cleanSyncedNotes();
int    syncedNotesCount();
int    nextNoteNumber();
void   saveTag(int num, const char* tag);

void   loadTags();
void   saveTagsToFile();
void   createDefaultTags();
bool   addCustomTag(const char* newTag);
bool   deleteTag(const char* tagName);
bool   tagHasNotes(const char* tag);
void   replaceTagOnNotes(const char* oldTag, const char* newTag);

String noteMetaPath(int num);
String readNoteMetaValue(int num, const char* key);
void   writeNoteMeta(int num, const char* tag);

String noteCreatedUtc(int num);
String noteSyncedUtc(int num);
String utcToLocalDeviceLabel(const String& utcIso);
String noteCreatedDeviceLabel(int num);
String noteSyncedDeviceLabel(int num);
String currentUtcIso();
bool   isNoteUploaded(int num);
String noteTextContent(int num);

String notePreviewText(int num, size_t maxLen = 90);
String noteTickerText(int idx);
bool   activeTickerNeedsScroll(int cursor);
void   drawTickerText(int x, int y, int maxW, const String& rawText, bool active, uint8_t color);

int  filteredCount();
int  noteAtFilteredIndex(int visIdx);
