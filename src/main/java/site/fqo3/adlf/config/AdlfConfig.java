package site.fqo3.adlf.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ADLF configuration with debug logging toggle.
 * <p>
 * When {@code debug} is {@code true}, the Mixin emits detailed INFO-level logs
 * for tracing offset correction during assembly/disassembly. When {@code false}
 * (default), only WARN/ERROR-level messages are logged.
 */
public final class AdlfConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG = BUILDER
            .comment("Enable verbose debug logging for DisplayLink offset correction. "
                   + "Set to true to trace every Case 3 detection and fix in the game log. "
                   + "Default: false (only warnings and errors are logged).")
            .define("debug", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Cached value, updated on config load/reload
    private static boolean debugEnabled = false;

    private AdlfConfig() {
        // utility class — no instances
    }

    /**
     * Returns the current debug toggle value.
     * <p>
     * The value is cached on config load/reload for fast access from the Mixin.
     *
     * @return {@code true} if verbose debug logging is enabled
     */
    public static boolean debugLogging() {
        return debugEnabled;
    }

    /**
     * Called by the owning mod when the config file is loaded or reloaded.
     * Refreshes the cached debug flag so that the Mixin sees changes without
     * a full game restart.
     */
    public static void refresh() {
        debugEnabled = DEBUG.get();
    }
}