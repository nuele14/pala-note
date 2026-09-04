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

data class SttExecutionResult(
    val text: String?,
    val audioDurationSec: Float,
    val durationMs: Long,
    val error: String? = null
)

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
    suspend fun transcribeNoteWithMetrics(
        noteId: String,
        onProgress: ((status: String, audioDurationSec: Float, elapsedSec: Float) -> Unit)? = null
    ): SttExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val note = noteDao.getNoteByIdDirect(noteId)
                ?: noteDao.getPendingTranscriptions().find { it.id == noteId }
                ?: return@withContext SttExecutionResult(null, 0f, 0L, "Nota non trovata nel database")

            val audioFile = File(note.audioLocalPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                val err = "File audio non trovato sul disco: ${audioFile.name}"
                Log.w(TAG, err)
                return@withContext SttExecutionResult(null, 0f, 0L, err)
            }

            if (!modelManager.isModelReady()) {
                val err = "Modello Whisper non ancora scaricato sul dispositivo"
                Log.w(TAG, err)
                return@withContext SttExecutionResult(null, 0f, 0L, err)
            }

            // Legge i campioni PCM 16kHz dal file WAV
            val samples = readWavSamples(audioFile)
            if (samples.isEmpty()) {
                val err = "Nessun campione audio letto dal file WAV ${audioFile.name}"
                Log.w(TAG, err)
                return@withContext SttExecutionResult(null, 0f, 0L, err)
            }

            val audioDurationSec = samples.size / 16000f

            // Ottimizzazione Short-circuit: audio troppo breve (< 0.5s a 16kHz = 8000 campioni)
            if (samples.size < 8000) {
                Log.d(TAG, "Audio clip too short (< 0.5s), skipping Whisper inference.")
                val shortText = "Nota #${note.deviceNoteNum} (Clip audio troppo breve)"
                val updated = note.copy(transcriptionText = shortText, transcriptionLanguage = "it")
                noteDao.updateNote(updated)
                val durationMs = System.currentTimeMillis() - startTime
                return@withContext SttExecutionResult(shortText, audioDurationSec, durationMs)
            }

            onProgress?.invoke("Caricamento Whisper...", audioDurationSec, 0.1f)

            val rec = getOrCreateRecognizer()
            if (rec == null) {
                val err = "Inizializzazione Whisper ONNX fallita (OfflineRecognizer null)"
                Log.e(TAG, err)
                return@withContext SttExecutionResult(null, audioDurationSec, System.currentTimeMillis() - startTime, err)
            }

            Log.d(TAG, "Transcribing WAV audio on-device for note #${note.deviceNoteNum} (${audioFile.name}, ${audioDurationSec}s)...")
            onProgress?.invoke("Decodifica acustica PCM...", audioDurationSec, (System.currentTimeMillis() - startTime) / 1000f)

            val stream = rec.createStream()
            stream.acceptWaveform(samples, 16000)
            rec.decode(stream)
            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim()
            val durationMs = System.currentTimeMillis() - startTime
            val rtf = if (durationMs > 0 && audioDurationSec > 0) (durationMs / 1000f) / audioDurationSec else 0f
            Log.d(TAG, "On-device transcription completed: '$text' in ${durationMs}ms (RTF: ${rtf}x)")

            val finalText = text.ifBlank { "Nota #${note.deviceNoteNum} (${note.tag})" }
            val updated = note.copy(
                transcriptionText = finalText,
                transcriptionLanguage = "it"
            )
            noteDao.updateNote(updated)
            return@withContext SttExecutionResult(finalText, audioDurationSec, durationMs)
        } catch (t: Throwable) {
            val durationMs = System.currentTimeMillis() - startTime
            val err = "${t.javaClass.simpleName}: ${t.localizedMessage ?: t.message}"
            Log.e(TAG, "Fatal error during on-device transcription for note $noteId: $err", t)
            return@withContext SttExecutionResult(null, 0f, durationMs, err)
        }
    }

    suspend fun transcribeNote(noteId: String): String? = transcribeNoteWithMetrics(noteId).text

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
