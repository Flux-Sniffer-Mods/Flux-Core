package io.github.fluxsniffermods.core.shell

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import rikka.shizuku.Shizuku

/** Whether shell commands can run: through root, a shell helper or Shizuku. */
enum class ShellStatus {
    /** Commands run. */
    Connected,
    /** Shizuku is running but hasn't allowed the app. */
    NotAuthorized,
    /** Nothing to run them through. */
    NotConnected;

    companion object {
        fun now(): ShellStatus = runCatching {
            when {
                RootShell.active || BuiltInShell.running() -> Connected
                !Shizuku.pingBinder() -> NotConnected
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Connected
                else -> NotAuthorized
            }
        }.getOrDefault(NotConnected)
    }
}

/** The shell's state on a page, read again whenever the page comes back. */
class ShellState(context: Context) {
    private val app = context.applicationContext
    val status = mutableStateOf(ShellStatus.NotConnected)
    val running = mutableStateOf(false)
    val paired = mutableStateOf(ShellSetup.paired(app))
    val lender = mutableStateOf<String?>(null)

    /** Off the main thread: the shell's answer can take a moment. */
    fun refresh() {
        Thread {
            val now = ShellStatus.now()
            val up = BuiltInShell.running()
            val through = BuiltInShell.lender?.takeIf { BuiltInShell.borrowed() }?.title
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                status.value = now
                running.value = up
                paired.value = ShellSetup.paired(app)
                lender.value = through
            }
        }.start()
    }
}

@Composable
fun rememberShellState(): ShellState {
    val context = LocalContext.current
    val state = remember { ShellState(context) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}
