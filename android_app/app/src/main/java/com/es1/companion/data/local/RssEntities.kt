package com.es1.companion.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "rss_feeds")
data class RssFeedEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val category: String = "Tech",
    val enabled: Boolean = true,
    val lastFetchedUtc: String? = null,
    val createdAtUtc: String? = null
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = RssFeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["link"], unique = true)
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val feedId: String,
    val feedTitle: String,
    val title: String,
    val author: String? = null,
    val link: String,
    val pubDate: String? = null,
    val rawSummary: String = "",
    val markdownContent: String = "",
    val isRead: Boolean = false,
    val isPushedToDevice: Boolean = false,
    val pushedAtUtc: String? = null,
    val createdUtc: String
)
