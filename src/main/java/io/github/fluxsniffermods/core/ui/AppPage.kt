package io.github.fluxsniffermods.core.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.FluxCore
import io.github.fluxsniffermods.core.R
import io.github.fluxsniffermods.core.update.FluxUpdates
import org.json.JSONObject

/**
 * The app's own page under its settings: its version and updates, shell access, the crash log,
 * the other Flux apps, what's new and the credits. [extra] adds the app's own rows to the top;
 * [crashSections] its own parts of the crash log.
 */
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    credits: String? = null,
    crashSections: (Context) -> String = { "" },
    crashTags: String = "",
    shellExtra: @Composable () -> Unit = {},
    startOnShell: Boolean = false,
    extra: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var shellPage by rememberSaveable { mutableStateOf(startOnShell) }
    if (shellPage) {
        // Opened straight on the shell's page: back leaves, as it came
        val leave = { if (startOnShell) onBack() else shellPage = false }
        BackHandler { leave() }
        ShellScreen(modifier, onBack = leave, extra = shellExtra)
        return
    }
    val name = appName()
    FluxScreenScaffold(stringResource(R.string.flux_app_page, name), onBack, modifier) {
        extra()
        SettingsSectionDivider(stringResource(R.string.flux_updates_section))
        if (FluxCore.checksUpdates) {
            FluxActionRow(
                linkId = null,
                icon = "⇣",
                title = stringResource(R.string.flux_update_check),
                description = stringResource(R.string.flux_update_check_description, name, FluxUpdates.friendly(FluxCore.versionName))
            ) { FluxUpdates.checkNow(context) }
            var dev by remember { mutableStateOf(FluxUpdates.devBuilds(context)) }
            FluxSwitchRow(
                linkId = null,
                icon = "✦",
                title = stringResource(R.string.fork_update_channel_title),
                description = stringResource(if (dev) R.string.fork_update_channel_dev else R.string.fork_update_channel_stable),
                checked = dev
            ) { on -> dev = on; FluxUpdates.setDevBuilds(context, on) }
            var hours by remember { mutableIntStateOf(FluxUpdates.everyHours(context)) }
            FluxActionRow(
                linkId = null,
                icon = "↻",
                title = stringResource(R.string.update_check_interval_title),
                description = stringResource(R.string.update_check_interval_description, everyText(context, hours))
            ) {
                hours = FluxUpdates.HOURS[(FluxUpdates.HOURS.indexOf(hours) + 1) % FluxUpdates.HOURS.size]
                FluxUpdates.setEveryHours(context, hours)
            }
        } else {
            FluxNote(stringResource(R.string.flux_version, name, FluxUpdates.friendly(FluxCore.versionName)))
        }
        SettingsSectionDivider(stringResource(R.string.root_title))
        ShellAccessRow(stringResource(R.string.flux_shell_why, name)) { shellPage = true }
        SettingsSectionDivider(stringResource(R.string.flux_problems_section))
        CrashLogRows(crashSections, crashTags)
        SettingsSectionDivider(stringResource(R.string.flux_apps_section))
        FluxNote(stringResource(R.string.flux_apps_note))
        FluxApp.entries.filter { it.packageName != context.packageName }.forEach { app ->
            val installed = remember(app) { app.installed(context) }
            FluxActionRow(
                linkId = null,
                appPackage = app.packageName.takeIf { installed },
                icon = if (installed) null else "+",
                title = app.title,
                description = stringResource(
                    if (installed) R.string.flux_app_open else R.string.flux_app_get,
                    stringResource(appDescription(app))
                )
            ) { app.open(context) }
        }
        val notes = remember { whatsNew(context) }
        if (notes.isNotEmpty()) {
            SettingsSectionDivider(stringResource(R.string.flux_whats_new))
            notes.forEach { note ->
                Text(
                    "• $note",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        SettingsSectionDivider(stringResource(R.string.flux_credits))
        FluxNote(listOfNotNull(credits, stringResource(R.string.flux_credits_text)).joinToString("\n\n"))
    }
}

private fun appDescription(app: FluxApp) = when (app) {
    FluxApp.KEYBOARD -> R.string.flux_app_keyboard
    FluxApp.GAMING -> R.string.flux_app_gaming
    FluxApp.STREAMING -> R.string.flux_app_streaming
    FluxApp.DESKTOP -> R.string.flux_app_desktop
}

private fun everyText(context: Context, hours: Int) = context.getString(
    when (hours) {
        1 -> R.string.update_check_every_1
        6 -> R.string.update_check_every_6
        24 -> R.string.update_check_every_24
        else -> R.string.update_check_every_2
    }
)

/** The app's notes (assets/whats_new.json's "highlights"), newest first. */
fun whatsNew(context: Context): List<String> = runCatching {
    val json = JSONObject(context.assets.open("whats_new.json").bufferedReader().use { it.readText() })
    val array = json.optJSONArray("highlights") ?: return emptyList()
    (0 until array.length()).mapNotNull { index ->
        val entry = array.opt(index)
        ((entry as? JSONObject)?.optString("text") ?: entry as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }
}.getOrDefault(emptyList())
