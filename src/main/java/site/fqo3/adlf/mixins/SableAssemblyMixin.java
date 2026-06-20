package site.fqo3.adlf.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 两步法修复 DisplayLink TargetOffset 在 Case 3（DL 和广告牌同步组装）时被错误变换的问题。
 *
 * Phase 1 (loadWithComponents 之前):
 *   检测 DL 的目标方块是否在同一组装中 → 保存 Tag 中的 newPos + 原始 offset 到列表
 * Phase 2 (moveBlocks 尾部):
 *   对每项在 dest 世界取 BE → 检查 offset 是否已变化 → 若变化则恢复原始值
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public class SableAssemblyMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 三组平行列表，用索引关联（避免内部类触发 Mixin 包隔离限制）
    private static final ThreadLocal<List<BlockPos>> LIST_NEWPOS = new ThreadLocal<>();
    private static final ThreadLocal<List<BlockPos>> LIST_OFFSET = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> LIST_DEBUG = new ThreadLocal<>(); // 调试

    // ============================================================
    //  Phase 1: loadWithComponents 调用前，检测 Case 3 并保存数据
    // ============================================================
    @Inject(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
            shift = At.Shift.BEFORE), remap = false)
    private static void phase1Before(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks, CallbackInfo ci,
            @Local(ordinal = 0) BlockPos block,
            @Local(ordinal = 0) CompoundTag tag) {

        // ── 判断是否为 DisplayLink ──
        String id = tag.getString("id");
        if (id == null || !id.contains("display_link")) {
            return;
        }

        // ── 从 tag 读取 TargetOffset ──
        BlockPos savedOffset = NbtUtils.readBlockPos(tag, "TargetOffset").orElse(BlockPos.ZERO);
        if (BlockPos.ZERO.equals(savedOffset)) {
            LOGGER.info("[ADLF=P1] DisplayLink 但 TargetOffset 为零，跳过。block={}", block);
            return;
        }

        // ── 双层判定：区分 Case 1/2/3 ──
        // 第一层：target 方块在 blocks 列表中 → Case 3 正向（方块→物理化）
        // 第二层：block 是子世界坐标（大数字）+ offset 小 → Case 3 反向（物理化→方块）
        boolean case3Forward = false;
        int totalBlocks = 0;
        for (BlockPos bp : blocks) {
            totalBlocks++;
            if (bp.equals(block.offset(savedOffset))) {
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

        LOGGER.info("[ADLF=P1] DL @ block={}, offset={}, blocks总数={}, case3Forward={}, case3Reverse={}",
                block, savedOffset, totalBlocks, case3Forward, case3Reverse);

        if (!case3Forward && !case3Reverse) {
            LOGGER.info("[ADLF=P1] 非 Case 3，跳过");
            return;
        }

        // ── 从 tag 的 x/y/z 读取 newPos（Sable 已将坐标写进 tag）──
        BlockPos newPosFromTag = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));

        LOGGER.info("[ADLF=P1] ★ 检测到 Case 3！tag.newPos={}, savedOffset={}", newPosFromTag, savedOffset);
        LOGGER.info("[ADLF=P1]    原始 block={}，offset={}", block, savedOffset);

        // ── 存入三组平行列表 ──
        List<BlockPos> npList = LIST_NEWPOS.get();
        List<BlockPos> offList = LIST_OFFSET.get();
        List<String> dbgList = LIST_DEBUG.get();
        if (npList == null) {
            npList = new ArrayList<>();
            offList = new ArrayList<>();
            dbgList = new ArrayList<>();
            LIST_NEWPOS.set(npList);
            LIST_OFFSET.set(offList);
            LIST_DEBUG.set(dbgList);
        }
        npList.add(newPosFromTag);
        offList.add(savedOffset);
        dbgList.add(block.toShortString() + "→" + newPosFromTag.toShortString());

        LOGGER.info("[ADLF=P1] 已缓存: 列表大小={}", npList.size());
    }

    // ============================================================
    //  Phase 2: moveBlocks 尾部，恢复 offset
    // ============================================================
    @Inject(method = "moveBlocks", at = @At("TAIL"), remap = false)
    private static void phase2Tail(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks, CallbackInfo ci) {

        List<BlockPos> npList = LIST_NEWPOS.get();
        if (npList == null || npList.isEmpty()) return;
        List<BlockPos> offList = LIST_OFFSET.get();
        List<String> dbgList = LIST_DEBUG.get();
        LIST_NEWPOS.remove(); LIST_OFFSET.remove(); LIST_DEBUG.remove();

        int total = npList.size();
        LOGGER.info("[ADLF=P2] ═══ TAIL 开始，需要修复 {} 个 DL ═══", total);
        ServerLevel dest = transform.getLevel();

        int fixed = 0, alreadyOk = 0, notFound = 0;
        for (int i = 0; i < total; i++) {
            BlockPos newPos = npList.get(i);
            BlockPos savedOffset = offList.get(i);
            try {
                BlockEntity be = dest.getBlockEntity(newPos);
                if (be == null) {
                    LOGGER.warn("[ADLF=P2] ✗ 未找到 BE！newPos={}", newPos);
                    notFound++; continue;
                }
                CompoundTag nbt = be.saveWithFullMetadata(dest.registryAccess());
                BlockPos currentOffset = NbtUtils.readBlockPos(nbt, "TargetOffset").orElse(BlockPos.ZERO);
                LOGGER.info("[ADLF=P2] [{}/{}] newPos={}, 当前offset={}, 原始offset={}",
                        i + 1, total, newPos, currentOffset, savedOffset);
                if (!currentOffset.equals(savedOffset)) {
                    nbt.put("TargetOffset", NbtUtils.writeBlockPos(savedOffset));
                    be.loadWithComponents(nbt, dest.registryAccess());
                    LOGGER.info("[ADLF=P2] ✓ 已修复！{} → {}", currentOffset, savedOffset);
                    fixed++;
                } else {
                    LOGGER.info("[ADLF=P2] offset 未变化，无需修复");
                    alreadyOk++;
                }
            } catch (Exception e) {
                LOGGER.error("[ADLF=P2] ✗ 异常: newPos={}", newPos, e);
            }
        }
        LOGGER.info("[ADLF=P2] ═══ TAIL 完成: 修复={}, 不需要={}, 未找到={}, 总计={} ═══",
                fixed, alreadyOk, notFound, total);
    }
}