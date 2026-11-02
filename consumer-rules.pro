# The shell helpers are started by name with app_process
-keep class io.github.fluxsniffermods.core.shell.ShellServer { public static void main(java.lang.String[]); }
-keep class io.github.fluxsniffermods.core.input.InputServer { public static void main(java.lang.String[]); }
-keep class io.github.fluxsniffermods.core.display.ExtraScreenServer { public static void main(java.lang.String[]); }
-keep class io.github.fluxsniffermods.core.display.WifiDisplayServer { public static void main(java.lang.String[]); }
-keep class io.github.muntashirakon.adb.** { *; }
# Shizuku's shell processes are started by reflection (Shizuku.newProcess is private)
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
