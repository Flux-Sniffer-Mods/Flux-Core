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
    /**
     * Settings Flux Titan Hub kept for a module, for its own app to take over: every one whose
     * name starts with one of "prefixes" (a string array), as the answer's own keys and values.
     */
    const val LEGACY_PREFS = "legacy_prefs"

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
    /**
     * How streaming is: "enabled" (answering Moonlight), "session" (streaming now), "separate"
     * (on a screen of its own), "desktop" (Android's desktop there), "game" (the game streamed).
     */
    const val STREAM_STATE = "stream_state"
    /** Streaming on ("1") or off ("0"). */
    const val STREAM_ENABLE = "stream_enable"
    /** Flux Streaming's page for the screen of its own (the TV's), and its own page. */
    const val STREAM_SCREEN_ACTIVITY = "io.github.fluxsniffermods.streaming.ExtraScreenActivity"
    const val STREAM_APP_ACTIVITY = "io.github.fluxsniffermods.streaming.StreamingActivity"
    /**
     * Opening game [arg] now: "mode" is "phone" (nothing to ask), "tv" (streamed to the TV, as
     * set) or "ask" (which, with the TV's "tv" name).
     */
    const val TV_PROMPT = "tv_prompt"
    /** Game [arg] (named "name") played on the TV: turned on, Moonlight opened there into it. */
    const val TV_PLAY = "tv_play"
    /** Whether the volume keys are the TV's now (streaming to it), and the TV's volume "up" or "down" ([arg]). */
    const val TV_VOLUME_HERE = "tv_volume_here"
    const val TV_VOLUME = "tv_volume"

    // ---- Flux Gaming ----

    /**
     * The games, A to Z: parallel arrays "ids", "names", "packages" (each comma-separated) and
     * "launches" (intent URIs that start each, through Flux Gaming), and "art" (each one's art
     * version). [arg] "launcher": none while the keyboard's quick launcher isn't to list them.
     */
    const val GAMES = "games"
    /** A game's art, as a file from Flux Gaming's provider: art/<id>/<cover|portrait>. */
    fun gameArtPath(id: String, kind: String) = "art/" + android.net.Uri.encode(id) + "/" + kind
    /** Gaming mode now: "playing" (the game's package), "keys" (it takes the keys), "sendsKeys" (the game takes keys, not a controller). */
    const val GAME_STATE = "game_state"
    /** Told when "keys" changes (gaming mode takes the keys, or gives them back). */
    const val TOPIC_GAME_KEYS = "game_keys"
    /** The layout's controls as keys, for a game taking keys: parallel "controls" (names) and "codes". */
    const val GAME_LAYOUT = "game_layout"
    /** From the keyboard: [arg] "input_started", "editor_started", "keyboard_requested" or "editor_finished"; "package", "inputType", "fieldId", "hint". */
    const val GAME_EDITOR = "game_editor"
    /** From streaming: the stream mirrors the phone at "width" x "height", or (none) ended or on a screen of its own. */
    const val GAME_STREAMING = "game_streaming"
    /** From streaming: the game on the screen of its own "id" ("width", "height", "desktop"), or (none) back on the phone. */
    const val GAME_EXTRA_SCREEN = "game_extra_screen"
    /** From streaming: the app [arg] in front on the stream. */
    const val GAME_FRONT = "game_front"
    /** From streaming, after it ended unexpectedly: the phone's screen put back. */
    const val GAME_RECOVER = "game_recover"
    /** Waits (a few seconds at most) for gaming mode's screen changes to finish. */
    const val GAME_SETTLE = "game_settle"
    /** The "intent" (a PendingIntent) that places keys on the game's screen. */
    const val GAME_PLACE_KEYS = "game_place_keys"
    /** Flux Gaming's game launcher: the launches in [GAMES] go through it; this extra says the TV was asked already. */
    const val EXTRA_TV_ASKED = "tv_asked"
}
