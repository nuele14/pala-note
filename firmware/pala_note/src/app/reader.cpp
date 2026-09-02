#include "reader.h"
#include "../../config.h"
#include "../../globals.h"
#include "SD_MMC.h"

void loadArticleIndex() {
  articleIndex.clear();
  if (!SD_MMC.exists(ARTICLES_DIR)) {
    SD_MMC.mkdir(ARTICLES_DIR);
  }

  if (SD_MMC.exists(ARTICLE_INDEX_FILE)) {
    File f = SD_MMC.open(ARTICLE_INDEX_FILE);
    if (f) {
      while (f.available()) {
        String ln = f.readStringUntil('\n');
        ln.trim();
        if (!ln.length()) continue;

        int c1 = ln.indexOf(',');
        int c2 = ln.indexOf(',', c1 + 1);
        int c3 = ln.indexOf(',', c2 + 1);
        int c4 = ln.indexOf(',', c3 + 1);

        if (c1 < 0 || c2 < 0) continue;

        ArticleEntry e;
        e.num = ln.substring(0, c1).toInt();
        strncpy(e.title, ln.substring(c1 + 1, c2).c_str(), sizeof(e.title) - 1);
        e.title[sizeof(e.title) - 1] = '\0';

        if (c3 >= 0) {
          strncpy(e.source, ln.substring(c2 + 1, c3).c_str(), sizeof(e.source) - 1);
          e.source[sizeof(e.source) - 1] = '\0';
          if (c4 >= 0) {
            strncpy(e.date, ln.substring(c3 + 1, c4).c_str(), sizeof(e.date) - 1);
            e.date[sizeof(e.date) - 1] = '\0';
            e.isRead = (ln.substring(c4 + 1).toInt() == 1);
          } else {
            strncpy(e.date, ln.substring(c3 + 1).c_str(), sizeof(e.date) - 1);
            e.date[sizeof(e.date) - 1] = '\0';
            e.isRead = false;
          }
        } else {
          strncpy(e.source, ln.substring(c2 + 1).c_str(), sizeof(e.source) - 1);
          e.source[sizeof(e.source) - 1] = '\0';
          strcpy(e.date, "");
          e.isRead = false;
        }

        articleIndex.push_back(e);
      }
      f.close();
    }
  }

  // Scan directory for unindexed articles
  File dir = SD_MMC.open(ARTICLES_DIR);
  if (dir && dir.isDirectory()) {
    bool changed = false;
    File file = dir.openNextFile();
    while (file) {
      String name = file.name();
      if (name.startsWith("/")) name = name.substring(name.lastIndexOf('/') + 1);
      if (name.startsWith("art_") && name.endsWith(".md")) {
        int num = name.substring(4, 7).toInt();
        if (num > 0) {
          bool found = false;
          for (size_t i = 0; i < articleIndex.size(); i++) {
            if (articleIndex[i].num == num) { found = true; break; }
          }
          if (!found) {
            ArticleEntry e;
            e.num = num;
            snprintf(e.title, sizeof(e.title), "Article #%03d", num);
            strcpy(e.source, "News");
            strcpy(e.date, "");
            e.isRead = false;
            articleIndex.push_back(e);
            changed = true;
          }
        }
      }
      file = dir.openNextFile();
    }
    dir.close();
    if (changed) saveArticleIndex();
  }
}

void saveArticleIndex() {
  const char* tmp = "/articles/index.tmp";
  if (SD_MMC.exists(tmp)) SD_MMC.remove(tmp);
  File f = SD_MMC.open(tmp, FILE_WRITE);
  if (!f) return;
  for (size_t i = 0; i < articleIndex.size(); i++) {
    f.printf("%d,%s,%s,%s,%d\n",
             articleIndex[i].num,
             articleIndex[i].title,
             articleIndex[i].source,
             articleIndex[i].date,
             articleIndex[i].isRead ? 1 : 0);
  }
  f.close();
  if (SD_MMC.exists(ARTICLE_INDEX_FILE)) SD_MMC.remove(ARTICLE_INDEX_FILE);
  SD_MMC.rename(tmp, ARTICLE_INDEX_FILE);
}

void addArticleToIndex(int num, const char* title, const char* source, const char* date, bool isRead) {
  for (size_t i = 0; i < articleIndex.size(); i++) {
    if (articleIndex[i].num == num) {
      strncpy(articleIndex[i].title, title, sizeof(articleIndex[i].title) - 1);
      strncpy(articleIndex[i].source, source, sizeof(articleIndex[i].source) - 1);
      strncpy(articleIndex[i].date, date, sizeof(articleIndex[i].date) - 1);
      articleIndex[i].isRead = isRead;
      saveArticleIndex();
      return;
    }
  }

  ArticleEntry e;
  e.num = num;
  strncpy(e.title, title, sizeof(e.title) - 1);
  e.title[sizeof(e.title) - 1] = '\0';
  strncpy(e.source, source, sizeof(e.source) - 1);
  e.source[sizeof(e.source) - 1] = '\0';
  strncpy(e.date, date, sizeof(e.date) - 1);
  e.date[sizeof(e.date) - 1] = '\0';
  e.isRead = isRead;
  articleIndex.insert(articleIndex.begin(), e); // Insert at beginning (newest first)
  saveArticleIndex();
}

int nextArticleNumber() {
  int maxNum = 0;
  for (size_t i = 0; i < articleIndex.size(); i++) {
    if (articleIndex[i].num > maxNum) maxNum = articleIndex[i].num;
  }
  return maxNum + 1;
}

String articleMarkdownContent(int num) {
  char path[64];
  snprintf(path, sizeof(path), "%s/art_%03d.md", ARTICLES_DIR, num);
  if (!SD_MMC.exists(path)) {
    // Fallback to /notes/note_XXX.md if migrated
    snprintf(path, sizeof(path), "%s/note_%03d.md", NOTES_DIR, num);
    if (!SD_MMC.exists(path)) return "";
  }

  File f = SD_MMC.open(path);
  if (!f) return "";
  String out = "";
  while (f.available() && out.length() < 16384) {
    out += (char)f.read();
  }
  f.close();
  return out;
}

void deleteArticle(int num) {
  char mdPath[64];
  snprintf(mdPath, sizeof(mdPath), "%s/art_%03d.md", ARTICLES_DIR, num);
  if (SD_MMC.exists(mdPath)) SD_MMC.remove(mdPath);

  char metaPath[64];
  snprintf(metaPath, sizeof(metaPath), "%s/art_%03d.meta", ARTICLES_DIR, num);
  if (SD_MMC.exists(metaPath)) SD_MMC.remove(metaPath);

  for (auto it = articleIndex.begin(); it != articleIndex.end(); ++it) {
    if (it->num == num) {
      articleIndex.erase(it);
      break;
    }
  }
  saveArticleIndex();
}

void markArticleRead(int num, bool isRead) {
  for (size_t i = 0; i < articleIndex.size(); i++) {
    if (articleIndex[i].num == num) {
      articleIndex[i].isRead = isRead;
      saveArticleIndex();
      break;
    }
  }
}

int articleCount() {
  return (int)articleIndex.size();
}
