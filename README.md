# Flux Core

What the Flux apps share: [Flux Keyboard](https://github.com/Flux-Sniffer-Mods/Flux-Keyboard),
[Flux Gaming](https://github.com/Flux-Sniffer-Mods/Flux-Gaming),
[Flux Streaming](https://github.com/Flux-Sniffer-Mods/Flux-Streaming) and
[Flux Desktop](https://github.com/Flux-Sniffer-Mods/Flux-Desktop). An Android library each app has
as a submodule in `core/` and builds with itself.

- **The shell** (`shell/`): commands as the ADB shell through root, the built-in shell (paired once
  with wireless debugging) or Shizuku. A Flux app without a helper of its own uses another's, so one
  pairing does for all of them.
- **The link** (`link/`): how the apps talk to each other, through providers only apps signed with
  the same key can reach.
- **A screen of its own** (`display/`) and **input** (`input/`): a virtual display made by the shell,
  its picture as H.264, and keys, touches and a controller injected into it.
- **Updates** (`update/`): each app installs its own new builds from its own repository's releases.
- **The look** (`ui/`): the theme, the settings rows, the ready checklist, Shell access, the crash
  log and the About page with the other Flux apps.

Strings come in English, German, Greek, Spanish, French, Armenian, Italian, Polish, Russian,
Ukrainian and Vietnamese.

GPL-3.0.
