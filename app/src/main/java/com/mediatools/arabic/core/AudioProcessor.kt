package com.mediatools.arabic.core

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * يعيد ترميز الصوت فعليًا إلى AAC داخل حاوية MP4 (M4A) بمعدل بت محدد،
 * ويتجاهل مسار الفيديو إن وُجد. لا يعدّل الملف الأصلي أبدًا.
 * يعمل محليًا بالكامل عبر Media3 Transformer (Apache 2.0، مكتبة رسمية من Google).
 */
@androidx.annotation.OptIn(UnstableApi::class)
object AudioProcessor {

    suspend fun transcodeToM4a(
        context: Context,
        input: Uri,
        bitrateBps: Int,
        output: File,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(bitrateBps).build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
                .setRemoveVideo(true)
                .build()

            output.delete()
            transformer.start(item, output.absolutePath)
            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { transformer.cancel() }
            }
        }
    }
}
