package io.github.fluxsniffermods.core

import android.content.Context
import android.content.SharedPreferences

/** The app's own settings, kept by the parts Core holds (the shell, the extra screen, updates). */
object FluxPrefs {
    /** The settings file: an app with one of its own from before (Flux Keyboard's) names it as it starts. */
    @Volatile var fileName = "flux_prefs"

    fun get(context: Context): SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(fileName, Context.MODE_PRIVATE)
}
