package com.es1.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.room.migration.Migration

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN fullHtmlContent TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN isFullContent INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [NoteEntity::class, TagRuleEntity::class, RssFeedEntity::class, ArticleEntity::class, ArticleDeviceSyncEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun rssDao(): RssDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "es1_notes.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        populateDefaultTagRules(database.noteDao())
                        populateDefaultRssFeeds(database.rssDao())
                    }
                }
            }
        }

        suspend fun populateDefaultRssFeeds(dao: RssDao) {
            val defaultFeeds = listOf(
                RssFeedEntity(
                    id = "hn_top",
                    title = "Hacker News",
                    url = "https://news.ycombinator.com/rss",
                    category = "Tech",
                    enabled = true
                ),
                RssFeedEntity(
                    id = "antirez_blog",
                    title = "Antirez Blog",
                    url = "http://antirez.com/rss",
                    category = "Programming",
                    enabled = true
                ),
                RssFeedEntity(
                    id = "ars_technica",
                    title = "Ars Technica",
                    url = "https://feeds.arstechnica.com/arstechnica/index",
                    category = "Tech",
                    enabled = true
                )
            )
            dao.insertFeeds(defaultFeeds)
        }

        suspend fun populateDefaultTagRules(dao: NoteDao) {
            val defaultRules = listOf(
                TagRuleEntity(
                    tag = "Todo",
                    systemPrompt = "Estrai una lista di compiti azionabili in formato markdown checkbox (- [ ]). Sii conciso e chiaro.",
                    targetFolder = "UnoNotes/Todo",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Meeting",
                    systemPrompt = "Estrai partecipanti, punti chiave discussi, decisioni prese e action items con assegnatari.",
                    targetFolder = "UnoNotes/Meeting",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Idea",
                    systemPrompt = "Sviluppa l'idea con: Titolo accattivante, Obiettivo, Punti di forza, Prossimi passi per validarla.",
                    targetFolder = "UnoNotes/Idee",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Work",
                    systemPrompt = "Organizza le informazioni di lavoro in modo professionale con contesto, stato e deliverable.",
                    targetFolder = "UnoNotes/Lavoro",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Buy",
                    systemPrompt = "Crea una lista della spesa o acquisti ordinata per categorie.",
                    targetFolder = "UnoNotes/Acquisti",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Private",
                    systemPrompt = "Revisiona la nota personale con tono riflessivo e intimo, evidenziando pensieri ed emozioni.",
                    targetFolder = "UnoNotes/Personale",
                    outputFormat = "markdown"
                ),
                TagRuleEntity(
                    tag = "Note",
                    systemPrompt = "Rielabora la nota vocale rendendola grammaticalmente perfetta, ben formattata con titoli ed elenchi.",
                    targetFolder = "UnoNotes/Note",
                    outputFormat = "markdown"
                )
            )
            dao.insertTagRules(defaultRules)
        }
    }
}
