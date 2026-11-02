package io.github.fluxsniffermods.core.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.fluxsniffermods.core.FluxLog
import io.github.fluxsniffermods.core.R
import io.github.fluxsniffermods.core.shell.AdbShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's last times not responding, with where each thread was (its main thread first). */
private fun anrTraces(context: Context): java.io.File? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(android.app.ActivityManager::class.java) ?: return null
    val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        .filter { it.reason == android.app.ApplicationExitInfo.REASON_ANR }.take(2)
    if (exits.isEmpty()) return null
    val dir = context.getExternalFilesDir(null) ?: return null
    val file = java.io.File(dir, "anr-traces.txt")
    file.bufferedWriter().use { out ->
        for (exit in exits) {
            out.write("--- ${java.util.Date(exit.timestamp)}: ${exit.description}\n")
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val main = Regex("(?s)\"main\".*?\n\n").find(trace)?.value.orEmpty()
            out.write(main)
            out.write(trace.take(60_000))
            out.write("\n")
        }
    }
    file
}.getOrNull()

/** Where the crash log is saved: Download/<the app's name>/crash-log.txt. */
fun crashLogPath(context: Context) = "Download/${appName(context)}/crash-log.txt"

/**
 * Android's record of the app's last crashes, saved through the shell to share, with the app's
 * own log; [sections] adds the app's own parts (shell commands that print them), [tags] the
 * log tags of its own worth keeping. And a fresh start: the logs emptied.
 */
@Composable
fun CrashLogRows(sections: (Context) -> String = { "" }, tags: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val path = crashLogPath(context)
    FluxActionRow(
        linkId = null,
        icon = "⚠",
        title = stringResource(R.string.crash_log),
        description = stringResource(R.string.crash_log_description, appName(), path)
    ) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val file = "/storage/emulated/0/$path"
                val app = context.packageName
                val traces = anrTraces(context)
                val own = runCatching { sections(context) }.getOrDefault("")
                AdbShell.run(
                    "f='$file'; mkdir -p \"\${f%/*}\"; { " +
                        "echo '== why it stopped =='; dumpsys activity exit-info $app | head -n 120; " +
                        "echo '== crashes =='; logcat -d -b crash -t 400; " +
                        "echo '== warnings and errors =='; logcat -d -b main,system '*:W' 'ActivityTaskManager:I' | " +
                        "grep -i -E '$app|Flux|Permission Denial|AndroidRuntime|lowmemorykiller|has died|Killing${if (tags.isNotEmpty()) "|$tags" else ""}' | " +
                        "grep -v 'Changing focus' | tail -n 400; " +
                        "echo '== screen =='; wm size | cat; wm density | cat; " +
                        "echo '== accessibility services =='; settings get secure enabled_accessibility_services | tr ':' '\\n'; " +
                        own +
                        "echo '== the app\\'s own log =='; tail -c 120000 '${context.getExternalFilesDir(null)?.path}/${FluxLog.FILE_NAME}'; " +
                        (if (traces != null) "echo '== not responding: threads =='; cat '${traces.path}'; " else "") +
                        // Through a pipe: Android's services can't write into a file in Download
                        "} 2>&1 | cat > \"\$f\"; echo ok",
                    15_000
                )?.contains("ok") == true
            }
            Toast.makeText(
                context,
                if (saved) context.getString(R.string.crash_log_saved, path) else context.getString(R.string.adb_not_running),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    FluxActionRow(
        linkId = null,
        icon = "✕",
        title = stringResource(R.string.crash_log_clear),
        description = stringResource(R.string.crash_log_clear_description, appName())
    ) {
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                runCatching { java.io.File(context.getExternalFilesDir(null), FluxLog.FILE_NAME).delete() }
                AdbShell.run("logcat -c -b main -b system -b crash; echo ok", 10_000)?.contains("ok") == true
            }
            Toast.makeText(context, if (cleared) R.string.crash_log_cleared else R.string.adb_not_running, Toast.LENGTH_SHORT).show()
        }
    }
}
