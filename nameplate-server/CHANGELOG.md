# Changelog

All notable changes to NameplateBuilder Server will be documented in this file.

## [4.260326.7] - 2026-04-10

### New for Mod Developers
- **Overridable segments** - Mods can now provide context-specific names for entities. For example, a pet naming mod can make "Entity Name" show your pet's custom name instead of the NPC type. See the API changelog and docs for details
- Entity Name and Player Name can be overridden by other mods. Health, Stamina, and Mana cannot since these are "factual" values

### UI
- When a segment has overrides from other mods, an "Overrides" button appears on the chain block showing which mods provide values
- The admin chain editor now shows a warning when the chain is not locked, reminding that changes won't apply to players until locked

### Improved
- NPC names are now cleaned up: internal suffixes like Patrol, Wander, Guard, Idle are stripped (e.g. "Skeleton Fighter Wander" now shows as "Skeleton Fighter")

### Bug Fixes
- Fixed a crash when rapidly adjusting the vertical offset slider

---

## [4.260326.6] - 2026-04-08

### UI Improvements
- Segments in the chain can now be reordered by dragging and dropping
- Vertical offset now uses a slider instead of a text input
- Tooltips added to all buttons and controls
- Scrollbars updated with a gold tint for better visibility
- Save button is now greyed out when there are no unsaved changes
- Available, disabled, and blacklist sections now show more items at once
- Plugin version is now displayed in the UI title bar
- Fixed a typo in the separator editor

### Storage Improvements
- Player preferences and admin configuration now use JSON format
- Each player's preferences are stored in a separate file for better reliability
- Old `.txt` preferences are automatically migrated on first load
- Segment references are now namespace-safe, preventing conflicts between mods

### Performance
- Numerous optimizations across versions .2 through .6 including array-based lookups, per-archetype caching, and entity skip logic
- Use `/npbbench <players> <seconds>` to benchmark nameplate performance on your hardware (e.g. `/npbbench 50 5` simulates 50 players for 5 seconds)

### New API Features
- Mods can now mark up to 3 segments per mod as enabled by default for new players
- Default segments can target NPC chains, Player chains, or both

### Admin Features
- Regex pattern blacklist for bulk NPC exclusion (e.g. `Citizen.*` blocks all Citizen NPCs)
- Default blacklist patterns for known non-combat NPCs (Citizen, Mount, Pet) added on first install
- Admins can now edit locked chains directly while players see a read-only view
- Locked chains show "Chain is locked by the server admin" tooltip for players

### Gameplay
- Player nameplates are now hidden when crouching (like Minecraft)
- "Only Show NPC Nameplates When Looking" is now enabled by default for new players
- NPC nameplate view range reduced from 30 to 12 blocks
- Player nameplates are always visible regardless of look direction
- Removed the "Type /npb to customize" hint text from entities with no configured segments

### Bug Fixes
- Fixed wrong segment being added when clicking Add on page 2 or later
- Fixed the Enable Nameplates toggle in General settings not disabling nameplates in-game
- Fixed namespace toggle in chain settings not taking effect immediately
- Fixed a crash that could occur when entities were removed during nameplate processing
- New players now start with only Entity Name and Health in their chain by default

---

## [4.260326.5] - 2026-04-04

### Performance
- Entities with no nearby players are now skipped entirely, reducing work by ~85% on servers with many loaded entities
- Mod discovery for the admin UI now runs once per segment instead of every tick for every entity
- Replaced internal stream operations with simple loops to reduce per-tick allocations

### Fixed
- Dead entities now correctly clean up their nameplate data even when no player is nearby
- Anchor entities are now removed when all players leave the area

---

## [4.260326.4] - 2026-04-03

### Fixed
- Crash caused by `segments.hashCode()` triggering NPE on Hytale's proxied `ComponentType` objects in the cached comparator optimization
- Potential NPE in view-cone check when `HeadRotation.getDirection()` returns null
- `NameplateData` serialization corrupting text containing backslash-e or backslash-c sequences due to incorrect unescape order
- Resolver exceptions now catch `Exception` instead of `Throwable` to avoid silently swallowing fatal errors like `OutOfMemoryError`
- Debug flag visibility across threads (`volatile`)
- Formatting issues in admin world/instance toggle handlers

---

## [4.260326.3] - 2026-04-03

### Performance
- **~60-80% reduction in CPU usage** across 13 optimizations targeting the aggregator and stat systems
- Nameplate updates for dead entities, disabled chains, and filtered entities no longer allocate objects - they reuse a single cached update
- When an admin locks the chain order, nameplate text is computed once per entity instead of once per viewer. On a server with 100 players, this eliminates 99% of the text building work
- Entity type detection (used for namespace filtering) now uses a cache instead of scanning component types with reflection every tick
- World lookups reduced from up to 5 per entity to 1 per entity
- Built-in stats (health, stamina, mana) are only updated when the actual values change. Entities with stable HP skip all string building entirely
- Bar display strings (e.g. `||||||------`) are pre-computed once instead of rebuilt every tick for every entity
- View-cone check ("only show when looking") uses optimized math without allocating intermediate objects
- Orphan anchor cleanup now runs once per tick cycle instead of once per entity

### Fixed
- NPC picker selection with a filter applied now selects the correct NPC

### Changed
- NPC picker buttons (Cancel, Add All Filtered, Add to Blacklist) now fit within the popup bounds
- Filter fields use placeholder text with search icon instead of separate labels
- Pagination labels show current/total format (e.g. `8/24`) across all pages
- Button spacing normalized to 16px from edges across all pages

---

## [4.260326.2] - 2026-04-01

### Added
- **Resolver pattern** - Mods can now register resolver functions that compute segment values per entity, replacing the need for manual tick systems
- Built-in segments (health, stamina, mana, entity name, player name) now use resolvers with archetype-based optimization
- **Horizontal scroll chain** - Chain blocks now scroll horizontally instead of using pagination, supporting up to 10 visible blocks
- **Preview bar rework** - Up to 8 preview targets in a scrollable container with Clear All button inline
- **Chain/Settings sub-tabs** - 2-state nav button pattern for switching between Chain and Settings views
- **UI separators** - Visual separators between chain/preview and preview/search sections

### Changed
- API methods renamed for clarity: `define()`, `defineVariants()`, `undefine()`, `setText()`, `clearText()`
- Requires NameplateBuilder API v2.0.0
- Default chain simplified to only name segment + health
- Player tab hides preview target buttons since players only see their own nameplate
- Player tab preview shows all segments unfiltered
- Save button spacing equalized (right and bottom margin) across all pages

### Fixed
- NPC picker typing no longer loses focus after one character
- Built-in segments now respect namespace killswitch
- Vanilla NPCs get nameplates correctly regardless of entity type ID

### Removed
- **PlaceholderAPI support** - Resolves per-viewer, not per-entity, making it unsuitable for nameplates. See Placeholder-API page on the docs for more info.

### Performance
- Nameplate processing is now **~30-40%** faster due to reduced internal lookups and removal of per-tick string scanning overhead

---

## [4.260326.1] - 2026-03-30

### Added
- **Blacklist filter** - Filter the blacklist by name to quickly find specific NPCs
- **Add All Filtered** - Add all NPCs matching the current filter to the blacklist at once from the NPC picker
- **Remove Filtered** - Remove all blacklisted NPCs matching the current filter
- **Clear Blacklist** - Remove all blacklisted NPCs at once
- **Blacklist now shows 18 entries per page** - Up from 8, with compact row spacing

---

## [4.260326.0] - 2026-03-30

### Added
- **NPC entity name segment** - NPCs now display their name (e.g. "Archaeopteryx") as a built-in segment
- **Admin killswitches** - Admins can now disable nameplates server-wide, per chain type (NPC/Player), per mod, and per world or instance
- **Admin chain locking** - Admins can lock the segment order so all players see the same chain
- **Admin world/instance controls** - New two-column section in Admin Settings to enable or disable nameplates per world and instance
- **Admin NPC blacklist** - Prevent specific NPC types from ever showing nameplates. Searchable popup with filter and pagination
- **Per-world/instance settings for players** - Players can now toggle nameplates on or off for individual worlds and instances
- **Chain and Settings sub-tabs** - NPC and Player pages now have a Chain tab for ordering segments and a Settings tab for toggles, mod controls, and world settings
- **Per-mod ON/OFF toggles** - Enable or disable all segments from a specific mod, including NameplateBuilder's own built-in segments
- **Multi-profile preview** - The preview bar now shows how nameplates look on different entity types (e.g. vanilla vs RPG Mobs NPCs)
- **PlaceholderAPI support** - Segment text containing placeholders is now parsed per viewer
- **Admin override indicators** - When an admin disables a feature, players see "(Disabled by Admin)" and cannot re-enable it

### Fixed
- World crash when chunks unload while anchor entities are active
- Nameplates from other mods being overwritten by NameplateBuilder on non-vanilla NPCs

---

## [3.260219.0] - 2026-02-20

### Changed
- Updated server version

---

## [3.260218.0] - 2026-02-19

### Changed
- Adopted new versioning scheme: `{hytale_update}.{YYMMDD}.{mod_build}`
- Updated server version

---

## [1.0.3] - 2026-02-18

### Added
- Admin setting to enable or disable welcome messages for all players

---

## [1.0.2] - 2026-02-17

### Fixed
- Nameplate text remaining visible above entities after they are removed
- Crash when moving quickly with a health nameplate visible
- Nameplate offset anchor not recovering after being invalidated by the engine

### Changed
- Updated target server version to 2026.02.17-255364b8e
- Updated nameplate protocol to match the latest Hytale build

---

## [1.0.1] - 2026-02-13

### Fixed
- Nameplate lagging behind fast-moving NPCs
- Crash when an entity dies during chunk saving
- Nameplate remaining visible after an entity is cleaned up
- Nameplate offset anchors being incorrectly saved to disk

### Changed
- Player nameplates now use client-side rendering for smoother display
- NPC nameplates with vertical offset now use predictive positioning to reduce lag

---

## [1.0.0] - 2026-02-11

### Added
- Initial release of NameplateBuilder server plugin
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
