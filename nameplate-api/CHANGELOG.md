# Changelog

All notable changes to NameplateBuilder API will be documented in this file.

## [2.0.1] - 2026-04-05

### Changed
- `NameplateData.getEntriesDirect()` added for internal iteration without `UnmodifiableMap` wrapper overhead

### Fixed
- `NameplateData` unescape order causing data corruption on round-trip serialization for text containing backslash sequences

## [2.0.0] - 2026-04-01

### Breaking Changes
- `describe()` renamed to `define()` and now returns `SegmentBuilder`
- `describeVariants()` renamed to `defineVariants()`
- `undescribe()` renamed to `undefine()`
- `register()` renamed to `setText()`
- `remove()` renamed to `clearText()`

### Added
- **Resolver pattern** - Mods can register a `SegmentResolver` function that computes segment text per entity, replacing the need for manual tick systems
- `SegmentResolver` functional interface with `resolve(Store, Ref, int variantIndex)` signature
- `SegmentBuilder` interface with `resolver()`, `requires()`, and `cacheTicks()` methods
- `define()` returns `SegmentBuilder` for fluent configuration of resolvers and optimization hints

---

## [1.0.0] - 2026-02-11

### Added
- Initial release of NameplateBuilder API
- `NameplateAPI` static entry point for mod developers
- `NameplateData` ECS component for storing segment text
- `SegmentTarget` enum (ALL, PLAYERS, NPCS) for entity targeting
- Format variant system with multiple display formats per segment
- Prefix/suffix wrapping support
- Bar customization with empty-fill character
