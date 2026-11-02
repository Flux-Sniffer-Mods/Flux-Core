package io.github.fluxsniffermods.core

import android.content.Context
import android.os.Looper
import io.github.fluxsniffermods.core.shell.RootShell
import io.github.fluxsniffermods.core.shell.ShellSetup
import io.github.fluxsniffermods.core.update.FluxUpdates

/**
 * What every Flux app starts the same way: its log, background failures kept apart, the shell
 * (started now, or once Wi-Fi connects), root when switched on, and its update checks.
 */
object FluxCore {
    /** The app's version name, for its updates and crash log. */
    @Volatile var versionName: String = "0"
        private set
    /** Whether the app checks its GitHub releases for updates. */
    @Volatile var checksUpdates = true
        private set

    /** What the app's About page adds: its credits, and its own parts of the crash log. */
    class About(
        val credits: Int? = null,
        val crashSections: (Context) -> String = { "" },
        val crashTags: String = ""
    )

    @Volatile var about = About()
        private set

    fun init(context: Context, versionName: String, checksUpdates: Boolean = true, about: About = About()) {
        val app = context.applicationContext
        this.versionName = versionName
        this.checksUpdates = checksUpdates
        this.about = about
        FluxLog.init(app)
        keepBackgroundFailuresApart()
        ShellSetup.ensureStarted(app)
        // Root, when switched on: asked off the main thread, as su may wait for the user
        Thread { RootShell.init(app) }.start()
        if (checksUpdates) runCatching { FluxUpdates.schedule(app) }
    }

    /**
     * A failure on a background thread (a stream, a download) is logged, here and in the crash
     * log, and ends that thread alone. The main thread's failures, and running out of memory,
     * end the app as before.
     */
    private fun keepBackgroundFailuresApart() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread === Looper.getMainLooper().thread || error is OutOfMemoryError || previous == null) {
                previous?.uncaughtException(thread, error)
            } else {
                runCatching { FluxLog.w("FluxCrash", "thread ${thread.name}: ${android.util.Log.getStackTraceString(error)}") }
            }
        }
    }
}
