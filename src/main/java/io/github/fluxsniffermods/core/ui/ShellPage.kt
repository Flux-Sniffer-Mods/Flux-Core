package io.github.fluxsniffermods.core.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.R
import io.github.fluxsniffermods.core.shell.RootShell
import io.github.fluxsniffermods.core.shell.ShellSetup
import io.github.fluxsniffermods.core.shell.ShellStatus
import io.github.fluxsniffermods.core.shell.ShizukuBoot
import io.github.fluxsniffermods.core.shell.ShizukuPermission
import io.github.fluxsniffermods.core.shell.rememberShellState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's name, for the strings that say it. */
@Composable
fun appName(): String {
    val context = LocalContext.current
    return remember { appName(context) }
}

fun appName(context: Context): String = FluxApp.self(context)?.title
    ?: runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault("Flux")

/** The shell's mark on its rows: a prompt. */
const val SHELL_ICON = "❯"

/**
 * Shell access: the built-in shell (paired once through wireless debugging, or another Flux
 * app's, lent), root, or Shizuku. Whatever the app runs as the ADB shell goes through it.
 */
@Composable
fun ShellScreen(modifier: Modifier = Modifier, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val name = appName()
    val shell = rememberShellState()
    var status by shell.status
    val running by shell.running
    val paired by shell.paired
    val lender by shell.lender
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> shell.refresh() }
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) } }
    }
    FluxScreenScaffold(stringResource(R.string.root_title), onBack, modifier) {
        if (ShellSetup.supported()) {
            FluxActionRow(
                linkId = null,
                icon = SHELL_ICON,
                title = stringResource(R.string.shell_title),
                description = when {
                    lender != null -> stringResource(R.string.shell_borrowed, lender!!)
                    running -> stringResource(R.string.shell_running)
                    paired -> stringResource(R.string.shell_paired)
                    else -> stringResource(R.string.shell_setup, name)
                }
            ) {
                when {
                    running -> Unit
                    paired -> {
                        Toast.makeText(context, R.string.shell_starting, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ShellSetup.start(context) }
                            if (!ok) Toast.makeText(
                                context,
                                context.getString(R.string.shell_start_now_failed) + "\n" + ShellSetup.lastError.orEmpty(),
                                Toast.LENGTH_LONG
                            ).show()
                            shell.refresh()
                        }
                    }
                    !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                        Toast.makeText(context, context.getString(R.string.shell_notifications_off, name), Toast.LENGTH_LONG).show()
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    else -> ShellSetup.beginPairing(context)
                }
            }
            if (paired) FluxActionRow(
                linkId = null,
                title = stringResource(R.string.shell_forget_title),
                description = stringResource(R.string.shell_forget_description)
            ) {
                ShellSetup.forget(context)
                shell.refresh()
            }
        }
        var rootOn by remember { mutableStateOf(RootShell.enabled(context)) }
        FluxSwitchRow(
            linkId = null,
            icon = "#",
            title = stringResource(R.string.root_shell_title),
            description = stringResource(R.string.root_shell_description),
            checked = rootOn
        ) { on ->
            scope.launch {
                val granted = withContext(Dispatchers.IO) {
                    RootShell.setEnabled(context, on)
                    RootShell.active
                }
                rootOn = on
                if (on && !granted) Toast.makeText(context, R.string.root_shell_denied, Toast.LENGTH_LONG).show()
                shell.refresh()
            }
        }
        FluxNote(
            when (status) {
                ShellStatus.Connected -> stringResource(R.string.root_note, name)
                ShellStatus.NotAuthorized -> stringResource(R.string.adb_not_allowed, name)
                ShellStatus.NotConnected -> stringResource(R.string.adb_not_running)
            }
        )
        if (status == ShellStatus.NotAuthorized) FluxActionRow(
            linkId = null,
            title = stringResource(R.string.adb_allow_title, name),
            description = stringResource(R.string.adb_allow_description)
        ) {
            ShizukuPermission.request(context) { shell.refresh() }
        }
        // Shizuku starting itself at boot (no root): not needed once the built-in shell is set up
        if (status == ShellStatus.Connected && !paired && lender == null && remember { ShizukuBoot.installed(context) }) {
            var ready by remember { mutableStateOf(ShizukuBoot.granted(context)) }
            FluxActionRow(
                linkId = null,
                icon = "↻",
                title = stringResource(R.string.shizuku_boot_title),
                description = stringResource(if (ready) R.string.shizuku_boot_ready else R.string.shizuku_boot_description, name)
            ) {
                ShizukuBoot.setUp(context) { ok -> ready = ok }
            }
        }
        extra()
    }
}

/**
 * Shell access where a page needs it: [why] it's needed, whether it's running, and a tap to
 * the shell's page ([open]) to set it up or start it.
 */
@Composable
fun ShellAccessRow(why: String, open: () -> Unit) {
    val shell = rememberShellState()
    val status by shell.status
    val paired by shell.paired
    val state = stringResource(
        when {
            status == ShellStatus.Connected -> R.string.shell_access_on
            paired -> R.string.shell_access_paired
            else -> R.string.shell_access_off
        }
    )
    FluxActionRow(linkId = null, icon = SHELL_ICON, title = stringResource(R.string.root_title), description = "$why $state", onClick = open)
}
