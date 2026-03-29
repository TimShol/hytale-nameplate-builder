# Changelog

All notable changes to NameplateBuilder Server will be documented in this file.

## [4.260326.0] - 2026-03-29

### Added
- **One-chain model** - Single NPC chain and single Player chain per player, replacing per-namespace chains. Full cross-mod segment interleaving
- **Opt-in NPC model** - Only Hytale-namespace NPCs are auto-seeded. Other mods call `NameplateAPI.register()` to opt in. Prevents overwriting other mods' nameplates
- **Built-in entity-name segment** - `entity-name` segment for NPCs, resolved from entity type ID (e.g. "Archaeopteryx")
- **Admin killswitches** - Master enable, per-chain enable (NPC/Player), per-namespace mod killswitch, per-world/instance killswitch
- **Admin chain locking** - Lock NPC and/or Player chain order so all players see the admin's configured order (read-only)
- **Admin world/instance killswitches** - Two-column layout in Admin Settings with independent pagination for worlds and instances
- **Admin NPC blacklist** - Blacklist specific NPC types from ever receiving nameplates. NPC picker popup with filter, pagination, and selection
- **Per-world/instance enable** - Players can toggle nameplates per world and per instance. Admin overrides take precedence
- **Chain/Settings sub-tabs** - Horizontal sub-tabs on NPC/Player editor pages for chain editing and per-chain settings
- **Mod killswitches in Settings tab** - ON/OFF toggles per mod namespace with display names from plugin manifests
- **Multi-profile preview** - Preview bar shows different entity profiles auto-generated from runtime observation
- **Runtime entity-type tracking** - Tracks which mods contribute segments to which entity types
- **PlaceholderAPI consumer integration** - Soft dependency on PlaceholderAPI. Parses `%placeholder%` tokens in segment text with 500ms TTL cache
- **Admin override indicators** - All admin-disabled features show "(Disabled by Admin)" to players with blocked toggles
- **NPC picker popup** - Modal overlay with transparent backdrop, filter search, pagination, Cancel/Add buttons (Cancel left, Add right)
- **Scrollable admin settings** - Admin Settings tab uses TopScrolling with styled scrollbar

### Changed
- **Master toggle now clears nameplates** - Disabling the master toggle actively sends empty updates to clear existing nameplates, instead of silently stopping updates
- **Consistent top padding** - All content areas have uniform top margin for visual consistency
- **Sub-tab bars** - Removed dark background from sub-tab bars, tabs stretch properly across content area
- **Save message system** - All save buttons show success/error feedback. Each tab targets its own save message element
- **Code quality** - Replaced all abbreviated variable names (`idx`, `ns`, `wn`, `in`, `vi`, `seg`, `pfx`, `sfx`) with full descriptive names

### Fixed
- NPC picker filter typing stops after one character (visibility toggle was resetting text field focus)
- NPC picker row backgrounds changing on hover (now uses dialog background color)
- Blacklist save message targeting hidden element in wrong content section
- Chain enable toggle not checking admin killswitch state in handler
- Enable Nameplates toggle not checking master killswitch state in handler
- Pagination buttons stretched too far apart in two-column layouts (fixed-width pagination groups)

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
