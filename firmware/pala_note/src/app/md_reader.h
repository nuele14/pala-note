#pragma once
#include <Arduino.h>

/**
 * Generic Markdown / Text Reader for E-Paper (200x200)
 * Formats Markdown elements (# headers, - [ ] checkboxes, * bullets, quotes)
 * Supports pagination and audio playback overlay.
 * Reusable for notes, transcripts, and RSS / Hacker News articles.
 */

struct MdRenderResult {
  int totalPages;
  int currentPage;
};

void mdReaderInit();

// Displays a page of markdown / text
MdRenderResult showMdDocument(const char* title, const String& rawMarkdown, int pageIndex, bool isAudioAvailable = false);

// Helper to calculate total pages without rendering
int mdCalculateTotalPages(const String& rawMarkdown);
