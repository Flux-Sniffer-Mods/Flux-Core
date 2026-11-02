package io.github.fluxsniffermods.core.link

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.FluxLog

/**
 * How the Flux apps talk to each other: each has a provider ([FluxLinkProvider]) only the others
 * can reach (they're signed with the same key, and the permission is a signature one). A call
 * asks something of another app, or tells it something; a topic changed is a note any of them
 * can watch for.
 */
object FluxLink {
    private const val TAG = "FluxLink"
    const val PERMISSION = "io.github.fluxsniffermods.permission.LINK"

    fun authority(app: FluxApp) = "${app.packageName}.link"

    fun uri(app: FluxApp, path: String = ""): Uri = Uri.parse("content://${authority(app)}/$path")

    /**
     * [method] of [app]'s provider, or null when it isn't installed or didn't answer. It may
     * start [app]: off the main thread where it can be.
     */
    fun call(context: Context, app: FluxApp, method: String, arg: String? = null, extras: Bundle? = null): Bundle? {
        if (app.packageName == context.packageName) return null
        return runCatching { context.contentResolver.call(uri(app), method, arg, extras) }
            .onFailure { if (it !is IllegalArgumentException) FluxLog.w(TAG, "${app.name} $method: $it") }
            .getOrNull()
    }

    /** [call] for a yes or no ("ok" in its answer). */
    fun ask(context: Context, app: FluxApp, method: String, arg: String? = null, extras: Bundle? = null): Boolean =
        call(context, app, method, arg, extras)?.getBoolean(KEY_OK) == true

    /** Tells the apps watching [topic] of this app that it changed. */
    fun changed(context: Context, topic: String) {
        val self = FluxApp.self(context) ?: return
        runCatching { context.contentResolver.notifyChange(uri(self, topic), null) }
    }

    /**
     * [onChange] (on the main thread) each time [app] says [topic] changed; the observer, to
     * [unwatch] with. Null when [app] isn't installed.
     */
    fun watch(context: Context, app: FluxApp, topic: String, onChange: () -> Unit): ContentObserver? {
        if (!app.installed(context)) return null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        return runCatching {
            context.contentResolver.registerContentObserver(uri(app, topic), false, observer)
            observer
        }.onFailure { FluxLog.w(TAG, "can't watch ${app.name} $topic: $it") }.getOrNull()
    }

    fun unwatch(context: Context, observer: ContentObserver?) {
        if (observer != null) runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    /** A yes answer. */
    fun ok(on: Boolean = true) = Bundle().apply { putBoolean(KEY_OK, on) }

    const val KEY_OK = "ok"
}
