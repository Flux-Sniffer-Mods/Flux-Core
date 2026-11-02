package io.github.fluxsniffermods.core.link

/** What the Flux apps ask of each other ([FluxLink.call]), and what they tell each other. */
object LinkMethods {
    // ---- Flux Keyboard ----

    /**
     * The built-in keyboard's layers as Flux Keyboard has them (with your edits): parallel
     * arrays "<layer>_keys" (key codes) and "<layer>_chars" for the layers alt, sym and
     * sym_shifted.
     */
    const val KEY_LAYERS = "key_layers"
    fun layerKeys(layer: String) = "${layer}_keys"
    fun layerChars(layer: String) = "${layer}_chars"
    const val LAYER_ALT = "alt"
    const val LAYER_SYM = "sym"
    const val LAYER_SYM_SHIFTED = "sym_shifted"

    // ---- Flux Desktop ----

    /** The desktop: "installed" (set up), "label" (its Linux, as named), "running" and "starting". */
    const val DESKTOP_INFO = "desktop_info"
    /**
     * Flux Desktop's activity (exported to the Flux apps only) that starts the desktop if it
     * isn't running and opens it on the display in [EXTRA_SCREEN] (none or -1: the phone's).
     */
    const val DESKTOP_OPEN_ACTIVITY = "io.github.fluxsniffermods.desktop.LinuxOpenActivity"
    const val EXTRA_SCREEN = "screen"

    // ---- Flux Streaming ----

    /** The screen of its own a stream has open (a TV's, through Moonlight): its display "id", or nothing. */
    const val STREAM_SCREEN = "stream_screen"
}
