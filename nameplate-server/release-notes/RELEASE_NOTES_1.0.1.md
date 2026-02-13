# NameplateBuilder 1.0.1 Release Notes

## Summary

Version 1.0.1 is a stability and performance update that fixes critical bugs related to nameplate lag and entity lifecycle management. This release significantly improves the visual quality of nameplates on moving entities and eliminates crashes during chunk serialization.

## What Changed

### Server Plugin: Version 1.0.1
The **NameplateBuilder server plugin** has been updated with the following improvements:

## Bug Fixes

### 1. Fixed Nameplate Lag on Moving Entities
**Problem:** Nameplates visibly lagged behind fast-moving NPCs, creating a floating-text-trailing effect.

**Root Cause:** The Hytale ECS requires component updates to use `CommandBuffer.replaceComponent()` during tick processing, which introduces a 1-tick delay. By the time the anchor entity position update executed, the real entity had already moved.

**Solution:** Implemented **velocity-based predictive positioning** with exponential smoothing:
- Tracks entity velocity as a delta between current and previous positions
- Applies exponential moving average (70% current velocity + 30% new velocity) to reduce jitter
- Positions anchor where the entity *will be* next tick, compensating for the CommandBuffer delay
- Result: Smooth nameplate following with minimal visual lag, even on fast-moving entities

### 2. Fixed Chunk Serialization Crash
**Problem:** When spawning and killing large numbers of entities (e.g., 100 skeletons), the server would crash with `Invalid entity reference!` errors during chunk saving.

**Root Cause:** Chunk serialization would attempt to save anchor entities that were queued for removal via `CommandBuffer.removeEntity()`. The anchor ref became invalid during serialization, causing the crash.

**Solution:** Implemented a **deferred removal strategy**:
- `removeAnchor()` adds anchors to a `pendingRemovals` list instead of removing immediately
- `cleanupPendingRemovals()` runs at the *start* of each tick, before chunk saving
- Actual removal happens via `CommandBuffer.removeEntity()` only when refs are confirmed valid
- Race condition eliminated: anchors are fully removed before chunk saver accesses them

### 3. Fixed Orphaned Anchor Cleanup
**Problem:** Anchors would persist after their parent entities were removed (e.g., via `/npc clean`), leaving invisible entities in the world.

**Solution:** Added `cleanupOrphanedAnchors()` method:
- Detects when real entity refs become invalid (`!realRef.isValid()`)
- Schedules anchors for deferred removal
- Runs every tick to catch entities removed by external commands

### 4. Fixed Anchor Disk Persistence
**Problem:** Anchor entities were being saved to chunk files and reloaded on server restart, causing duplicate invisible entities.

**Solution:** Changed anchor spawning to use `AddReason.LOAD` instead of `AddReason.SPAWN`:
- Marks anchors as runtime-only entities
- Prevents chunk saver from persisting them to disk
- Anchors are purely transient and recreated as needed

## Architecture Changes

### Player vs NPC Nameplate Rendering

**Players:**
- Always use vanilla client-side nameplate rendering
- Never create anchor entities, regardless of offset configuration
- Result: Zero lag, perfect synchronization with player movement
- Offset configuration still works (handled client-side by Hytale engine)

**NPCs:**
- Use invisible anchor entities when vertical offset is configured
- Anchors positioned with velocity-based prediction
- Smooth hologram-style rendering with minimal lag
- Anchors cleaned up on death or when offset returns to zero

### Technical Improvements

**AnchorEntityManager enhancements:**
- Added velocity tracking fields (`lastPosition`, `velocity`) to `AnchorState`
- Implemented predictive positioning in `repositionAnchor()`
- Added `pendingRemovals` list for deferred cleanup
- Added `cleanupPendingRemovals()` for safe anchor removal
- Added `cleanupOrphanedAnchors()` for detecting invalid refs
- Changed anchor `ProjectileComponent` model ID to empty string (no visual artifacts)
- Enhanced javadoc comments explaining the deferred removal pattern

**NameplateAggregatorSystem enhancements:**
- Added player detection to skip anchor creation for players
- Removed direct anchor removal during death cleanup
- Death cleanup now only blanks nameplates and removes `NameplateData` component
- Anchors are cleaned up via `cleanupOrphanedAnchors()` on next tick

## Upgrade Instructions

### For Server Administrators
1. Download `NameplateBuilder-1.0.1.jar`
2. Replace the old jar in `UserData/Mods` folder
3. Restart the server
4. No configuration changes required — all player preferences and admin settings are preserved

### For Mod Developers
- No changes required — API version 1.0.0 is still current
- Existing mods will work without modification
- Example mod remains at 1.0.0 (unchanged reference implementation)
- If you were experiencing lag with nameplate offsets, players will automatically benefit from the velocity prediction improvements

## Testing Performed

- Spawn 100 NPCs with nameplates, kill all — no crashes
- Fast-moving entities (leap attacks) — minimal lag
- Entity removal via `/npc clean` — anchors cleaned up properly
- Server restart — no orphaned anchors loaded from disk
- Death cleanup — nameplates disappear, no lingering text
- Chunk saving — no serialization errors

## Known Issues

None at this time.