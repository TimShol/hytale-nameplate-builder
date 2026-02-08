# NameplateBuilder

A server-side nameplate aggregator for Hytale that lets multiple mods contribute text segments to entity nameplates, with a built-in player UI for customization.

### Downloads

[![CurseForge Server Plugin](https://img.shields.io/badge/Server_Plugin-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/PLACEHOLDER_SERVER_SLUG)
[![CurseForge API](https://img.shields.io/badge/API_(for_mod_devs)-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/PLACEHOLDER_API_SLUG)
[![GitHub Example Mod](https://img.shields.io/badge/Example_Mod-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/PLACEHOLDER_USER/NameplateBuilder/releases/latest)

<!-- TODO: Replace PLACEHOLDER_SERVER_SLUG, PLACEHOLDER_API_SLUG, and PLACEHOLDER_USER with actual values -->

![Editor UI overview](docs/screenshots/editor-overview.png)
<!-- SCREENSHOT: Full editor window with several blocks in the chain, some in available, and preview visible -->

## Overview

NameplateBuilder solves a core problem in modded Hytale servers: when multiple mods want to display information above entities (health, guild tags, titles, ranks, etc.), they conflict over the single `Nameplate` component. NameplateBuilder acts as a central aggregator — each mod registers its own named segments, and the system composites them into a single nameplate string per viewer, per entity, every tick.

Players get a UI to choose which segments they see, reorder them, customize separators between individual segments, configure a vertical nameplate offset, and toggle a "only show when looking at entity" mode. Nameplates are automatically cleared when an entity dies.

## Features

- **Multi-mod nameplate aggregation** — Any number of mods can register their own named segments (health, guild tag, tier, title, etc.) and NameplateBuilder composites them into a single nameplate per entity
- **Per-player customization UI** — Players open `/npb` to browse, search, add, remove, and reorder nameplate segments from all installed mods
- **Per-block separators** — Each segment in the chain can have its own separator to the next segment (or no separator at all), allowing fine-grained control over how segments are joined
- **Nameplate offset** — Configurable vertical offset value per player, stored and ready for hologram-based rendering (accepts both comma and dot decimal separators)
- **View-cone filtering** — Optional "only show when looking at" mode hides nameplates for entities outside a ~25° view cone at up to 30 blocks range
- **Death cleanup** — Nameplates are automatically cleared when an entity dies, instead of lingering through the death animation
- **Live preview** — The UI shows a real-time preview of the composited nameplate text with the player's current segment chain and separators
- **Segment target hints** — Mods tag segments as `[All]`, `[Players]`, or `[NPCs]` so players know at a glance which segments are relevant
- **Hidden metadata keys** — Keys prefixed with `_` are stored in the component but never displayed, useful for per-entity internal state
- **Persistent preferences** — All player settings (chain order, separators, offset, toggles) are saved to disk and survive server restarts
- **Safe concurrent initialization** — Uses `putComponent()` (upsert) pattern to avoid race conditions when multiple mods initialize the same entity

## Architecture

```
nameplate-api/            Public API jar — compile against this
nameplate-server/         Server plugin — runs the aggregator, UI, and preferences
nameplate-example-mod/    Example mod demonstrating the API
```

**nameplate-api** exposes `NameplateAPI`, `NameplateData`, `SegmentTarget`, and the exception types. Mods depend on this at compile time.

**nameplate-server** registers the `NameplateData` ECS component, runs the `NameplateAggregatorSystem` tick system (including death cleanup), manages the player UI page, and persists per-player preferences (chain order, per-block separators, offset, toggles).

**nameplate-example-mod** shows the full modder workflow: describing segments, attaching nameplates to NPCs on spawn, and updating segments every tick.

## Quick Start

### 1. Add the dependency

In your mod's `manifest.json`:

```json
{
  "Dependencies": {
    "Frotty27:NameplateBuilder": "*"
  }
}
```

In your `build.gradle`:

```groovy
dependencies {
    compileOnly files('path/to/NameplateBuilder-API-1.0.0.jar')
}
```

### 2. Describe your segments (optional)

In your plugin's `setup()`, give segments a human-readable name for the player UI:

```java
@Override
protected void setup() {
    NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL);
    NameplateAPI.describe(this, "guild",  "Guild Tag",  SegmentTarget.ALL);
    NameplateAPI.describe(this, "tier",   "Elite Tier", SegmentTarget.NPCS);
    NameplateAPI.describe(this, "status", "Online Status", SegmentTarget.PLAYERS);
}
```

The `SegmentTarget` enum tells the UI which entity types a segment is intended for. The UI shows this as a tag next to the author (e.g. `by Frotty27 [NPCs]`), so players know at a glance which segments are relevant. Available targets:

| Target | Tag | Meaning |
|--------|-----|---------|
| `SegmentTarget.ALL` | `[All]` | Applies to all entities |
| `SegmentTarget.PLAYERS` | `[Players]` | Intended for player entities |
| `SegmentTarget.NPCS` | `[NPCs]` | Intended for NPC entities |

This is purely a UI hint — it does not enforce restrictions at runtime. The no-target overload `describe(plugin, id, name)` defaults to `ALL`.

This step is optional. Undescribed segments still work — they just show the raw segment ID in the UI instead of a display name.

### 3. Register text on entities

At runtime, set nameplate text on any entity:

```java
// Set text (creates the component automatically if needed)
NameplateAPI.register(store, entityRef, "health", "67/67");
NameplateAPI.register(store, entityRef, "guild", "[Warriors]");

// Update — just call register() again, no remove needed
NameplateAPI.register(store, entityRef, "health", "23/67");

// Remove a single segment
NameplateAPI.remove(store, entityRef, "health");

// Remove all nameplate data from an entity
store.tryRemoveComponent(entityRef, NameplateAPI.getComponentType());
```

### 4. Attach nameplates to NPCs on spawn

The recommended way to initialize nameplates on NPCs is with an `EntityTickingSystem` that detects newly spawned entities. The system queries for entities that have the NPC component, checks the role name, and seeds nameplate data on the first tick:

```java
final class MyNpcNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String ROLE_NAME = "Archaeopteryx"; // or any NPC role

    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    MyNpcNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.npcType = NPCEntity.getComponentType();
        this.nameplateDataType = nameplateDataType;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(npcType);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npc = chunk.getComponent(index, npcType);
        if (npc == null || !ROLE_NAME.equals(npc.getRoleName())) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        // Skip if already initialized (reads are safe from tick systems)
        if (store.getComponent(entityRef, nameplateDataType) != null) {
            return;
        }

        // Build the component and add it via the CommandBuffer.
        // store.addComponent() would throw — the store is locked during tick.
        NameplateData data = new NameplateData();
        data.setText("health", "67/67");
        data.setText("title", "The Brave");
        commandBuffer.putComponent(entityRef, nameplateDataType, data);
    }
}
```

> **Why `putComponent`?** Multiple mods or systems may race to initialize the same entity. `addComponent()` throws `IllegalArgumentException` if the component already exists, while `putComponent()` is an upsert (add-or-replace), making it safe against race conditions between systems sharing the same `CommandBuffer` tick.

Register the system in your plugin's `setup()`:

```java
@Override
protected void setup() {
    NameplateAPI.describe(this, "health", "Health Bar");
    NameplateAPI.describe(this, "title", "Player Title");

    ComponentType<EntityStore, NameplateData> type = NameplateAPI.getComponentType();
    getEntityStoreRegistry().registerSystem(new MyNpcNameplateSystem(type));
}
```

**How it works:** The system queries all entities with an `NPCEntity` component. On each tick, it checks the role name and whether the entity already has `NameplateData`. If not, it builds a `NameplateData` component with the default segment values and adds it via `commandBuffer.putComponent()`, which defers the write until after the system finishes. On subsequent ticks the entity already has data, so the check returns early — making this a one-shot initializer.

> **Why CommandBuffer?** Inside an `EntityTickingSystem`, the `Store` is locked for writes. Calling `store.addComponent()` (or `NameplateAPI.register()` on an entity without data) throws `IllegalStateException`. All structural changes must go through the `CommandBuffer`. Reading via `store.getComponent()` is safe. Mutating an existing component in place (e.g. `data.setText()`) is also safe.

See `ArchaeopteryxNameplateSystem` in the example mod for the full working implementation.

### 5. Tick-based updates

The `NameplateData` component persists on the entity. Calling `register()` every tick is safe — it updates the internal map value in place without adding/removing the component, so there is no flashing.

```java
// Inside an EntityTickingSystem — runs every tick
NameplateData data = chunk.getComponent(index, nameplateDataType);
if (data != null) {
    int seconds = tickCounter / 20;
    data.setText("lifetime", seconds + "s");
}
```

See `LifetimeNameplateSystem` in the example mod for a full working implementation.

### 6. Hidden metadata keys

Keys prefixed with `_` are treated as hidden metadata and are **never** shown in the nameplate output or the player UI. Use them to store per-entity internal state alongside visible segments:

```java
// Store a spawn tick for computing per-entity lifetime
data.setText("_spawn_tick", String.valueOf(globalTick));

// Read it back — not visible to players
String spawnTick = data.getText("_spawn_tick");
```

See `LifetimeNameplateSystem` for an example that uses `_lifetime_tick` to track per-entity lifetime independently.

## API Reference

### `NameplateAPI`

| Method | Description |
|--------|-------------|
| `describe(plugin, segmentId, displayName)` | Register UI metadata (defaults to `SegmentTarget.ALL`) |
| `describe(plugin, segmentId, displayName, target)` | Register UI metadata with entity target hint |
| `undescribe(plugin, segmentId)` | Remove a segment description from the UI |
| `register(store, entityRef, segmentId, text)` | Set nameplate text on an entity |
| `remove(store, entityRef, segmentId)` | Remove a segment's text from an entity |
| `getComponentType()` | Get the `ComponentType` for advanced use cases |

### `SegmentTarget`

| Value | UI Tag | Description |
|-------|--------|-------------|
| `ALL` | `[All]` | Applies to all entity types |
| `PLAYERS` | `[Players]` | Intended for player entities |
| `NPCS` | `[NPCs]` | Intended for NPC entities |

### Exceptions

| Exception | When |
|-----------|------|
| `NameplateNotInitializedException` | API used before NameplateBuilder has loaded |
| `NameplateArgumentException` | Null or blank parameter passed to an API method |

Both extend `NameplateException` (a `RuntimeException`).

### `NameplateData`

The ECS component attached to entities. Most mods should use `NameplateAPI.register()` and `NameplateAPI.remove()` rather than interacting with this directly.

| Method | Description |
|--------|-------------|
| `setText(key, text)` | Set or update a segment's text |
| `getText(key)` | Get a segment's current text |
| `removeText(key)` | Remove a segment |
| `getEntries()` | Unmodifiable view of all entries |
| `isEmpty()` | Check if the component has no entries |

> **Convention:** Keys starting with `_` are hidden metadata — they are stored in the component but skipped by the aggregator and never displayed.

## Player UI

Players open the Nameplate Builder editor via the `/nameplatebuilder` command (alias: `/npb`). The UI lets them:

- **Browse** all registered segments from all mods, with `[All]`/`[Players]`/`[NPCs]` target tags
- **Search** and filter by name, author, mod, or target category
- **Build** their nameplate chain by adding/removing/reordering blocks
- **Preview** the composited nameplate text in real time (truncated with ellipsis if too long)
- **Per-block separators** — click the separator label between any two chain blocks to edit that specific separator. Each block can have its own separator (or none). A green highlight shows which separator is selected for editing. Click again to deselect, or click a different separator to switch. The default separator (`" - "`) is used for newly added blocks
- **Offset** — set a vertical offset for the nameplate position (accepts both `,` and `.` as decimal separators, clamped to -5.0 – 5.0)
- **Clear All** — remove all blocks from the chain in one click
- **Toggle** "only show when looking at entity" mode (view-cone filter)

When a player has no blocks enabled, entities managed by NameplateBuilder show "Type /npb to customize" as a hint instead of the raw entity ID.

Preferences are saved per player and persist across sessions.

### Screenshots

#### Editor with blocks in chain
![Editor with chain](docs/screenshots/editor-chain.png)
<!-- SCREENSHOT: Editor showing 2-4 blocks in the chain section, with separators visible between them, and more blocks in Available below -->

#### Editor with empty chain
![Editor empty](docs/screenshots/editor-empty.png)
<!-- SCREENSHOT: Editor with "No blocks added yet" in the chain section, all blocks in Available, showing the Clear All button in the settings row -->

#### Available blocks with target tags
![Available blocks](docs/screenshots/available-blocks.png)
<!-- SCREENSHOT: Close-up of the Available Blocks section showing several blocks with their [All], [Players], [NPCs] tags next to author names -->

#### Preview bar
![Preview](docs/screenshots/preview.png)
<!-- SCREENSHOT: Close-up of the Preview bar showing composited text like "Health Bar - Guild Tag - Elite Tier" -->

#### Settings rows
![Settings rows](docs/screenshots/settings-rows.png)
<!-- SCREENSHOT: Close-up of the two settings rows: Row 1 with look-at toggle, Clear All, OFFSET label and text field; Row 2 with SEPARATOR label, separator text field, and context label showing "(default for new blocks)" or "(after Block Name)" -->

#### In-world nameplate on NPC
![In-world nameplate](docs/screenshots/nameplate-ingame.png)
<!-- SCREENSHOT: An Archaeopteryx NPC in the game world with a nameplate above it showing something like "42/67 - [Elite] - Lv. 5 - [Warriors]" -->

#### In-world nameplate with custom selection
![Custom selection](docs/screenshots/nameplate-custom.png)
<!-- SCREENSHOT: An Archaeopteryx NPC showing a nameplate with only 1-2 segments selected (e.g. just "Health Bar") to demonstrate per-player customization -->

#### Empty nameplate hint
![Empty hint](docs/screenshots/nameplate-empty-hint.png)
<!-- SCREENSHOT: An entity (player or NPC) showing "Type /npb to customize" as nameplate text when no blocks are enabled -->

#### Search/filter
![Search filter](docs/screenshots/editor-search.png)
<!-- SCREENSHOT: Editor with a search term typed in the Search field, showing filtered results in Available Blocks -->

## How It Works

1. **Registration** — Mods call `NameplateAPI.describe()` during `setup()` to register UI metadata, and `NameplateAPI.register()` at runtime to set per-entity text via the `NameplateData` ECS component.

2. **NPC initialization** — An `EntityTickingSystem` checks for newly spawned NPCs that don't yet have `NameplateData`. On their first tick, the system seeds default segments by adding a `NameplateData` component via the `CommandBuffer`. The aggregator picks up any visible entity that has `NameplateData` — no native `Nameplate` component is needed. Subsequent ticks skip already-initialized entities.

3. **Aggregation** — The `NameplateAggregatorSystem` ticks every frame. For each visible entity with a `NameplateData` component, it reads the segment entries (skipping hidden `_`-prefixed keys), applies the viewer's preferences (ordering, enabled/disabled, per-block separators), composites the text, and queues a nameplate update to each viewer. If all segments are disabled, it shows a hint message instead of the raw entity ID.

4. **Death cleanup** — When an entity receives a `DeathComponent`, the aggregator sends an empty nameplate update to all viewers (clearing the displayed text immediately) and removes the `NameplateData` component so no further updates are produced. Without this, nameplates would linger through the death animation until the entity model despawns.

5. **View-cone filtering** — When enabled, the aggregator uses dot-product math to check if the viewer is looking at the entity (within a ~25 degree half-angle cone at up to 30 blocks range). Entities outside the cone receive an empty nameplate update to prevent the default ID from bleeding through.

6. **Preferences** — Each player's segment chain, ordering, per-block separators, offset, and settings are stored by `NameplatePreferenceStore` and persisted to disk. The UI stores preferences under a global wildcard (`*`), which the aggregator falls back to when no entity-type-specific preferences exist.

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

## Project Structure

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
      NameplateRegistry.java              Segment metadata store
      NameplateBuilderPage.java           Player UI page
      NameplateBuilderCommand.java        /npb and /nameplatebuilder command
      NameplatePreferenceStore.java       Per-player preference persistence
      SegmentKey.java                     record(pluginId, segmentId)
    src/main/resources/
      Common/UI/Custom/Pages/
        NameplateBuilder_Editor.ui        UI layout definition

  nameplate-example-mod/
    src/main/java/.../example/
      NameplateExamplePlugin.java         Example: describe + register systems
      ArchaeopteryxNameplateSystem.java   Example: NPC on-spawn + live health
      LifetimeNameplateSystem.java        Example: per-entity tick-based updates
```

## License

This project is licensed under the [MIT License](LICENSE).
