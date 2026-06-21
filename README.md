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
| **Case 3** | 告示牌和 DL 在同一架飞行器上 | ❌ TargetOffset 被错误变换 | ✅ 保持原始偏移 |

### 实现原理

本 Mod 通过 Mixin 注入 Sable 的 `SubLevelAssemblyHelper.moveBlocks()` 方法：

1. **Phase 1**（`loadWithComponents` 之前）：检测 DisplayLink 的 TargetOffset 是否为相邻方块的相对偏移（绝对值 < 1000），若是则保存原始值和目标坐标
2. **Phase 2**（`moveBlocks` TAIL）：在目标世界查找对应的 BlockEntity，若 TargetOffset 已被错误变换为世界坐标级大偏移，则恢复原始值

双层判定逻辑覆盖正向（方块→物理化）和反向（物理化→方块）两个组装方向，同时避免误判正常的 Case 1/2 场景。

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
### 已知问题

- 无法处理旋转后Offset（下版本修复）
- 显示目标物理化/去物理化无法追踪（不一定会修复）
---

## English

### Overview

Fixes a bug in Create: Aeronautics where the Display Link's `TargetOffset` gets corrupted during physics assembly/disassembly when both the Display Link and its target sign are on the same contraption.

### Applicable Scenario (Case 3)

| Case | Description | Before Fix | After Fix |
|------|-------------|------------|-----------|
| **Case 1** | Sign on ground, DL on airship only | ✅ OK | ✅ OK |
| **Case 2** | Sign on a different contraption | ✅ OK | ✅ OK |
| **Case 3** | Sign and DL on the **same** contraption | ❌ TargetOffset corrupted | ✅ Preserved |

### How It Works

A Mixin injects into Sable's `SubLevelAssemblyHelper.moveBlocks()` method:

1. **Phase 1** (before `loadWithComponents`): Detects whether the DisplayLink's TargetOffset is a small relative offset (< 1000 blocks). If so, saves the original value and the target position.
2. **Phase 2** (`moveBlocks` TAIL): Looks up the BlockEntity in the destination world. If TargetOffset has been incorrectly transformed into world-scale coordinates, restores the original value.

A two-layer detection covers both forward (blocks→physics) and reverse (physics→blocks) assembly directions, while correctly excluding normal Case 1/2 scenarios.

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

- 🧠 **This mod was developed with AI assistance ** — some of the code structure and implementation details were AI-generated.
- 🔧 The author does not guarantee ongoing maintenance. Future version compatibility may lag behind.
- 🏢 **Create / Sable / Aeronautics may fix this bug at the architecture level in the future**, at which point this mod will no longer be needed. Please check official changelogs.
