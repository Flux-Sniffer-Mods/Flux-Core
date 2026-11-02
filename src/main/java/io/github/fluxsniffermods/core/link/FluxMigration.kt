package io.github.fluxsniffermods.core.link

import android.content.Context
import android.os.Bundle
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.FluxLog
import io.github.fluxsniffermods.core.FluxPrefs

/**
 * Once, as an app that was a module of Flux Titan Hub first starts: the settings the hub kept for
 * it (still in Flux Keyboard, the hub's successor) taken over, so its games, pairings and choices
 * come along; what's been set in the app already stays. Asked again at each start until Flux
 * Keyboard answers (it may be updated later); nothing when it isn't installed.
 */
object FluxMigration {
    private const val TAG = "FluxMigration"
    private const val KEY_DONE = "migration_from_hub_done"

    /** Blocking: off the main thread. Whether anything came over. */
    fun importOnce(context: Context, prefixes: List<String>): Boolean {
        val prefs = FluxPrefs.get(context)
        if (prefs.getBoolean(KEY_DONE, false)) return false
        if (!FluxApp.KEYBOARD.installed(context)) {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return false
        }
        val answer = FluxLink.call(context, FluxApp.KEYBOARD, LinkMethods.LEGACY_PREFS, null,
            Bundle().apply { putStringArray("prefixes", prefixes.toTypedArray()) }) ?: return false
        val edit = prefs.edit()
        var count = 0
        for (key in answer.keySet()) {
            // What's been set here already stays (set up before Flux Keyboard was updated)
            if (prefixes.none { key.startsWith(it) } || prefs.contains(key)) continue
            @Suppress("DEPRECATION")
            when (val value = answer.get(key)) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Array<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> continue
            }
            count++
        }
        edit.putBoolean(KEY_DONE, true).apply()
        FluxLog.w(TAG, "$count settings taken over from Flux Titan Hub")
        return count > 0
    }

    /** Flux Keyboard's side: [all] its settings, those named after one of [prefixes] as an answer. */
    fun answer(all: Map<String, *>, prefixes: List<String>): Bundle = Bundle().apply {
        for ((key, value) in all) {
            if (prefixes.none { key.startsWith(it) }) continue
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Set<*> -> putStringArray(key, value.filterIsInstance<String>().toTypedArray())
            }
        }
    }
}
