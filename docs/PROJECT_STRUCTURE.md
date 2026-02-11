# Project Structure

Architecture, file tree, and internal systems overview for contributors.

> **Looking for the main README?** See [README.md](../README.md) for an overview, features, and screenshots.

> **Looking for the modding API?** See [Modding Guide](MODDING_GUIDE.md) for code examples and API reference.

## File Tree

```
NameplateBuilder/
  nameplate-api/
    src/main/java/.../api/
      NameplateAPI.java                   Static entry point
      NameplateData.java                  ECS component (Map<String, String>)
      SegmentTarget.java                  Entity target enum (ALL, PLAYERS, NPCS)
      INameplateRegistry.java             Internal registry interface
      NameplateException.java             Base exception
      NameplateNotInitializedException.java
      NameplateArgumentException.java

  nameplate-server/
    src/main/java/.../server/
      NameplateBuilderPlugin.java         Server plugin entry point
      NameplateAggregatorSystem.java      Per-tick nameplate compositor + death cleanup
      DefaultSegmentSystem.java           Built-in segments (Player Name, Health, Stamina, Mana)
      NameplateRegistry.java              Segment metadata store (with variant support)
      NameplateBuilderPage.java           Player UI page (sidebar, editor, format popup)
      NameplateBuilderCommand.java        /npb command with admin permission check
      NameplatePreferenceStore.java       Per-player preference persistence
      AdminConfigStore.java               Server-wide admin config (required, disabled, server name)
      AnchorEntityManager.java            Invisible anchor entity lifecycle
      SegmentKey.java                     record(pluginId, segmentId)
      ActiveTab.java                      Sidebar tab enum (General, NPCs, Players, Admin, Disabled)
      AdminSubTab.java                    Admin sub-tab enum (Required, Disabled, Settings)
      UiState.java                        UI state snapshot record for cross-reopen persistence
      SettingsData.java                   Codec data class for client ↔ server UI events
      SegmentView.java                    Immutable segment metadata view record for UI rendering
    src/main/resources/
      Common/UI/Custom/Pages/
        NameplateBuilder_Editor.ui        UI layout definition

  nameplate-example-mod/
    src/main/java/.../example/
      NameplateExamplePlugin.java         Example: describe + register systems
      ArchaeopteryxNameplateSystem.java   Example: NPC on-spawn + live health
      LifetimeNameplateSystem.java        Example: per-entity tick-based updates
```

## How It Works

1. **Registration** — Mods call `NameplateAPI.describe()` during `setup()` to register UI metadata (display name, target hint, example text), and `NameplateAPI.register()` at runtime to set per-entity text via the `NameplateData` ECS component.

2. **NPC initialization** — An `EntityTickingSystem` checks for newly spawned NPCs that don't yet have `NameplateData`. On their first tick, the system seeds default segments by adding a `NameplateData` component via the `CommandBuffer`. The aggregator picks up any visible entity that has `NameplateData` — no native `Nameplate` component is needed. Subsequent ticks skip already-initialized entities.

3. **Aggregation** — The `NameplateAggregatorSystem` ticks every frame. For each visible entity with a `NameplateData` component, it resolves segment keys from the component (skipping hidden `_`-prefixed keys and admin-disabled segments), applies the viewer's preferences (ordering, enabled/disabled, format variants, prefix/suffix wrapping, bar fill replacement, per-block separators), enforces admin-required segments, composites the text, and queues a nameplate update to each viewer. If all segments are disabled, it shows a hint message instead of the raw entity ID.

4. **Format variant resolution** — When a viewer has selected a non-default variant (index > 0) for a segment, the aggregator looks up the suffixed key (e.g. `"health.2"` for variant 2). If found, that text is used; otherwise it falls back to the base key. After variant resolution, bar placeholder characters are replaced with the viewer's custom empty fill, and prefix/suffix wrapping is applied.

5. **Required segment enforcement** — Segments moved to the "Required" column by an admin always display in the aggregated output, regardless of the viewer's personal preferences. In the editor, required segments appear with a yellow-tinted background and cannot be removed from the chain (but can still be reordered).

6. **Disabled segment enforcement** — Segments moved to the "Disabled" column by an admin are hidden globally: they are excluded from all player chains, filtered from available-block lists, and skipped by the aggregator. A segment cannot be both required and disabled simultaneously. When all registered segments are disabled, the aggregator blanks all nameplates and the join message switches to the red "disabled" variant.

7. **Death cleanup** — When an entity receives a `DeathComponent`, the aggregator sends an empty nameplate update to all viewers (clearing the displayed text immediately) and removes the `NameplateData` component so no further updates are produced. Without this, nameplates would linger through the death animation until the entity model despawns.

8. **View-cone filtering** — When enabled, the aggregator uses dot-product math to check if the viewer is looking at the entity (within a ~25 degree half-angle cone at up to 30 blocks range). Entities outside the cone receive an empty nameplate update to prevent the default ID from bleeding through.

9. **Anchor entities** — When a player configures a vertical offset, an invisible "anchor" entity (a bare `ProjectileComponent` with `Intangible` and `NetworkId`) is spawned above the real entity. The aggregator routes nameplate text to the anchor instead of the real entity, creating a hologram-style offset effect. Anchors follow the real entity every tick and are automatically cleaned up on death or when the offset returns to zero.

10. **Welcome message** — When a player joins (via `PlayerReadyEvent`), a coloured message is sent: green if any segments are available, red if all segments are admin-disabled. The message includes the configured server name (or "NameplateBuilder" as default). Players can disable the message via the General settings tab.

11. **Preferences** — Each player's segment chain, ordering, per-block separators, format variant selections, prefix/suffix text, bar empty-fill character, offset, welcome message toggle, and settings are stored by `NameplatePreferenceStore` and persisted to disk. Admin configuration (required segments, disabled segments, server name) is stored separately by `AdminConfigStore`.

## Persistence Formats

### Player Preferences (`preferences.txt`)

One line per record, pipe-delimited:

| Prefix | Format | Description |
|--------|--------|-------------|
| `N` | `N\|uuid\|pluginId:segmentId,...` | NPC segment chain order |
| `P` | `P\|uuid\|pluginId:segmentId,...` | Player segment chain order |
| `E` | `E\|uuid\|true/false` | Master enable toggle |
| `L` | `L\|uuid\|true/false` | Look-at (view-cone) toggle |
| `O` | `O\|uuid\|0.5` | Vertical offset value |
| `S` | `S\|uuid\|pluginId:segmentId\|separator` | Per-block separator |
| `V` | `V\|uuid\|pluginId:segmentId\|variantIndex` | Selected format variant |
| `X` | `X\|uuid\|pluginId:segmentId\|prefix` | Prefix text |
| `U` | `U\|uuid\|pluginId:segmentId\|suffix` | Suffix text |
| `B` | `B\|uuid\|pluginId:segmentId\|char` | Bar empty-fill character |
| `W` | `W\|uuid\|true/false` | Welcome message toggle |

### Admin Config (`admin_config.txt`)

| Prefix | Format | Description |
|--------|--------|-------------|
| `S` | `S\|serverName` | Server display name for welcome message |
| `R` | `R\|pluginId\|segmentId` | Required segment |
| `D` | `D\|pluginId\|segmentId` | Disabled segment |

Lines starting with `#` are comments. Blank lines are ignored.

## Building

**Requirements:**
- Java 25
- Gradle 9.2+
- Hytale Server build-7 jar installed at the default location

```bash
./gradlew build
```

This compiles all three modules, packages shadow JARs, and deploys the server plugin and example mod to your Hytale `UserData/Mods` folder.

To build individual modules:

```bash
./gradlew :nameplate-api:build
./gradlew :nameplate-server:build
./gradlew :nameplate-example-mod:build
```
