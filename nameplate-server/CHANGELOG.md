# Changelog

All notable changes to NameplateBuilder Server will be documented in this file.

## [1.0.1] - 2025-02-13

### Fixed
- Fixed nameplate lag on moving NPCs with velocity-based predictive positioning
- Fixed crash during chunk serialization when entities die
- Fixed orphaned anchor cleanup when entities are removed (e.g., via `/npc clean`)
- Fixed anchor entities being persisted to disk during chunk saves

### Changed
- Player nameplates now always use vanilla client-side rendering (zero lag, no anchor entities)
- NPC nameplates use invisible anchor entities with velocity prediction when vertical offset is configured
- Anchor entities now use empty model ID to prevent visual artifacts
- Improved anchor entity lifecycle management with deferred removal strategy

## [1.0.0] - 2025-02-11

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
