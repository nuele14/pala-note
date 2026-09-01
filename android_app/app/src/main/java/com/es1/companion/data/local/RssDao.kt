package com.es1.companion.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM articles WHERE queuedForSync = 1 ORDER BY datetime(createdUtc) DESC")
    fun getQueuedArticlesFlow(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE queuedForSync = 1")
    suspend fun getQueuedArticlesList(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE link = :link LIMIT 1")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    // Coda di sincronizzazione
    @Query("UPDATE articles SET queuedForSync = 1, targetDeviceId = :targetDeviceId WHERE id IN (:ids)")
    suspend fun queueArticlesForSync(ids: List<String>, targetDeviceId: String = "ALL")

    @Query("UPDATE articles SET queuedForSync = 0 WHERE id = :id")
    suspend fun removeFromSyncQueue(id: String)

    @Query("UPDATE articles SET queuedForSync = 0")
    suspend fun clearSyncQueue()

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun markArticleRead(id: String, isRead: Boolean)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    // ── Multi-Device Tracking ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceSync(sync: ArticleDeviceSyncEntity)

    @Query("SELECT * FROM article_device_sync")
    fun getAllDeviceSyncsFlow(): Flow<List<ArticleDeviceSyncEntity>>

    @Query("SELECT * FROM article_device_sync WHERE articleId = :articleId")
    suspend fun getDeviceSyncsForArticle(articleId: String): List<ArticleDeviceSyncEntity>

    @Query("SELECT DISTINCT articleId FROM article_device_sync WHERE deviceId = :deviceId")
    suspend fun getSyncedArticleIdsForDevice(deviceId: String): List<String>

    @Transaction
    suspend fun markArticleSyncedAndDequeue(
        articleId: String,
        deviceId: String,
        deviceName: String,
        syncedUtc: String
    ) {
        insertDeviceSync(
            ArticleDeviceSyncEntity(
                articleId = articleId,
                deviceId = deviceId,
                deviceName = deviceName,
                syncedAtUtc = syncedUtc
            )
        )
        removeFromSyncQueue(articleId)
    }
}
