# Changelog

All notable changes to NameplateBuilder Server will be documented in this file.

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
- Segments map passed as parameter to buildText, eliminating redundant registry lookups
- LinkedHashSet for O(1) contains checks in the aggregator
- Removed per-tick PlaceholderAPI string scanning and cache eviction

## [4.260326.1] - 2026-03-30

### Added
- **Blacklist filter** - Filter the blacklist by name to quickly find specific NPCs
- **Add All Filtered** - Add all NPCs matching the current filter to the blacklist at once from the NPC picker
- **Remove Filtered** - Remove all blacklisted NPCs matching the current filter
- **Clear Blacklist** - Remove all blacklisted NPCs at once
- **Blacklist now shows 18 entries per page** - Up from 8, with compact row spacing

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

## [3.260219.0] - 2026-02-20

### Changed
- Updated server version

## [3.260218.0] - 2026-02-19

### Changed
- Adopted new versioning scheme: `{hytale_update}.{YYMMDD}.{mod_build}`
- Updated server version

## [1.0.3] - 2026-02-18

### Added
- Admin setting to enable or disable welcome messages for all players

## [1.0.2] - 2026-02-17

### Fixed
- Nameplate text remaining visible above entities after they are removed
- Crash when moving quickly with a health nameplate visible
- Nameplate offset anchor not recovering after being invalidated by the engine

### Changed
- Updated target server version to 2026.02.17-255364b8e
- Updated nameplate protocol to match the latest Hytale build

## [1.0.1] - 2026-02-13

### Fixed
- Nameplate lagging behind fast-moving NPCs
- Crash when an entity dies during chunk saving
- Nameplate remaining visible after an entity is cleaned up
- Nameplate offset anchors being incorrectly saved to disk

### Changed
- Player nameplates now use client-side rendering for smoother display
- NPC nameplates with vertical offset now use predictive positioning to reduce lag

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
