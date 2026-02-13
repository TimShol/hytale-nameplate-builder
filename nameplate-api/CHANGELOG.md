# Changelog

All notable changes to NameplateBuilder API will be documented in this file.

## [1.0.0] - 2025-02-11

### Added
- Initial release of NameplateBuilder API
- `NameplateAPI` static entry point for mod developers
- `NameplateAPI.describe()` method for registering segment metadata
- `NameplateAPI.register()` method for setting nameplate text on entities
- `NameplateAPI.unregister()` method for removing segments
- `NameplateData` ECS component for storing segment text (`Map<String, String>`)
- `SegmentTarget` enum (ALL, PLAYERS, NPCS) for entity targeting
- `INameplateRegistry` interface for internal registry access
- `NameplateException` base exception class
- `NameplateNotInitializedException` for API usage before initialization
- `NameplateArgumentException` for invalid segment keys or parameters
- Format variant system (register multiple display formats per segment)
- Prefix/suffix wrapping support
- Bar customization with empty-fill character
