package com.mediatools.arabic

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediatools.arabic.core.AudioProcessor
import com.mediatools.arabic.core.BitrateCalculator
import com.mediatools.arabic.core.MediaInfoReader
import com.mediatools.arabic.core.SourceInfo
import com.mediatools.arabic.ui.formatSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface JobState {
    data object Idle : JobState
    data class Running(val message: String) : JobState
    data class Done(val message: String) : JobState
    data class Failed(val message: String) : JobState
}

class ToolsViewModel(private val app: Application) : AndroidViewModel(app) {

    // ---- ضغط الصوت ----
    var compressSource by mutableStateOf<SourceInfo?>(null); private set
    var compressTargetMb by mutableStateOf("15")
    var compressState by mutableStateOf<JobState>(JobState.Idle); private set

    // ---- فيديو إلى صوت ----
    var convertSource by mutableStateOf<SourceInfo?>(null); private set
    var convertKbps by mutableIntStateOf(128)
    var convertState by mutableStateOf<JobState>(JobState.Idle); private set

    private var job: Job? = null

    init {
        // ملفات مؤقتة متبقية من تعطل سابق: لا توجد مهمة نشطة عند بدء التطبيق، فحذفها آمن.
        app.cacheDir.listFiles { f -> f.name.startsWith("work_") }?.forEach { it.delete() }
    }

    fun pickCompressSource(uri: Uri) {
        viewModelScope.launch {
            compressSource = withContext(Dispatchers.IO) { MediaInfoReader.read(app, uri) }
            compressState = JobState.Idle
        }
    }

    fun pickConvertSource(uri: Uri) {
        viewModelScope.launch {
            convertSource = withContext(Dispatchers.IO) { MediaInfoReader.read(app, uri) }
            convertState = JobState.Idle
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun startCompress(dest: Uri) {
        val src = compressSource ?: return
        val targetMb = compressTargetMb.toDoubleOrNull()
        if (targetMb == null || targetMb <= 0.0) {
            compressState = JobState.Failed(app.getString(R.string.fail_invalid_size)); return
        }
        if (src.durationMs <= 0) {
            compressState = JobState.Failed(app.getString(R.string.fail_no_duration)); return
        }
        val targetBytes = (targetMb * BitrateCalculator.BYTES_PER_MB).toLong()
        val targetLabel = formatSize(targetBytes)

        job = viewModelScope.launch {
            val tmp = File(app.cacheDir, "work_${System.nanoTime()}.m4a")
            var success = false
            try {
                if (src.sizeBytes in 1..targetBytes) {
                    compressState = JobState.Done(app.getString(R.string.done_already_small))
                    return@launch
                }
                var kbps = BitrateCalculator.suggestedKbps(targetBytes, src.durationMs)
                if (!BitrateCalculator.isFeasible(kbps)) {
                    compressState = JobState.Failed(
                        app.getString(R.string.fail_impossible, BitrateCalculator.MIN_KBPS)
                    )
                    return@launch
                }
                kbps = kbps.coerceAtMost(BitrateCalculator.MAX_KBPS)

                var attempt = 1
                var finalSize = -1L
                while (attempt <= BitrateCalculator.MAX_ATTEMPTS) {
                    compressState = JobState.Running(
                        app.getString(R.string.running_attempt, attempt, BitrateCalculator.MAX_ATTEMPTS, kbps)
                    )
                    AudioProcessor.transcodeToM4a(app, src.uri, kbps * 1000, tmp)
                    val actual = tmp.length()
                    if (actual in 1..targetBytes) {
                        finalSize = actual; break
                    }
                    if (actual <= 0) throw IllegalStateException("empty output")
                    kbps = BitrateCalculator.nextAttemptKbps(kbps, targetBytes, actual)
                    if (!BitrateCalculator.isFeasible(kbps)) {
                        compressState = JobState.Failed(
                            app.getString(R.string.fail_impossible, BitrateCalculator.MIN_KBPS)
                        )
                        return@launch
                    }
                    attempt++
                }
                if (finalSize < 0) {
                    compressState = JobState.Failed(
                        app.getString(R.string.fail_over_target, BitrateCalculator.MAX_ATTEMPTS)
                    )
                    return@launch
                }
                withContext(Dispatchers.IO) { copyTo(tmp, dest) }
                success = true
                compressState = JobState.Done(
                    app.getString(R.string.done_compress, formatSize(finalSize), targetLabel)
                )
            } catch (e: CancellationException) {
                compressState = JobState.Idle
                throw e
            } catch (e: Exception) {
                compressState = JobState.Failed(
                    app.getString(R.string.fail_generic, e.message ?: e.javaClass.simpleName)
                )
            } finally {
                tmp.delete()
                if (!success) deleteDest(dest)
            }
        }
    }

    fun startConvert(dest: Uri) {
        val src = convertSource ?: return
        val kbps = convertKbps
        job = viewModelScope.launch {
            val tmp = File(app.cacheDir, "work_${System.nanoTime()}.m4a")
            var success = false
            try {
                convertState = JobState.Running(app.getString(R.string.running_convert, kbps))
                AudioProcessor.transcodeToM4a(app, src.uri, kbps * 1000, tmp)
                val size = tmp.length()
                if (size <= 0) throw IllegalStateException("empty output")
                withContext(Dispatchers.IO) { copyTo(tmp, dest) }
                success = true
                convertState = JobState.Done(app.getString(R.string.done_convert, formatSize(size)))
            } catch (e: CancellationException) {
                convertState = JobState.Idle
                throw e
            } catch (e: Exception) {
                convertState = JobState.Failed(
                    app.getString(R.string.fail_generic, e.message ?: e.javaClass.simpleName)
                )
            } finally {
                tmp.delete()
                if (!success) deleteDest(dest)
            }
        }
    }

    private fun copyTo(src: File, dest: Uri) {
        val out = app.contentResolver.openOutputStream(dest, "w")
            ?: throw IllegalStateException("cannot open destination")
        out.use { o -> src.inputStream().use { it.copyTo(o) } }
    }

    /** حذف ملف الوجهة الفارغ أو الناقص عند فشل العملية. الملف الأصلي لا يُمس. */
    private fun deleteDest(dest: Uri) {
        try {
            DocumentsContract.deleteDocument(app.contentResolver, dest)
        } catch (_: Exception) {
        }
    }
}
