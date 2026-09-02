#pragma once
#include <Arduino.h>
#include <vector>
#include "../../types.h"

#define ARTICLES_DIR "/articles"
#define ARTICLE_INDEX_FILE "/articles/index.txt"

void loadArticleIndex();
void saveArticleIndex();
void addArticleToIndex(int num, const char* title, const char* source, const char* date, bool isRead = false);
int  nextArticleNumber();
String articleMarkdownContent(int num);
void deleteArticle(int num);
void markArticleRead(int num, bool isRead = true);
int  articleCount();
