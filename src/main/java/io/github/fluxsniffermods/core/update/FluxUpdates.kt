package io.github.fluxsniffermods.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.FluxCore
import io.github.fluxsniffermods.core.FluxLog
import io.github.fluxsniffermods.core.FluxPrefs
import io.github.fluxsniffermods.core.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Each Flux app updates itself from its own repository's "flux/v…" releases: full releases, and
 * dev builds too while "Dev builds" is on (on to begin with in a dev build). Looked at every
 * couple of hours in the background (GitHub answers "not modified" while nothing changed) and
 * at once from the app's page; a newer build is a notification that downloads and installs it.
 */
object FluxUpdates {
    private const val TAG = "FluxUpdates"
    private const val WORK = "flux_update_check"
    private const val CHANNEL = "flux_updates"
    private const val NOTIFICATION_ID = 7310
    private const val KEY_DEV = "updates_dev_builds"
    private const val KEY_HOURS = "updates_every_hours"
    private const val KEY_ETAG = "updates_tags_etag"
    private const val KEY_BODY = "updates_tags_body"
    private const val KEY_ANNOUNCED = "updates_announced"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class Release(val tag: String, val version: String, val title: String, val page: String, val apk: String)

    fun devBuilds(context: Context): Boolean =
        FluxPrefs.get(context).getBoolean(KEY_DEV, isDevBuild(FluxCore.versionName))

    fun setDevBuilds(context: Context, on: Boolean) = FluxPrefs.get(context).edit().putBoolean(KEY_DEV, on).apply()

    /** How often the background check runs: 1, 2, 6 or 24 hours. */
    fun everyHours(context: Context): Int = FluxPrefs.get(context).getInt(KEY_HOURS, 2).takeIf { it in HOURS } ?: 2

    fun setEveryHours(context: Context, hours: Int) {
        FluxPrefs.get(context).edit().putInt(KEY_HOURS, hours).apply()
        schedule(context)
    }

    val HOURS = listOf(1, 2, 6, 24)

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!FluxCore.checksUpdates || FluxApp.self(app) == null) {
            runCatching { WorkManager.getInstance(app).cancelUniqueWork(WORK) }
            return
        }
        val hours = everyHours(app).toLong()
        val request = PeriodicWorkRequestBuilder<Check>(hours, TimeUnit.HOURS, hours * 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * The newest release newer than this build (dev builds while they're wanted), or null; a
     * failure to ask ([Outcome.failed]). Blocking.
     */
    fun check(context: Context): Outcome {
        val app = FluxApp.self(context) ?: return Outcome(null, failed = true)
        val prefs = FluxPrefs.get(context)
        val request = Request.Builder()
            .url("https://api.github.com/repos/${app.repository}/git/matching-refs/tags/flux/v")
            .header("Accept", "application/vnd.github+json")
            .apply { prefs.getString(KEY_ETAG, null)?.let { header("If-None-Match", it) } }
            .build()
        val body = runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> prefs.getString(KEY_BODY, null)
                    response.isSuccessful -> response.body?.string()?.also { text ->
                        response.header("ETag")?.let { prefs.edit().putString(KEY_ETAG, it).putString(KEY_BODY, text).apply() }
                    }
                    else -> null
                }
            }
        }.onFailure { FluxLog.w(TAG, "couldn't check: $it") }.getOrNull() ?: return Outcome(null, failed = true)
        val refs = runCatching {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("ref") }
        }.getOrElse { return Outcome(null, failed = true) }
        return Outcome(newest(app, refs, FluxCore.versionName, devBuilds(context)), failed = false)
    }

    class Outcome(val release: Release?, val failed: Boolean)

    /** The newest of [refs] ("refs/tags/flux/v1.0") newer than [current]. */
    internal fun newest(app: FluxApp, refs: List<String>, current: String, dev: Boolean): Release? =
        refs.map { it.removePrefix("refs/tags/") }
            .filter { it.startsWith("flux/v") && (dev || !isDevBuild(it)) }
            .filter { (compareVersions(it.removePrefix("flux/v"), current) ?: -1) > 0 }
            .maxWithOrNull { a, b -> compareVersions(a.removePrefix("flux/v"), b.removePrefix("flux/v")) ?: 0 }
            ?.let { tag ->
                val version = tag.removePrefix("flux/v")
                val base = "https://github.com/${app.repository}/releases"
                Release(tag, version, "${app.title} " + friendly(version), "$base/tag/$tag", "$base/download/$tag/${app.apkName}-$version.apk")
            }

    /** A dev build's version (1.0-flux.<yyyyMMddHHmm>), as opposed to a full release's (1.0). */
    fun isDevBuild(version: String) = version.contains("-flux.")

    /** "1.1 dev · 2 Nov 2026, 13:45" for a dev build, the version itself for a full release. */
    fun friendly(version: String): String {
        if (!isDevBuild(version)) return version
        val stamp = version.substringAfterLast('.')
        val time = runCatching {
            val parsed = java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(stamp)!!
            java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(parsed)
        }.getOrDefault(stamp)
        return version.substringBefore('-') + " dev · " + time
    }

    /** Numeric versions and their prereleases compared; null for a version it can't read. */
    fun compareVersions(first: String, second: String): Int? {
        val pattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
        val a = pattern.matchEntire(first.trim().removePrefix("v")) ?: return null
        val b = pattern.matchEntire(second.trim().removePrefix("v")) ?: return null
        val av = a.groupValues[1].split('.').map(String::toBigInteger)
        val bv = b.groupValues[1].split('.').map(String::toBigInteger)
        for (i in 0 until maxOf(av.size, bv.size)) {
            val order = (av.getOrNull(i) ?: java.math.BigInteger.ZERO).compareTo(bv.getOrNull(i) ?: java.math.BigInteger.ZERO)
            if (order != 0) return order
        }
        val ap = a.groupValues[2]
        val bp = b.groupValues[2]
        if (ap == bp) return 0
        if (ap.isEmpty()) return 1
        if (bp.isEmpty()) return -1
        val ai = ap.split('.')
        val bi = bp.split('.')
        for (i in 0 until minOf(ai.size, bi.size)) {
            val an = ai[i].toBigIntegerOrNull()
            val bn = bi[i].toBigIntegerOrNull()
            val order = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> ai[i].compareTo(bi[i])
            }
            if (order != 0) return order
        }
        return ai.size.compareTo(bi.size)
    }

    /** "Check for updates" on the app's page: says what it found, and offers a newer build. */
    fun checkNow(context: Context, onDone: (Release?) -> Unit = {}) {
        val app = context.applicationContext
        android.widget.Toast.makeText(app, app.getString(R.string.flux_update_checking, title(app)), android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val result = check(app)
            Handler(Looper.getMainLooper()).post {
                val release = result.release
                when {
                    release != null -> UpdateInstaller.downloadAndInstall(context, release.apk, release.page)
                    result.failed -> android.widget.Toast.makeText(app, R.string.flux_update_failed, android.widget.Toast.LENGTH_LONG).show()
                    else -> android.widget.Toast.makeText(app, app.getString(R.string.flux_update_current, title(app)), android.widget.Toast.LENGTH_SHORT).show()
                }
                onDone(release)
            }
        }.start()
    }

    private fun title(context: Context) = FluxApp.self(context)?.title ?: "Flux"

    /** A newer build, as a notification that installs it (once per build). */
    internal fun announce(context: Context, release: Release) {
        val prefs = FluxPrefs.get(context)
        if (prefs.getString(KEY_ANNOUNCED, null) == release.tag) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.flux_updates_section), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val install = PendingIntent.getActivity(
            context, 0, UpdateInstallActivity.downloadIntent(context, release.apk, release.page),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.flux_notification)
            .setContentTitle(context.getString(R.string.flux_update_title, title(context)))
            .setContentText(context.getString(R.string.flux_update_message, release.title))
            .setContentIntent(install)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        prefs.edit().putString(KEY_ANNOUNCED, release.tag).apply()
    }

    /** The release page of this app, to read about a build. */
    fun releasesPage(context: Context) = FluxApp.self(context)?.releasesPage

    internal fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The background check. */
    class Check(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            if (!FluxCore.checksUpdates) return Result.success()
            val outcome = FluxUpdates.check(applicationContext)
            if (outcome.failed) return Result.retry()
            outcome.release?.let { FluxUpdates.announce(applicationContext, it) }
            return Result.success()
        }
    }
}
