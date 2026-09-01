package com.es1.companion.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {

    // ── Feeds ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM rss_feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<RssFeedEntity>>

    @Query("SELECT * FROM rss_feeds WHERE enabled = 1 ORDER BY title ASC")
    suspend fun getEnabledFeedsList(): List<RssFeedEntity>

    @Query("SELECT * FROM rss_feeds WHERE id = :id LIMIT 1")
    suspend fun getFeedById(id: String): RssFeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<RssFeedEntity>)

    @Update
    suspend fun updateFeed(feed: RssFeedEntity)

    @Delete
    suspend fun deleteFeed(feed: RssFeedEntity)

    // ── Articles ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM articles ORDER BY datetime(createdUtc) DESC, rowid DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY datetime(createdUtc) DESC")
    fun getArticlesByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isPushedToDevice = 0 ORDER BY datetime(createdUtc) DESC")
    suspend fun getPendingPushArticles(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE link = :link LIMIT 1")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Query("UPDATE articles SET isPushedToDevice = 1, pushedAtUtc = :utc WHERE id = :id")
    suspend fun markArticlePushed(id: String, utc: String)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun markArticleRead(id: String, isRead: Boolean)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE isRead = 1 AND isPushedToDevice = 1")
    suspend fun clearArchivedArticles()
}
