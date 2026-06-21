# Aeronautics Display Link Fix [ADLF]
# 航空学显示链接器修复 [ADLF]

[中文](#chinese) | [English](#english)

---

## 中文

### 简介

修复 Create: Aeronautics（机械动力航空学）中当显示链接器（Display Link）与告示牌同处一架飞行器时，物理化/反物理化后 TargetOffset 异常导致无法正常显示的问题。

### 适用场景（Case 3）

| 场景 | 说明 | 修复前 | 修复后 |
|------|------|--------|--------|
| **Case 1** | 告示牌在地面，只在飞行器上放置 DL | ✅ 正常 | ✅ 正常 |
| **Case 2** | 告示牌在另一飞行器上 | ✅ 正常 | ✅ 正常 |
| **Case 3** | 告示牌和 DL 在同一架飞行器上 | ❌ TargetOffset 被错误变换 | ✅ 正确的旋转感知偏移 |

### 实现原理

本 Mod 通过 Mixin 注入 Sable 的 `SubLevelAssemblyHelper.moveBlocks()` 方法：

1. **Phase 1**（`loadWithComponents` 之前）：检测 DisplayLink 的 TargetOffset 是否为相邻方块的相对偏移（绝对值 < 1000），若目标方块也在同一组装中（Case 3），则保存原始 DL 坐标和偏移
2. **Phase 2**（`moveBlocks` TAIL）：用 Sable 的 `transform.apply()` 独立计算旧目标方块的正确新位置，然后算出 `correctedOffset = newTarget - newDLPos`，仅在当前值不正确时写入

双层判定逻辑覆盖正向（方块→物理化）和反向（物理化→方块）两个组装方向，同时避免误判正常的 Case 1/2 场景。

### v1.2 新功能

- **旋转感知偏移修正**：v1.2 现在会正确计算旋转后的 TargetOffset，支持 Aeronautics 内部的转角组装调用
- **配置文件**：`adlf-common.toml` 提供 `debug = false/true` 开关，无需重启即可切换详细日志
- **日志优化**：生产环境下默认静默（仅报错日志），开启 debug 后输出完整的 Case 3 检测和修复追踪信息

### 构建

```bash
# 前置条件：JDK 21
git clone <repo>
cd Aeronautics-display-link-fix
./gradlew build
# jar 输出：build/libs/aeronautics_display_link_fix-<version>.jar
```

### 依赖

- Minecraft 1.21.1 + NeoForge 21.1.233
- Create 6.0.10
- Sable 1.2.2
- Create: Simulated 1.2.1
- Create: Aeronautics 1.2.1

### 注意事项

- 🧠 **本项目由 AI辅助编写**，代码结构和实现细节由 AI 辅助生成。
- 🔧 由于作者不保证持续维护，后续版本兼容性可能滞后。
- 🏢 **Create / Sable / Aeronautics 官方未来可能会在架构层面修复此 Bug**，届时本 Mod 将不再需要。请关注官方更新日志。

---

## English

### Overview

Fixes a bug in Create: Aeronautics where the Display Link's `TargetOffset` gets corrupted during physics assembly/disassembly when both the Display Link and its target sign are on the same contraption.

### Applicable Scenario (Case 3)

| Case | Description | Before Fix | After Fix |
|------|-------------|------------|-----------|
| **Case 1** | Sign on ground, DL on airship only | ✅ OK | ✅ OK |
| **Case 2** | Sign on a different contraption | ✅ OK | ✅ OK |
| **Case 3** | Sign and DL on the **same** contraption | ❌ TargetOffset corrupted | ✅ Rotation-aware correction |

### How It Works

A Mixin injects into Sable's `SubLevelAssemblyHelper.moveBlocks()` method:

1. **Phase 1** (before `loadWithComponents`): Detects whether the DisplayLink's target sign is in the same assembly (Case 3). If so, saves the original DL position and relative offset.
2. **Phase 2** (`moveBlocks` TAIL): Uses Sable's `transform.apply()` to independently compute the correct new target position, then calculates `correctedOffset = newTarget - newDLPos`. Writes back only if the current value is incorrect.

A two-layer detection covers both forward (blocks→physics) and reverse (physics→blocks) assembly directions, while correctly excluding normal Case 1/2 scenarios.

### v1.2 New Features

- **Rotation-aware offset correction**: v1.2 now properly calculates the TargetOffset after rotation, supporting Aeronautics' internal rotated assembly calls
- **Configuration file**: `adlf-common.toml` provides a `debug = false/true` toggle; switch without restart to enable detailed tracing
- **Optimized logging**: Silent in production by default (errors only); enable debug for full Case 3 detection and fix tracing

### Build

```bash
# Prerequisites: JDK 21
git clone <repo>
cd Aeronautics-display-link-fix
./gradlew build
# Output: build/libs/aeronautics_display_link_fix-<version>.jar
```

### Dependencies

- Minecraft 1.21.1 + NeoForge 21.1.233
- Create 6.0.10
- Sable 1.2.2
- Create: Simulated 1.2.1
- Create: Aeronautics 1.2.1

### Notes

- 🧠 **This mod was developed with AI assistance** — some of the code structure and implementation details were AI-generated.
- 🔧 The author does not guarantee ongoing maintenance. Future version compatibility may lag behind.
- 🏢 **Create / Sable / Aeronautics may fix this bug at the architecture level in the future**, at which point this mod will no longer be needed. Please check official changelogs.