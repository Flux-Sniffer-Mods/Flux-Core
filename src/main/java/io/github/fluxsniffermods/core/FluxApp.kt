package io.github.fluxsniffermods.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The Flux apps: each its own app, signed with the same key, so they find and trust each other
 * ([io.github.fluxsniffermods.core.link.FluxLink]).
 */
enum class FluxApp(val packageName: String, val title: String, val repository: String, val apkName: String) {
    KEYBOARD("io.github.fluxsniffermods.fluxkeyboard", "Flux Keyboard", "Flux-Sniffer-Mods/Flux-Keyboard", "flux-keyboard"),
    GAMING("io.github.fluxsniffermods.fluxgaming", "Flux Gaming", "Flux-Sniffer-Mods/Flux-Gaming", "flux-gaming"),
    STREAMING("io.github.fluxsniffermods.fluxstreaming", "Flux Streaming", "Flux-Sniffer-Mods/Flux-Streaming", "flux-streaming"),
    DESKTOP("io.github.fluxsniffermods.fluxdesktop", "Flux Desktop", "Flux-Sniffer-Mods/Flux-Desktop", "flux-desktop");

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0); true
    }.getOrDefault(false)

    /** Its own page, or its releases to install it from. */
    fun open(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(releasesPage))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val releasesPage: String get() = "https://github.com/$repository/releases"

    companion object {
        /** This app, among them. */
        fun self(context: Context): FluxApp? = entries.firstOrNull { it.packageName == context.packageName }

        /** The others that are installed. */
        fun others(context: Context): List<FluxApp> = entries.filter { it.packageName != context.packageName && it.installed(context) }
    }
}
