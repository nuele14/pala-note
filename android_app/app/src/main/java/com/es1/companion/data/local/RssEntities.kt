package com.es1.companion.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Genera un UUID v3 deterministico e immutabile a partire dal link o guid canonico dell'articolo.
 * Questo garantisce che l'identificativo rimanga identico nel tempo, tra dispositivi diversi,
 * e a seguito di esportazioni o ripristini da backup cloud.
 */
fun generateDeterministicArticleId(linkOrGuid: String): String {
    val clean = linkOrGuid.trim()
    return UUID.nameUUIDFromBytes(clean.toByteArray(StandardCharsets.UTF_8)).toString()
}

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
    val id: String, // Generato deterministicamente tramite generateDeterministicArticleId(link)
    val feedId: String,
    val feedTitle: String,
    val title: String,
    val author: String? = null,
    val link: String,
    val guid: String? = null,
    val pubDate: String? = null,
    val rawSummary: String = "",
    val markdownContent: String = "",
    val fullHtmlContent: String? = null, // HTML completo e ripulito dell'articolo per visualizzazione reader mode
    val isFullContent: Boolean = false, // true se il testo completo è stato estratto dal web oltre il feed RSS
    val isRead: Boolean = false,
    val queuedForSync: Boolean = false, // In attesa di trasferimento alla prossima sincronizzazione Wi-Fi
    val targetDeviceId: String = "ALL", // Dispositivo target per la coda (es. ALL, ES1_DEFAULT, KINDLE)
    val createdUtc: String
)

/**
 * Tabella relazionale multi-dispositivo.
 * Traccia su quali dispositivi (ES1 #1, ES1 #2, Kindle, ecc.) ogni articolo è stato trasferito con successo.
 */
@Entity(
    tableName = "article_device_sync",
    primaryKeys = ["articleId", "deviceId"],
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleId"]),
        Index(value = ["deviceId"])
    ]
)
data class ArticleDeviceSyncEntity(
    val articleId: String,
    val deviceId: String,          // es. "ES1_DEFAULT", "ES1_WORK", "KINDLE_PAPERWHITE"
    val deviceName: String? = null,// es. "ES1 Note Reader", "Kindle"
    val syncedAtUtc: String        // Data esatta di trasferimento riuscito
)
