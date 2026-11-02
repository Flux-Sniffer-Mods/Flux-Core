package io.github.fluxsniffermods.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.FluxCore
import io.github.fluxsniffermods.core.FluxLog
import io.github.fluxsniffermods.core.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * An update installed by the app itself: the APK attached to its own repository's release is
 * downloaded to the cache, checked to be a newer build of this same app, and handed to
 * Android's installer. Android still asks first, and refuses an APK signed with another key.
 */
object UpdateInstaller {
    private const val TAG = "FluxUpdates"
    private const val DIR = "flux-updates"
    private const val FILE = "update.apk"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var downloading = false

    /** Only APKs attached to this app's own releases are downloaded. */
    internal fun trusted(context: Context, url: String): Boolean {
        val app = FluxApp.self(context) ?: return false
        return url.startsWith("https://github.com/${app.repository}/releases/download/", ignoreCase = true) &&
            url.endsWith(".apk", ignoreCase = true)
    }

    internal fun newerBuildOfThisApp(archivePackage: String?, archiveVersion: String?, ownPackage: String, ownVersion: String): Boolean =
        archivePackage == ownPackage && archiveVersion != null &&
            (FluxUpdates.compareVersions(archiveVersion, ownVersion) ?: -1) > 0

    fun downloadAndInstall(context: Context, apkUrl: String, releasePage: String?) {
        val app = context.applicationContext
        val fallback = releasePage ?: FluxUpdates.releasesPage(app) ?: return
        if (!trusted(app, apkUrl)) {
            FluxUpdates.open(app, fallback)
            return
        }
        if (downloading) return
        downloading = true
        Toast.makeText(app, R.string.flux_update_downloading, Toast.LENGTH_SHORT).show()
        Thread {
            val apk = runCatching { download(app, apkUrl) }
                .onFailure { FluxLog.w(TAG, "update download failed: $it") }
                .getOrNull()
            downloading = false
            main.post {
                when {
                    apk == null -> {
                        Toast.makeText(app, R.string.flux_update_download_failed, Toast.LENGTH_LONG).show()
                        FluxUpdates.open(app, fallback)
                    }
                    app.packageManager.canRequestPackageInstalls() -> install(app, apk)
                    else -> askToAllowInstalls(app)
                }
            }
        }.start()
    }

    private fun download(context: Context, url: String): File? {
        val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
        val target = File(dir, FILE)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(target.path, 0)
        if (!newerBuildOfThisApp(info?.packageName, info?.versionName, context.packageName, FluxCore.versionName)) {
            FluxLog.w(TAG, "the download isn't a newer build of this app: ${info?.packageName} ${info?.versionName}")
            target.delete()
            return null
        }
        return target
    }

    private fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.flux.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            FluxLog.w(TAG, "no installer for the update: $it")
            Toast.makeText(context, R.string.flux_update_download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun pending(context: Context) = File(File(context.cacheDir, DIR), FILE)

    /**
     * Android's "Install unknown apps" switch, the first time. Flipping it restarts the app, so
     * the download is kept and [UpdateInstallActivity], which Android brings back, installs it.
     */
    private fun askToAllowInstalls(context: Context) {
        Toast.makeText(context, context.getString(R.string.flux_update_allow_installs, FluxApp.self(context)?.title ?: ""), Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(Intent(context, UpdateInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { runCatching { context.startActivity(allowInstallsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    internal fun allowInstallsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Installs a kept download once installs are allowed. Whether it did. */
    internal fun installPending(context: Context): Boolean {
        val apk = pending(context)
        if (!apk.isFile || !context.packageManager.canRequestPackageInstalls()) return false
        install(context.applicationContext, apk)
        return true
    }

    /** A download is only kept until it's installed (or while installs are being allowed). */
    fun clearDownloads(context: Context) {
        if (downloading) return
        val apk = pending(context)
        @Suppress("DEPRECATION")
        val info = if (apk.isFile) context.packageManager.getPackageArchiveInfo(apk.path, 0) else null
        if (newerBuildOfThisApp(info?.packageName, info?.versionName, context.packageName, FluxCore.versionName)) return
        File(context.cacheDir, DIR).deleteRecursively()
    }
}

/** The downloaded update, handed to Android's installer. */
class UpdateFiles : FileProvider()
