# Implementation Plan

## [Overview]

Upgrade the existing Mixin-based fix in `SableAssemblyMixin` to support rotation-aware offset recalculation, add a configurable debug logging system, and clean up the code structure for maintainability. The fix corrects DisplayLink's `targetOffset` field when DisplayLink and its target sign are assembled together (Case 3) under Sable's physics sub-level system, preventing broken connections after assembly/disassembly.

The current v1.1 fix only restores the original offset value. The upgraded v1.2 adds proper coordinate transformation when rotation is present (e.g., from Aeronautics internal calls), improves the detection heuristic for Case 3, and introduces a config file to toggle verbose debug logging. This ensures the fix works correctly in all scenarios while minimizing log spam in production.

## [Types]

A new `AdlfConfig` class will hold the debug toggle setting, built on NeoForge's `SimpleConfig` system.

- `AdlfConfig` (`src/main/java/site/fqo3/adlf/config/AdlfConfig.java`):
  - `boolean debug`: default `false`. When `true`, enables detailed INFO-level logging from the Mixin. When `false`, only WARN/ERROR-level logs are emitted.
  - Use `net.neoforged.neoforge.common.ModConfigSpec` for standard NeoForge config integration.

## [Files]

### New files to create:
1. `src/main/java/site/fqo3/adlf/config/AdlfConfig.java` — Config class with debug toggle and spec builder.
2. `src/main/java/site/fqo3/adlf/ADLFMod.java` — Main mod entry point. Registers config, listens for config reload events.

### Existing files to modify:
1. `src/main/java/site/fqo3/adlf/mixins/SableAssemblyMixin.java` — Major rewrite:
   - Phase 1: Store `(oldDLPos, savedOffset)` instead of just `(newPosFromTag, savedOffset)`. Keep Case 3 detection logic (two-layer heuristic).
   - Phase 2: Use `transform` to calculate proper new absolute target position; compute corrected offset as `newTargetAbsolute - newDLPos`. Restore only if corrupted.
   - Replace `LOGGER.info(...)` calls with conditional logging via `AdlfConfig.debugLogging()`.

2. `src/main/resources/META-INF/neoforge.mods.toml` — Update with mod name, description, etc. if needed.

3. `gradle.properties` — Bump `mod_version` to `1.2`.

## [Functions]

### New functions:

#### `ADLFMod.java`
- `ADLFMod()` — Constructor: registers config to the mod event bus.
- `onConfigReload(ModConfigEvent.Reloading)` — Re-reads config when changed.
- `static ADLFMod getInstance()` — Singleton accessor if needed.

### Modified functions:

#### `SableAssemblyMixin.java` — Phase 1 (`phase1Before`):
- **Change**: Save `block` (old position) alongside `savedOffset`, instead of `newPosFromTag`.
  - New list: `LIST_BLOCKPOS` (stores the original `block` parameter)
  - `LIST_OFFSET` stays (stores `savedOffset`)
  - `LIST_NEWPOS` becomes redundant → removed
- **Logic**: `oldTargetAbsolute = block.offset(savedOffset)` — computed later in Phase 2.
- **Change**: Replace `LOGGER.info(...)` with helper method `logDebug(String)` which checks the config.

#### `SableAssemblyMixin.java` — Phase 2 (`phase2Tail`):
- **Change**: Instead of reading `newPos` from `npList` and restoring `savedOffset`, compute:
  ```java
  BlockPos oldDLPos = blockList.get(i);
  BlockPos savedOffset = offsetList.get(i);
  BlockPos oldTargetAbsolute = oldDLPos.offset(savedOffset);
  BlockPos newTargetAbsolute = transform.apply(oldTargetAbsolute); // rotation+translation
  BlockPos newDLPos = be.getBlockPos();
  BlockPos correctedOffset = newTargetAbsolute.subtract(newDLPos);
  ```
- Update NBT/TE only if `currentOffset != correctedOffset`.
- **Change**: Conditional logging via `logDebug()` + `logError()` wrappers.

#### `SableAssemblyMixin.java` — Helper:
- `private static void logDebug(String msg)` — Logs at INFO level only if config debug is true.
- `private static void logError(String msg)` — Always logs at ERROR level.
- `private static boolean isDebugEnabled()` — Reads from config (cached at start of each Phase to avoid repeated file access).

## [Classes]

### New classes:

#### `ADLFMod` (site.fqo3.adlf.ADLFMod)
- File: `src/main/java/site/fqo3/adlf/ADLFMod.java`
- Implements `net.neoforged.bus.api.IEventBus` subscriber.
- Key methods:
  - Constructor: `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AdlfConfig.SPEC, "adlf-common.toml")`
  - `@SubscribeEvent` on `ModConfigEvent.Reloading` to invalidate cached debug flag.

#### `AdlfConfig` (site.fqo3.adlf.config.AdlfConfig)
- File: `src/main/java/site/fqo3/adlf/config/AdlfConfig.java`
- Fields:
  - `public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()`
  - `public static ModConfigSpec SPEC`
  - `public static ModConfigSpec.BooleanValue DEBUG`
- Static initializer block defines the spec.
- Helper: `public static boolean debugLogging()` — returns `DEBUG.get()`.

### Modified classes:

#### `SableAssemblyMixin`
- Remove `LIST_NEWPOS`, `LIST_DEBUG` ThreadLocals.
- Add `LIST_BLOCKPOS` (ThreadLocal<List<BlockPos>>).
- Update all references.

## [Dependencies]

No new dependencies. Existing dependencies suffice:
- `sponge-mixin:0.15.2+mixin.0.8.7`
- `mixinextras-common:0.5.3`
- `create-1.21.1-6.0.10.jar` (local)
- `sable-neoforge-1.21.1-1.2.2.jar` (local)
- NeoForge (implicit, via MDG plugin)

## [Testing]

Manual testing approach:
1. **Case 3 no-rotation**: Assemble a structure with DisplayLink + sign together → verify link still works.
2. **Case 3 with rotation**: (If Aeronautics can be configured to rotate) verify offset is correctly rotated.
3. **Case 1**: DisplayLink targets sign outside assembly → verify no false positive fix applied.
4. **Case 2**: DisplayLink on assembly targets sign outside → verify unchanged.
5. **Config toggle**: Set `debug=true` in config → verbose logs appear. Set `debug=false` → only error logs.
6. **Config reload**: Change config while server is running → behavior updates without restart.

## [Implementation Order]

1. Create `AdlfConfig.java` with the debug toggle spec.
2. Create `ADLFMod.java` with config registration.
3. Rewrite `SableAssemblyMixin.java`:
   a. Replace LIST_NEWPOS with LIST_BLOCKPOS.
   b. Add conditional logging helpers.
   c. Replace Phase 1 save logic.
   d. Rewrite Phase 2 with rotation-aware offset calculation.
4. Update `gradle.properties` version to 1.2.
5. Build and test.