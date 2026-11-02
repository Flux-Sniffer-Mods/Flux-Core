package io.github.fluxsniffermods.core.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fluxsniffermods.core.R
import io.github.fluxsniffermods.core.RestrictedSettings
import io.github.fluxsniffermods.core.shell.AdbShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Something an app can't work without yet: what it is, why, whether it's missing, and its fix. */
class Need(
    val title: Int,
    val description: (Context) -> String,
    val missing: (Context) -> Boolean,
    val fix: (Context) -> Unit
)

/** The needs every Flux app shares, each with its own reason. */
object Needs {
    /** The shell (root, a helper or Shizuku), set up on the app's Shell access page ([open]). */
    fun shell(why: Int, open: (Context) -> Unit) = Need(
        R.string.root_title,
        { context ->
            val paired = runCatching { io.github.fluxsniffermods.core.shell.ShellSetup.paired(context) }.getOrDefault(false)
            context.getString(why) + " " + context.getString(if (paired) R.string.shell_access_paired else R.string.shell_access_off)
        },
        { !runCatching { AdbShell.available() }.getOrDefault(false) },
        open
    )

    /** The app's accessibility [service] switched on. */
    fun accessibility(service: Class<*>, why: Int) = Need(
        R.string.readiness_accessibility,
        { context -> context.getString(why) },
        { context -> !accessibilityOn(context, service) },
        { context -> RestrictedSettings.openAccessibility(context) }
    )

    fun overlay(why: Int) = Need(
        R.string.readiness_overlay,
        { context -> context.getString(why) },
        { context -> !Settings.canDrawOverlays(context) },
        { context -> open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
    )

    fun notifications(why: Int) = Need(
        R.string.readiness_notifications,
        { context -> context.getString(why) },
        { context -> !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() },
        { context -> open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
    )

    private fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Whether the app's accessibility [service] is switched on. */
    fun accessibilityOn(context: Context, service: Class<*>): Boolean = runCatching {
        val wanted = ComponentName(context, service)
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').any { ComponentName.unflattenFromString(it) == wanted }
    }.getOrDefault(false)
}

/**
 * The app's ready checklist, at the top of its page: only what it still needs, each a tap from
 * its fix; nothing once it's ready (or [readyText], a quiet line). Checked again on coming back
 * and every few seconds (the shell starts by itself, a pairing comes from the TV).
 */
@Composable
fun ReadinessCard(needs: List<Need>, modifier: Modifier = Modifier, readyText: String? = null) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf<List<Need>?>(null) }
    var check by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(needs, check) {
        while (true) {
            missing = withContext(Dispatchers.IO) { needs.filter { runCatching { it.missing(context) }.getOrDefault(false) } }
            delay(3_000)
        }
    }
    val now = missing ?: return
    if (now.isEmpty()) {
        if (readyText != null) Text(
            "✓ $readyText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.readiness_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            now.forEach { need ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { need.fix(context); check++ }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("○", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(need.title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(need.description(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}
