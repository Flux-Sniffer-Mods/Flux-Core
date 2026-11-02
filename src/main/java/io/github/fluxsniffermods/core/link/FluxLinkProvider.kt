package io.github.fluxsniffermods.core.link

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.shell.BuiltInShell

/**
 * An app's end of [FluxLink], at "<package>.link" and only for the other Flux apps (the
 * manifest's permission). Each app answers its own calls in [answer]; every one of them lends
 * its running shell helper ([METHOD_SHELL]), so the shell is paired once for all of them.
 */
abstract class FluxLinkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        // Only the Flux apps themselves (the permission says so too)
        val caller = callingPackage
        if (caller != null && caller != context.packageName && FluxApp.entries.none { it.packageName == caller }) return null
        val identity = Binder.clearCallingIdentity()
        return try {
            when (method) {
                METHOD_SHELL -> BuiltInShell.lend(context)
                else -> answer(method, arg, extras)
            }
        } catch (e: Throwable) {
            io.github.fluxsniffermods.core.FluxLog.w("FluxLink", "$method from $caller: $e")
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    /** This app's answer to [method]; null when it has none. */
    protected abstract fun answer(method: String, arg: String?, extras: Bundle?): Bundle?

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** The shell helper this app has running: its port and token. */
        const val METHOD_SHELL = "shell"
    }
}
