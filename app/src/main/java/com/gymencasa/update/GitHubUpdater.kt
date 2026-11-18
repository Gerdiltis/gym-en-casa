package com.gymencasa.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gymencasa.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

data class AvailableUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Self-update for the sideloaded APK: reads the latest GitHub Release of [BuildConfig.GITHUB_REPO],
 * downloads its .apk and hands it to the system installer.
 */
@Singleton
class GitHubUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val http = OkHttpClient()

    val isConfigured: Boolean get() = BuildConfig.GITHUB_REPO.contains('/')

    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val release = json.decodeFromString<GhRelease>(resp.body.string())
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
            val remote = release.tagName.removePrefix("v")
            if (isNewer(remote, BuildConfig.VERSION_NAME)) {
                AvailableUpdate(remote, release.body.orEmpty(), apk.url, apk.size)
            } else null
        }
    }

    /** Downloads the APK, reporting progress 0..1. */
    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "gym-en-casa-${update.version}.apk")
        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val total = resp.body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            resp.body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
            val r = parts(remote); val l = parts(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
