package com.es1.companion.domain.queue

import android.util.Log
import com.es1.companion.data.local.NoteDao
import com.es1.companion.data.local.NoteEntity
import com.es1.companion.domain.llm.LLMEngine
import com.es1.companion.domain.stt.STTEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

enum class JobType {
    TRANSCRIPTION,
    SYNTHESIS
}

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ProcessingJob(
    val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val noteNum: Int,
    val noteTag: String,
    val type: JobType,
    val title: String,
    val status: JobStatus = JobStatus.PENDING,
    val error: String? = null
)

class ProcessingQueueManager(
    private val sttEngine: STTEngine,
    private val llmEngine: LLMEngine,
    private val noteDao: NoteDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val TAG = "ProcessingQueueManager"
    private val mutex = Mutex()

    private val transcriptionQueue = ArrayDeque<ProcessingJob>()
    private val synthesisQueue = ArrayDeque<ProcessingJob>()

    private val _currentJob = MutableStateFlow<ProcessingJob?>(null)
    val currentJob: StateFlow<ProcessingJob?> = _currentJob.asStateFlow()

    private val _allJobs = MutableStateFlow<List<ProcessingJob>>(emptyList())
    val allJobs: StateFlow<List<ProcessingJob>> = _allJobs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _phaseSummary = MutableStateFlow<String?>(null)
    val phaseSummary: StateFlow<String?> = _phaseSummary.asStateFlow()

    private var workerJob: Job? = null
    private var activeExecutionJob: Job? = null
    private var standbyJob: Job? = null

    /**
     * Accoda una singola nota per elaborazione completa o parziale.
     */
    fun enqueueNote(note: NoteEntity) {
        scope.launch {
            mutex.withLock {
                cancelStandbyTimer()
                val audioFile = File(note.audioLocalPath)
                val needsTranscription = note.transcriptionText.isNullOrBlank() && audioFile.exists() && audioFile.length() > 0

                if (needsTranscription) {
                    val job = ProcessingJob(
                        noteId = note.id,
                        noteNum = note.deviceNoteNum,
                        noteTag = note.tag,
                        type = JobType.TRANSCRIPTION,
                        title = "Trascrizione nota #${note.deviceNoteNum}"
                    )
                    transcriptionQueue.add(job)
                } else {
                    val job = ProcessingJob(
                        noteId = note.id,
                        noteNum = note.deviceNoteNum,
                        noteTag = note.tag,
                        type = JobType.SYNTHESIS,
                        title = "Elaborazione AI nota #${note.deviceNoteNum}"
                    )
                    synthesisQueue.add(job)
                }
                updateAllJobsList()
                ensureWorkerRunning()
            }
        }
    }

    /**
     * Accoda un batch di note sincronizzate (es. da ES1).
     * Ottimizzazione a 2 fasi:
     * 1. Tutte le trascrizioni audio (Whisper caricato 1 sola volta)
     * 2. Tutte le sintesi testuali (LiteRT-LM caricato 1 sola volta)
     */
    fun enqueueBatch(notes: List<NoteEntity>) {
        if (notes.isEmpty()) return
        scope.launch {
            mutex.withLock {
                cancelStandbyTimer()
                for (note in notes) {
                    val audioFile = File(note.audioLocalPath)
                    val needsTranscription = note.transcriptionText.isNullOrBlank() && audioFile.exists() && audioFile.length() > 0

                    if (needsTranscription) {
                        transcriptionQueue.add(
                            ProcessingJob(
                                noteId = note.id,
                                noteNum = note.deviceNoteNum,
                                noteTag = note.tag,
                                type = JobType.TRANSCRIPTION,
                                title = "Trascrizione nota #${note.deviceNoteNum}"
                            )
                        )
                    } else {
                        synthesisQueue.add(
                            ProcessingJob(
                                noteId = note.id,
                                noteNum = note.deviceNoteNum,
                                noteTag = note.tag,
                                type = JobType.SYNTHESIS,
                                title = "Elaborazione AI nota #${note.deviceNoteNum}"
                            )
                        )
                    }
                }
                updateAllJobsList()
                ensureWorkerRunning()
            }
        }
    }

    /**
     * Cancella un job specifico (se in corso o in attesa).
     */
    fun cancelJob(jobId: String) {
        scope.launch {
            mutex.withLock {
                val current = _currentJob.value
                if (current?.id == jobId) {
                    Log.d(TAG, "Cancelling active job: ${current.title}")
                    activeExecutionJob?.cancel()
                    activeExecutionJob = null
                    _currentJob.value = null
                } else {
                    transcriptionQueue.removeAll { it.id == jobId }
                    synthesisQueue.removeAll { it.id == jobId }
                }
                updateAllJobsList()
                if (transcriptionQueue.isEmpty() && synthesisQueue.isEmpty() && _currentJob.value == null) {
                    _isProcessing.value = false
                    _phaseSummary.value = null
                    startStandbyTimer()
                }
            }
        }
    }

    /**
     * Cancella tutti i job presenti in coda e interrompe quello attivo.
     */
    fun cancelAllJobs() {
        scope.launch {
            mutex.withLock {
                Log.d(TAG, "Cancelling all jobs...")
                activeExecutionJob?.cancel()
                activeExecutionJob = null
                workerJob?.cancel()
                workerJob = null

                transcriptionQueue.clear()
                synthesisQueue.clear()

                _currentJob.value = null
                _allJobs.value = emptyList()
                _isProcessing.value = false
                _phaseSummary.value = null

                // Standby immediato: scarica i modelli
                sttEngine.unloadEngine()
                llmEngine.unloadEngine()
            }
        }
    }

    private fun ensureWorkerRunning() {
        if (workerJob?.isActive == true) return
        _isProcessing.value = true
        workerJob = scope.launch {
            processQueues()
        }
    }

    private suspend fun processQueues() {
        while (true) {
            var nextJob: ProcessingJob? = null

            mutex.withLock {
                if (transcriptionQueue.isNotEmpty()) {
                    nextJob = transcriptionQueue.removeFirst()
                    _phaseSummary.value = "Fase 1: Trascrizione Whisper (${transcriptionQueue.size + 1} rimaste)"
                } else if (synthesisQueue.isNotEmpty()) {
                    // Quando tutte le trascrizioni sono finite, scarichiamo Whisper prima di avviare l'LLM!
                    sttEngine.unloadEngine()
                    nextJob = synthesisQueue.removeFirst()
                    _phaseSummary.value = "Fase 2: Elaborazione AI on-device (${synthesisQueue.size + 1} rimaste)"
                } else {
                    _currentJob.value = null
                    _isProcessing.value = false
                    _phaseSummary.value = null
                    updateAllJobsList()
                    startStandbyTimer()
                    return
                }

                nextJob?.let { job ->
                    _currentJob.value = job.copy(status = JobStatus.RUNNING)
                    updateAllJobsList()
                }
            }

            val job = nextJob ?: break

            // Esecuzione protetta con coroutine cancellabile
            val executionJob = scope.launch {
                try {
                    when (job.type) {
                        JobType.TRANSCRIPTION -> {
                            Log.d(TAG, "Executing STT job: ${job.title}")
                            val text = sttEngine.transcribeNote(job.noteId)
                            if (text != null) {
                                // Appena trascritta, accoda automaticamente per Fase 2 (LLM)
                                mutex.withLock {
                                    synthesisQueue.add(
                                        ProcessingJob(
                                            noteId = job.noteId,
                                            noteNum = job.noteNum,
                                            noteTag = job.noteTag,
                                            type = JobType.SYNTHESIS,
                                            title = "Elaborazione AI nota #${job.noteNum}"
                                        )
                                    )
                                    updateAllJobsList()
                                }
                            }
                        }
                        JobType.SYNTHESIS -> {
                            Log.d(TAG, "Executing LLM job: ${job.title}")
                            llmEngine.elaborateNote(job.noteId)
                        }
                    }
                } catch (ce: CancellationException) {
                    Log.d(TAG, "Job ${job.title} was cancelled.")
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Error executing job ${job.title}: ${t.message}", t)
                }
            }

            activeExecutionJob = executionJob
            executionJob.join()
            activeExecutionJob = null

            mutex.withLock {
                _currentJob.value = null
                updateAllJobsList()
            }
        }
    }

    private fun updateAllJobsList() {
        val list = mutableListOf<ProcessingJob>()
        _currentJob.value?.let { list.add(it) }
        list.addAll(transcriptionQueue)
        list.addAll(synthesisQueue)
        _allJobs.value = list
    }

    private fun cancelStandbyTimer() {
        standbyJob?.cancel()
        standbyJob = null
    }

    /**
     * Avvia il timer di standby di 30 secondi.
     * Se entro 30s non arrivano nuovi job, scarica i pesi di LiteRT-LM e Whisper dalla RAM.
     */
    private fun startStandbyTimer() {
        cancelStandbyTimer()
        standbyJob = scope.launch {
            Log.d(TAG, "Coda vuota: avvio timer di standby di 30 secondi...")
            delay(30_000)
            mutex.withLock {
                if (transcriptionQueue.isEmpty() && synthesisQueue.isEmpty() && _currentJob.value == null) {
                    Log.d(TAG, "Standby 30s completato: scaricamento di tutti i modelli dalla memoria RAM.")
                    sttEngine.unloadEngine()
                    llmEngine.unloadEngine()
                }
            }
        }
    }
}
