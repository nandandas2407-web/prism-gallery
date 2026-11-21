package com.prism.gallery.data.sync

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.prism.gallery.data.media.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Provider abstraction: WebDAV is implemented below; Google Drive (Drive API v3, drive.file scope) and S3 (SigV4) plug in here. */
interface CloudProvider {
    suspend fun exists(name: String): Boolean
    suspend fun upload(name: String, mime: String, open: () -> java.io.InputStream?): Boolean
}

class WebDavProvider(private val base: String, user: String, pass: String) : CloudProvider {
    private val auth = "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
    private fun conn(name: String, method: String) = (URL(base.trimEnd('/') + "/" + java.net.URLEncoder.encode(name, "UTF-8")).openConnection() as HttpURLConnection).apply {
        requestMethod = method; setRequestProperty("Authorization", auth); connectTimeout = 15_000; readTimeout = 60_000
    }
    override suspend fun exists(name: String) = runCatching { conn(name, "HEAD").responseCode == 200 }.getOrDefault(false)
    override suspend fun upload(name: String, mime: String, open: () -> java.io.InputStream?): Boolean = runCatching {
        // Conflict policy: never overwrite remote — keep both by suffixing the local timestamp.
        val target = if (exists(name)) "${name.substringBeforeLast('.')}-${System.currentTimeMillis()}.${name.substringAfterLast('.', "bin")}" else name
        conn(target, "PUT").run {
            doOutput = true; setRequestProperty("Content-Type", mime); setChunkedStreamingMode(64 * 1024)
            outputStream.use { o -> open()?.use { it.copyTo(o) } }
            responseCode in 200..299
        }
    }.getOrDefault(false)
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // TODO: read endpoint/credentials from encrypted settings; optional E2EE = encrypt each stream with a passphrase-derived AES-GCM key before upload.
        val url = inputData.getString("url") ?: return Result.success()
        val provider = WebDavProvider(url, inputData.getString("user") ?: "", inputData.getString("pass") ?: "")
        media.refresh()
        var ok = true
        media.items.value.take(200).forEach { m ->
            ok = provider.upload(m.name, m.mime) { ctx.contentResolver.openInputStream(m.uri) } && ok
        }
        return if (ok) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private const val NAME = "prism-sync"
    fun schedule(ctx: Context, wifiOnly: Boolean, charging: Boolean) {
        val c = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(charging).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(c).build())
    }
    fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME) }
}
