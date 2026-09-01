package com.es1.companion.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY datetime(createdUtc) DESC, deviceNoteNum DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE LOWER(tag) = LOWER(:tag) ORDER BY datetime(createdUtc) DESC, deviceNoteNum DESC")
    fun getNotesByTag(tag: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdDirect(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deviceNoteNum = :noteNum AND deviceId = :deviceId LIMIT 1")
    suspend fun getNoteByDeviceNum(noteNum: Int, deviceId: String): NoteEntity?

    @Query("""
        SELECT * FROM notes 
        WHERE (transcriptionText LIKE '%' || :query || '%' 
           OR elaboratedTitle LIKE '%' || :query || '%' 
           OR elaboratedMarkdown LIKE '%' || :query || '%'
           OR tag LIKE '%' || :query || '%')
        ORDER BY datetime(createdUtc) DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE transcriptionText IS NULL OR transcriptionText = ''")
    suspend fun getPendingTranscriptions(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE transcriptionText IS NOT NULL AND transcriptionText != '' AND elaboratedMarkdown IS NULL")
    suspend fun getPendingElaborations(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    // Tag Rules
    @Query("SELECT * FROM tag_rules ORDER BY tag ASC")
    fun getAllTagRules(): Flow<List<TagRuleEntity>>

    @Query("SELECT * FROM tag_rules WHERE tag = :tag LIMIT 1")
    suspend fun getTagRule(tag: String): TagRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagRules(rules: List<TagRuleEntity>)

    @Update
    suspend fun updateTagRule(rule: TagRuleEntity)
}
