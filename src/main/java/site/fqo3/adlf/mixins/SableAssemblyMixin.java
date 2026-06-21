package site.fqo3.adlf.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import site.fqo3.adlf.config.AdlfConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-phase fix for DisplayLink TargetOffset corruption during Sable
 * assembly/disassembly when both the DisplayLink and its target sign
 * are on the same structure (Case 3).
 * <p>
 * Phase 1 (before loadWithComponents):
 *   Detects Case 3 → saves (newPosFromTag, savedOffset).
 * Phase 2 (TAIL):
 *   Uses tag x/y/z for accurate BE lookup. Corrected offset is computed
 *   as {@code savedOffset.rotate(transform.getRotation())} — pure integer
 *   math, no floating-point rounding.
 * <p>
 * All INFO logs are gated by {@link AdlfConfig#debugLogging()}.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public class SableAssemblyMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Two parallel ThreadLocals (avoid inner-class Mixin package isolation) ──
    /** Tag x/y/z written by Sable — accurate destination position for BE lookup. */
    private static final ThreadLocal<List<BlockPos>> LIST_NEWPOS = new ThreadLocal<>();
    /** Original relative offset from DL to its target sign. */
    private static final ThreadLocal<List<BlockPos>> LIST_OFFSET = new ThreadLocal<>();

    // ════════════════════════════════════════════════════════════
    //  Conditional logging helpers
    // ════════════════════════════════════════════════════════════

    private static void logDebug(String msg, Object... args) {
        if (AdlfConfig.debugLogging()) {
            LOGGER.info(msg, args);
        }
    }

    private static void logError(String msg, Object... args) {
        LOGGER.error(msg, args);
    }

    // ════════════════════════════════════════════════════════════
    //  Phase 1: before loadWithComponents
    // ════════════════════════════════════════════════════════════

    @Inject(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                   + "loadWithComponents(Lnet/minecraft/nbt/CompoundTag;"
                   + "Lnet/minecraft/core/HolderLookup$Provider;)V",
            shift = At.Shift.BEFORE), remap = false)
    private static void phase1Before(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks, CallbackInfo ci,
            @Local(ordinal = 0) BlockPos block,
            @Local(ordinal = 0) CompoundTag tag) {

        // ── Is this a DisplayLink? ──
        String id = tag.getString("id");
        if (id == null || !id.contains("display_link")) {
            return;
        }

        // ── Read TargetOffset from tag ──
        BlockPos savedOffset = NbtUtils.readBlockPos(tag, "TargetOffset").orElse(BlockPos.ZERO);
        if (BlockPos.ZERO.equals(savedOffset)) {
            logDebug("[ADLF=P1] DisplayLink with zero TargetOffset, skip. block={}", block);
            return;
        }

        // ── Case 3 detection ──
        BlockPos targetPos = block.offset(savedOffset);
        boolean case3Forward = false;
        int totalBlocks = 0;
        for (BlockPos bp : blocks) {
            totalBlocks++;
            if (bp.distManhattan(targetPos) <= 2) {
                case3Forward = true;
                break;
            }
        }

        boolean bigBlockCoords = Math.abs(block.getX()) > 100000
                              || Math.abs(block.getZ()) > 100000;
        boolean smallOffset = Math.abs(savedOffset.getX()) < 1000
                           && Math.abs(savedOffset.getY()) < 1000
                           && Math.abs(savedOffset.getZ()) < 1000;
        boolean case3Reverse = !case3Forward && bigBlockCoords && smallOffset;

        logDebug("[ADLF=P1] DL @ block={}, offset={}, blocks={}, forward={}, reverse={}",
                block, savedOffset, totalBlocks, case3Forward, case3Reverse);

        if (!case3Forward && !case3Reverse) {
            logDebug("[ADLF=P1] Not Case 3, skip");
            return;
        }

        // ── Read tag x/y/z — Sable's ground truth for destination position ──
        BlockPos newPosFromTag = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));

        logDebug("[ADLF=P1] ★ Case 3 detected! block={}, tagPos={}, offset={}",
                block, newPosFromTag, savedOffset);

        // ── Store in parallel lists ──
        List<BlockPos> npList = LIST_NEWPOS.get();
        List<BlockPos> offList = LIST_OFFSET.get();
        if (npList == null) {
            npList = new ArrayList<>();
            offList = new ArrayList<>();
            LIST_NEWPOS.set(npList);
            LIST_OFFSET.set(offList);
        }
        npList.add(newPosFromTag);
        offList.add(savedOffset);

        logDebug("[ADLF=P1] Cached: size={}", npList.size());
    }

    // ════════════════════════════════════════════════════════════
    //  Phase 2: TAIL — rotation-aware offset correction
    // ════════════════════════════════════════════════════════════

    @Inject(method = "moveBlocks", at = @At("TAIL"), remap = false)
    private static void phase2Tail(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks, CallbackInfo ci) {

        List<BlockPos> npList = LIST_NEWPOS.get();
        if (npList == null || npList.isEmpty()) return;
        List<BlockPos> offList = LIST_OFFSET.get();
        LIST_NEWPOS.remove();
        LIST_OFFSET.remove();

        int total = npList.size();
        logDebug("[ADLF=P2] ═══ TAIL start, {} DL(s) to fix ═══", total);
        ServerLevel dest = transform.getLevel();
        Rotation rot = transform.getRotation();

        int fixed = 0, alreadyOk = 0, notFound = 0;
        for (int i = 0; i < total; i++) {
            BlockPos newDLPos = npList.get(i);       // tag x/y/z — accurate position
            BlockPos savedOffset = offList.get(i);
            try {
                // ── Rotation-aware corrected offset (exact integer math) ──
                BlockPos correctedOffset = savedOffset.rotate(rot);

                // ── Look up BE using tag coordinates ──
                BlockEntity be = dest.getBlockEntity(newDLPos);
                if (be == null) {
                    logDebug("[ADLF=P2] ✗ BE not found! tagPos={}", newDLPos);
                    notFound++;
                    continue;
                }

                CompoundTag nbt = be.saveWithFullMetadata(dest.registryAccess());
                BlockPos currentOffset = NbtUtils.readBlockPos(nbt, "TargetOffset").orElse(BlockPos.ZERO);

                logDebug("[ADLF=P2] [{}/{}] tagPos={}, savedOffset={}, rot={}",
                        i + 1, total, newDLPos, savedOffset, rot);
                logDebug("[ADLF=P2]         correctedOffset={}, currentOffset={}",
                        correctedOffset, currentOffset);

                if (!currentOffset.equals(correctedOffset)) {
                    nbt.put("TargetOffset", NbtUtils.writeBlockPos(correctedOffset));
                    be.loadWithComponents(nbt, dest.registryAccess());
                    logDebug("[ADLF=P2] ✓ Fixed! {} → {}", currentOffset, correctedOffset);
                    fixed++;
                } else {
                    logDebug("[ADLF=P2] offset unchanged, skip");
                    alreadyOk++;
                }
            } catch (Exception e) {
                logError("[ADLF=P2] ✗ Exception: tagPos={}", newDLPos, e);
            }
        }
        logDebug("[ADLF=P2] ═══ TAIL done: fixed={}, ok={}, notFound={}, total={} ═══",
                fixed, alreadyOk, notFound, total);
    }
}