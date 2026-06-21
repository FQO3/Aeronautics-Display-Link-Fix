# Changelog

## v1.1 - 双层判定修复

### 修复
- 修复 Case 3：当显示链接器（Display Link）与告示牌在同一飞行器上同步物理化/反物理化时，TargetOffset 被错误变换为世界坐标级大偏移的问题
- 采用双层判定逻辑，同时覆盖正向（方块→物理化）和反向（物理化→方块）组装方向
- 避免误判正常的 Case 1（告示牌在地面）和 Case 2（告示牌在另一飞行器）场景

### 技术细节
- TargetOffset 读取方式从 `@Local` 字节码索引改为从 NBT tag 直接读取（`tag.getInt("x/y/z")`），消除编译差异风险
- 移除 Mixin 内部类 `SavedCase3`，改用三组平行 `ThreadLocal` 列表，避免 Mixin 包隔离限制导致 `IllegalClassLoadError`
- 修正 `registryAccess` 从源世界改为目标世界（`dest.registryAccess()`）

---

### Fixed
- Fixed Case 3: DisplayLink's TargetOffset was incorrectly transformed into world-scale coordinates when the DisplayLink and its sign are on the same contraption
- Two-layer detection covers both forward (blocks→physics) and reverse (physics→blocks) assembly directions
- Correctly excludes normal Case 1 (sign on ground) and Case 2 (sign on different contraption) scenarios

### Technical
- TargetOffset now read from NBT tag directly (`tag.getInt("x/y/z")`) instead of `@Local` bytecode indexing
- Removed inner class `SavedCase3`, replaced with three parallel `ThreadLocal` lists to avoid Mixin package isolation `IllegalClassLoadError`
- Fixed `registryAccess` from source level to destination level (`dest.registryAccess()`)