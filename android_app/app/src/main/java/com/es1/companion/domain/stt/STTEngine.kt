package com.es1.companion.domain.stt

import android.content.Context
import android.util.Log
import com.es1.companion.data.local.NoteDao
import com.es1.companion.data.local.NoteEntity
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class STTEngine(
    private val context: Context,
    private val noteDao: NoteDao,
    val modelManager: WhisperModelManager = WhisperModelManager(context)
) {
    private val TAG = "STTEngine"
    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    private fun getOrCreateRecognizer(): OfflineRecognizer? {
        if (recognizer != null) return recognizer

        if (!modelManager.isModelReady()) {
            Log.w(TAG, "Whisper on-device model not downloaded yet.")
            return null
        }

        try {
            Log.d(TAG, "Initializing Sherpa-ONNX Whisper on-device recognizer...")
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = modelManager.getEncoderPath(),
                        decoder = modelManager.getDecoderPath(),
                        language = "it",
                        task = "transcribe"
                    ),
                    tokens = modelManager.getTokensPath(),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "whisper"
                )
            )
            recognizer = OfflineRecognizer(null, config)
            Log.d(TAG, "Sherpa-ONNX OfflineRecognizer initialized successfully.")
            return recognizer
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX recognizer: ${t.message}", t)
            return null
        }
    }

    @Synchronized
    fun unloadEngine() {
        try {
            recognizer?.release()
        } catch (_: Throwable) {}
        recognizer = null
        Log.d(TAG, "Sherpa-ONNX Whisper recognizer unloaded from memory.")
    }

    /**
     * Trascrive la nota localmente sul dispositivo con Whisper ONNX.
     */
    suspend fun transcribeNote(noteId: String): String? = withContext(Dispatchers.IO) {
        try {
            val note = noteDao.getNoteByIdDirect(noteId)
                ?: noteDao.getPendingTranscriptions().find { it.id == noteId }
                ?: return@withContext null

            val audioFile = File(note.audioLocalPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "Audio file missing for note #${note.deviceNoteNum} at ${note.audioLocalPath}")
                return@withContext null
            }

            if (!modelManager.isModelReady()) {
                Log.w(TAG, "Whisper model not ready on disk.")
                return@withContext null
            }

            val rec = getOrCreateRecognizer()
            if (rec == null) {
                Log.e(TAG, "OfflineRecognizer could not be created.")
                return@withContext null
            }

            Log.d(TAG, "Transcribing WAV audio on-device for note #${note.deviceNoteNum} (${audioFile.name})...")

            // Legge i campioni PCM 16kHz dal file WAV
            val samples = readWavSamples(audioFile)
            if (samples.isEmpty()) {
                Log.w(TAG, "No audio samples read from ${audioFile.name}")
                return@withContext null
            }

            // Ottimizzazione Short-circuit: audio troppo breve (< 0.5s a 16kHz = 8000 campioni)
            if (samples.size < 8000) {
                Log.d(TAG, "Audio clip too short (< 0.5s), skipping Whisper inference.")
                val shortText = "Nota #${note.deviceNoteNum} (Clip audio troppo breve)"
                val updated = note.copy(transcriptionText = shortText, transcriptionLanguage = "it")
                noteDao.updateNote(updated)
                return@withContext shortText
            }

            val stream = rec.createStream()
            stream.acceptWaveform(samples, 16000)
            rec.decode(stream)
            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim()
            Log.d(TAG, "On-device transcription completed: '$text'")

            val finalText = text.ifBlank { "Nota #${note.deviceNoteNum} (${note.tag})" }
            val updated = note.copy(
                transcriptionText = finalText,
                transcriptionLanguage = "it"
            )
            noteDao.updateNote(updated)
            return@withContext finalText
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error during on-device transcription for note $noteId", t)
            return@withContext null
        }
    }

    private fun readWavSamples(file: File): FloatArray {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return FloatArray(0)

            // Trova l'inizio del chunk 'data'
            var dataOffset = 44
            for (i in 0 until bytes.size - 4) {
                if (bytes[i] == 'd'.code.toByte() &&
                    bytes[i+1] == 'a'.code.toByte() &&
                    bytes[i+2] == 't'.code.toByte() &&
                    bytes[i+3] == 'a'.code.toByte()) {
                    dataOffset = i + 8
                    break
                }
            }

            val pcmBytes = bytes.size - dataOffset
            if (pcmBytes <= 1) return FloatArray(0)

            val sampleCount = pcmBytes / 2
            val validPcmBytes = sampleCount * 2
            val samples = FloatArray(sampleCount)
            val buffer = ByteBuffer.wrap(bytes, dataOffset, validPcmBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until sampleCount) {
                samples[i] = buffer.short / 32768.0f
            }
            return samples
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse WAV file ${file.name}", t)
            return FloatArray(0)
        }
    }

    suspend fun processAllPending(): Int = withContext(Dispatchers.IO) {
        val pending = noteDao.getPendingTranscriptions()
        var count = 0
        for (note in pending) {
            val res = transcribeNote(note.id)
            if (res != null) count++
        }
        return@withContext count
    }
}
