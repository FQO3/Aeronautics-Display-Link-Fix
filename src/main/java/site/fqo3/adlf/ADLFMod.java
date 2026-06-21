package site.fqo3.adlf;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import site.fqo3.adlf.config.AdlfConfig;

/**
 * Aeronautics Display Link Fix — main mod entry point.
 * <p>
 * Fixes the bug where Create's DisplayLink {@code targetOffset} gets corrupted
 * during Sable/ValkyrienSkies assembly/disassembly when the DisplayLink and
 * its target sign are on the same structure (Case 3).
 * <p>
 * Registers {@link AdlfConfig} and listens for config reload events so that
 * the debug logging toggle takes effect without a full game restart.
 */
@Mod(ADLFMod.MODID)
public class ADLFMod {

    public static final String MODID = "aeronautics_display_link_fix";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructed by NeoForge. Registers the common config spec and wires up
     * the reload listener.
     *
     * @param modEventBus the mod-specific event bus
     * @param modContainer the mod container for this mod
     */
    public ADLFMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the config spec so NeoForge creates adlf-common.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, AdlfConfig.SPEC, "adlf-common.toml");

        // Listen for config load and reload events to refresh cached values
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        LOGGER.info("[ADLF] Display Link Fix v{} loaded — config not yet loaded",
                modContainer.getModInfo().getVersion());
    }

    /**
     * Handles initial config load.
     */
    private void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AdlfConfig.SPEC) {
            AdlfConfig.refresh();
            LOGGER.info("[ADLF] Config loaded — debug logging is {}",
                    AdlfConfig.debugLogging() ? "ENABLED" : "disabled");
        }
    }

    /**
     * Handles config reload (e.g. when the user edits the toml file and
     * runs a reload command). Refreshes the cached debug flag so the Mixin
     * picks up changes without restarting the game.
     */
    private void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AdlfConfig.SPEC) {
            AdlfConfig.refresh();
            LOGGER.info("[ADLF] Config reloaded — debug logging is {}",
                    AdlfConfig.debugLogging() ? "ENABLED" : "disabled");
        }
    }
}