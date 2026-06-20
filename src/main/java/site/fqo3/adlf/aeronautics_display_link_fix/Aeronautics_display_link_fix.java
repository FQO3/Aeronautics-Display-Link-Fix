package site.fqo3.adlf.aeronautics_display_link_fix;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Aeronautics Display Link Fix
 * 
 * Fixes the bug where Display Link's targetOffset becomes corrupted
 * after assembly/disassembly (physics-ification) in Create: Aeronautics.
 * 
 * Root cause: Sable's SubLevelAssemblyHelper.moveBlocks() saves and loads
 * block entity NBT without adjusting targetOffset for the new worldPosition,
 * causing getTargetPosition() to point to a wrong coordinate.
 */
@Mod(Aeronautics_display_link_fix.MODID)
public class Aeronautics_display_link_fix {
    public static final String MODID = "aeronautics_display_link_fix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Aeronautics_display_link_fix(IEventBus modEventBus) {
        LOGGER.info("[ADLF] Display Link Fix loaded - will correct targetOffset during assembly/disassembly");
    }
}