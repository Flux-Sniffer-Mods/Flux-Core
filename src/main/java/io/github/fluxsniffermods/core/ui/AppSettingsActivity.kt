package io.github.fluxsniffermods.core.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import io.github.fluxsniffermods.core.FluxCore

/** The app's About page ([AppSettingsScreen]): updates, shell access, the crash log, the other Flux apps. */
class AppSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val about = FluxCore.about
        setContent {
            FluxTheme {
                AppSettingsScreen(
                    onBack = { finish() },
                    credits = about.credits?.let { stringResource(it) },
                    crashSections = about.crashSections,
                    crashTags = about.crashTags,
                    startOnShell = intent.getBooleanExtra(EXTRA_SHELL, false)
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SHELL = "shell"

        fun open(context: Context, shell: Boolean = false) {
            runCatching {
                context.startActivity(
                    Intent(context, AppSettingsActivity::class.java).putExtra(EXTRA_SHELL, shell).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        /** The app's Shell access page. */
        fun openShell(context: Context) = open(context, shell = true)
    }
}
