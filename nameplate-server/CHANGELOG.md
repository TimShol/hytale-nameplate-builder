# Changelog

All notable changes to NameplateBuilder Server will be documented in this file.

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
