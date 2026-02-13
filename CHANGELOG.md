# Changelog

All notable changes to NameplateBuilder will be documented in this file.

## [1.0.1] - 2025-02-13

### Fixed
- **Anchor entity lifecycle improvements** — Fixed crash when anchors were removed during chunk serialization by implementing a deferred removal strategy
- **Nameplate lag on moving entities** — Implemented velocity-based predictive positioning to compensate for the 1-tick CommandBuffer delay, significantly reducing visual lag on fast-moving NPCs
- **Chunk persistence issues** — Changed anchor spawning to use `AddReason.LOAD` instead of `AddReason.SPAWN` to prevent anchors from being persisted to disk during chunk saves
- **Orphaned anchor cleanup** — Added robust cleanup system to detect and remove anchors when their parent entities are removed (e.g., via `/npc clean`)

### Changed
- **Player nameplate rendering** — Player entities now always use vanilla client-side nameplate rendering and never create anchor entities, eliminating all lag for player nameplates regardless of offset configuration
- **NPC nameplate rendering** — NPC entities use invisible anchor entities with velocity prediction when vertical offset is configured, providing smooth hologram-style rendering
- **Anchor entity model** — Changed anchor `ProjectileComponent` to use empty model ID to prevent any visual artifacts

### Technical Details
- Anchor position updates now use `CommandBuffer.replaceComponent()` with velocity-based prediction (exponential moving average: 70% current velocity + 30% new velocity)
- Anchors are scheduled for removal via `pendingRemovals` list and cleaned up at the start of each tick before chunk saving
- Added `cleanupPendingRemovals()` and `cleanupOrphanedAnchors()` methods to `AnchorEntityManager`
- Enhanced javadoc comments across `AnchorEntityManager` and `NameplateAggregatorSystem`

## [1.0.0] - 2025-02-11

### Added
- Initial release of NameplateBuilder
- Multi-mod nameplate aggregation system
- Player customization UI (`/npb` command)
- Admin controls (required segments, disabled segments, server name)
- Built-in segments: Player Name, Health, Stamina, Mana
- Format variants with prefix/suffix wrapping
- View-cone filtering
- Nameplate offset with anchor entities
- Death cleanup
- Persistent preferences and admin config
- Welcome message on join
