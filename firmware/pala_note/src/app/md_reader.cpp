#include "md_reader.h"
#include "draw.h"
#include "ui.h"
#include "battery.h"
#include <vector>

static const int MD_LINES_PER_PAGE = 7;
static const int MD_CONTENT_Y0     = 34;
static const int MD_LINE_HEIGHT    = 18;
static const int MD_MAX_WIDTH      = 168;

enum LineType {
  LINE_NORMAL,
  LINE_HEADER_1,
  LINE_HEADER_2,
  LINE_TODO_UNCHECKED,
  LINE_TODO_CHECKED,
  LINE_BULLET,
  LINE_QUOTE,
  LINE_SEPARATOR,
  LINE_EMPTY
};

struct FormattedLine {
  LineType type;
  String text;
};

static void parseMarkdownLines(const String& rawMarkdown, std::vector<FormattedLine>& outLines) {
  outLines.clear();

  int start = 0;
  int totalLen = rawMarkdown.length();

  while (start < totalLen) {
    int end = rawMarkdown.indexOf('\n', start);
    if (end < 0) end = totalLen;

    String rawLine = rawMarkdown.substring(start, end);
    start = end + 1;

    // Clean carriage returns
    rawLine.replace("\r", "");
    rawLine.trim();

    if (rawLine.length() == 0) {
      FormattedLine fl;
      fl.type = LINE_EMPTY;
      fl.text = "";
      outLines.push_back(fl);
      continue;
    }

    LineType lType = LINE_NORMAL;
    String content = rawLine;

    // Detect Markdown syntax
    if (rawLine.startsWith("# ")) {
      lType = LINE_HEADER_1;
      content = rawLine.substring(2);
    } else if (rawLine.startsWith("## ")) {
      lType = LINE_HEADER_2;
      content = rawLine.substring(3);
    } else if (rawLine.startsWith("### ")) {
      lType = LINE_HEADER_2;
      content = rawLine.substring(4);
    } else if (rawLine.startsWith("- [ ] ") || rawLine.startsWith("* [ ] ")) {
      lType = LINE_TODO_UNCHECKED;
      content = rawLine.substring(6);
    } else if (rawLine.startsWith("- [x] ") || rawLine.startsWith("* [x] ") ||
               rawLine.startsWith("- [X] ") || rawLine.startsWith("* [X] ")) {
      lType = LINE_TODO_CHECKED;
      content = rawLine.substring(6);
    } else if (rawLine.startsWith("- ") || rawLine.startsWith("* ") || rawLine.startsWith("+ ")) {
      lType = LINE_BULLET;
      content = rawLine.substring(2);
    } else if (rawLine.startsWith("> ")) {
      lType = LINE_QUOTE;
      content = rawLine.substring(2);
    } else if (rawLine == "---" || rawLine == "***" || rawLine == "___") {
      lType = LINE_SEPARATOR;
      content = "";
    }

    // Clean accents & normalize for e-paper font
    content = normalizeForDisplay(content);

    // If separator or empty, push directly
    if (lType == LINE_SEPARATOR) {
      FormattedLine fl;
      fl.type = lType;
      fl.text = "";
      outLines.push_back(fl);
      continue;
    }

    // Word wrap long lines to fit MD_MAX_WIDTH
    int indentW = 0;
    if (lType == LINE_TODO_UNCHECKED || lType == LINE_TODO_CHECKED) indentW = 14;
    else if (lType == LINE_BULLET || lType == LINE_QUOTE) indentW = 10;

    int availW = MD_MAX_WIDTH - indentW;
    int pos = 0;

    while (pos < (int)content.length()) {
      while (pos < (int)content.length() && content[pos] == ' ') pos++;
      if (pos >= (int)content.length()) break;

      String subLine = "";
      int lastValidPos = pos;

      while (pos < (int)content.length()) {
        int wordEnd = content.indexOf(' ', pos);
        if (wordEnd < 0) wordEnd = content.length();
        String word = content.substring(pos, wordEnd);

        String testLine = subLine.length() ? subLine + " " + word : word;
        if (textW(testLine.c_str(), 1) <= availW) {
          subLine = testLine;
          pos = wordEnd;
          while (pos < (int)content.length() && content[pos] == ' ') pos++;
          lastValidPos = pos;
        } else {
          if (subLine.length() == 0) {
            // Very long word: force split
            subLine = word;
            pos = wordEnd;
          } else {
            pos = lastValidPos;
          }
          break;
        }
      }

      FormattedLine fl;
      fl.type = (outLines.empty() || outLines.back().type != lType) ? lType : LINE_NORMAL;
      fl.text = subLine;
      outLines.push_back(fl);
    }
  }
}

void mdReaderInit() {
}

int mdCalculateTotalPages(const String& rawMarkdown) {
  std::vector<FormattedLine> lines;
  parseMarkdownLines(rawMarkdown, lines);
  int n = lines.size();
  if (n == 0) return 1;
  return (n + MD_LINES_PER_PAGE - 1) / MD_LINES_PER_PAGE;
}

MdRenderResult showMdDocument(const char* title, const String& rawMarkdown, int pageIndex, bool isAudioAvailable) {
  clearWhite();

  std::vector<FormattedLine> lines;
  parseMarkdownLines(rawMarkdown, lines);

  int totalLines = lines.size();
  int totalPages = max(1, (totalLines + MD_LINES_PER_PAGE - 1) / MD_LINES_PER_PAGE);
  int currentPage = constrain(pageIndex, 0, totalPages - 1);

  // 1. Top Header Bar
  char headerTitle[32];
  if (title && strlen(title) > 0) {
    strncpy(headerTitle, title, sizeof(headerTitle) - 1);
    headerTitle[sizeof(headerTitle) - 1] = 0;
  } else {
    snprintf(headerTitle, sizeof(headerTitle), "Document");
  }

  drawStrFit(16, 12, 90, headerTitle, 1, BLACK);

  char pageStr[16];
  snprintf(pageStr, sizeof(pageStr), "%d/%d", currentPage + 1, totalPages);
  drawStr(112, 12, pageStr, 1, BLACK);

  drawBatteryMicroBadge(154, 12, readBatteryPercent(), BLACK);
  hline(16, 26, W - 32, BLACK);

  // 2. Render Page Lines
  int startLine = currentPage * MD_LINES_PER_PAGE;
  int endLine = min(startLine + MD_LINES_PER_PAGE, totalLines);

  int curY = MD_CONTENT_Y0;

  for (int i = startLine; i < endLine; i++) {
    const FormattedLine& fl = lines[i];

    switch (fl.type) {
      case LINE_HEADER_1:
        drawStr(16, curY + 2, fl.text.c_str(), 1, BLACK);
        hline(16, curY + 16, textW(fl.text.c_str(), 1) + 4, BLACK);
        break;

      case LINE_HEADER_2:
        fillRoundRect(16, curY - 1, textW(fl.text.c_str(), 1) + 8, 15, 3, BLACK);
        drawStr(20, curY + 2, fl.text.c_str(), 1, WHITE);
        break;

      case LINE_TODO_UNCHECKED:
        strokeRoundRect(16, curY + 2, 10, 10, 2, 1, BLACK);
        drawStr(30, curY + 2, fl.text.c_str(), 1, BLACK);
        break;

      case LINE_TODO_CHECKED:
        strokeRoundRect(16, curY + 2, 10, 10, 2, 1, BLACK);
        fillCircle(21, curY + 7, 2, BLACK);
        drawStr(30, curY + 2, fl.text.c_str(), 1, BLACK);
        hline(30, curY + 8, textW(fl.text.c_str(), 1), BLACK); // Strike-through
        break;

      case LINE_BULLET:
        fillCircle(19, curY + 7, 2, BLACK);
        drawStr(27, curY + 2, fl.text.c_str(), 1, BLACK);
        break;

      case LINE_QUOTE:
        vline(16, curY, MD_LINE_HEIGHT, BLACK);
        vline(17, curY, MD_LINE_HEIGHT, BLACK);
        drawStr(22, curY + 2, fl.text.c_str(), 1, BLACK);
        break;

      case LINE_SEPARATOR:
        hline(24, curY + 8, W - 48, BLACK);
        break;

      case LINE_EMPTY:
        // skip line
        break;

      case LINE_NORMAL:
      default:
        drawStr(16, curY + 2, fl.text.c_str(), 1, BLACK);
        break;
    }

    curY += MD_LINE_HEIGHT;
  }

  // 3. Footer Bar with icons
  drawFooterNav("page", "prev", "actions");

  refresh();

  MdRenderResult res;
  res.totalPages = totalPages;
  res.currentPage = currentPage;
  return res;
}
