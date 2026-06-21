# Changelog

## v1.2 — 旋转感知偏移修正

### 新增
- **旋转感知 TargetOffset 计算**：Phase 2 现在使用 `transform.apply(oldTargetAbsolute)` 独立计算目标方块的正确新位置，然后 `correctedOffset = newTarget - newDLPos`，正确处理 Sable 旋转装配
- **配置文件**：`adlf-common.toml` 新增 `debug` 开关（默认 `false`），无需重启即可切换详细日志
- **条件日志**：生产模式仅输出错误日志；开启 `debug = true` 后输出完整的 Case 3 检测和修复追踪

### 更改
- 主 mod 入口从 `Aeronautics_display_link_fix.java` 迁移至 `ADLFMod.java`，使用 NeoForge `ModContainer` 注册配置
- 配置类从 `Config.java`（仅 enabled 字段）迁移至 `AdlfConfig.java`（debug 开关 + 完整 spec builder）
- Mixin ThreadLocal 从 `LIST_NEWPOS` 改为 `LIST_BLOCKPOS`（保存原始 `block` 而非 tag 中的 newPos），用于 Phase 2 计算
- 日志调用全部切换为条件包装器 `logDebug()` / `logError()`，默认不产生信息输出

### 移除
- 旧版 `Config.java`（已被 `AdlfConfig` 替代）
- `LIST_DEBUG` ThreadLocal（冗余，debug 日志由条件开关控制）
- 不再需要 `newPosFromTag` 读取（Phase 2 自行计算新位置）

---

## v1.1 - 双层判定修复

### 修复
- 修复 Case 3：当显示链接器（Display Link）与告示牌在同一飞行器上同步物理化/反物理化时，TargetOffset 被错误变换为世界坐标级大偏移的问题
- 采用双层判定逻辑，同时覆盖正向（方块→物理化）和反向（物理化→方块）组装方向
- 避免误判正常的 Case 1（告示牌在地面）和 Case 2（告示牌在另一飞行器）场景

### 技术细节
- TargetOffset 读取方式从 `@Local` 字节码索引改为从 NBT tag 直接读取（`tag.getInt("x/y/z")`），消除编译差异风险
- 移除 Mixin 内部类 `SavedCase3`，改用三组平行 `ThreadLocal` 列表，避免 Mixin 包隔离限制导致 `IllegalClassLoadError`
- 修正 `registryAccess` 从源世界改为目标世界（`dest.registryAccess()`）