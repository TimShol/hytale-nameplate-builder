# NameplateBuilder

A server-side nameplate aggregator for Hytale that lets multiple mods contribute text segments to entity nameplates, with a built-in player UI for customization.

## Overview

NameplateBuilder solves a core problem in modded Hytale servers: when multiple mods want to display information above entities (health, guild tags, titles, ranks, etc.), they conflict over the single `Nameplate` component. NameplateBuilder acts as a central aggregator — each mod registers its own named segments, and the system composites them into a single nameplate string per viewer, per entity, every tick.

Players get a UI to choose which segments they see, reorder them, and toggle a "only show when looking at entity" mode.

## Architecture

```
nameplate-api/            Public API jar — compile against this
nameplate-server/         Server plugin — runs the aggregator, UI, and preferences
nameplate-example-mod/    Example mod demonstrating the API
```

**nameplate-api** exposes `NameplateAPI`, `NameplateData`, and the exception types. Mods depend on this at compile time.

**nameplate-server** registers the `NameplateData` ECS component, runs the `NameplateAggregatorSystem` tick system, manages the player UI page, and persists per-player preferences.

**nameplate-example-mod** shows the full modder workflow including a tick-based segment that updates every frame.

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
    NameplateAPI.describe(this, "health", "Health Bar");
    NameplateAPI.describe(this, "guild",  "Guild Tag");
    NameplateAPI.describe(this, "title",  "Player Title");
}
```

This is optional. Undescribed segments still work — they just show the raw segment ID in the UI instead of a display name.

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

### 4. Tick-based updates

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

## API Reference

### `NameplateAPI`

| Method | Description |
|--------|-------------|
| `describe(plugin, segmentId, displayName)` | Register UI metadata for a segment |
| `undescribe(plugin, segmentId)` | Remove a segment from the UI |
| `undescribeAll(plugin)` | Remove all of a plugin's segment descriptions |
| `register(store, entityRef, segmentId, text)` | Set nameplate text on an entity |
| `remove(store, entityRef, segmentId)` | Remove a segment's text from an entity |
| `getComponentType()` | Get the `ComponentType` for advanced use cases |

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
| `clear()` | Remove all entries |

## Player UI

Players open the Nameplate Builder editor via the `/nameplatebuilder` command. The UI lets them:

- **Browse** all registered segments from all mods
- **Search** and filter by name, author, or mod
- **Build** their nameplate chain by adding/removing/reordering segments
- **Preview** the composited nameplate text in real time
- **Toggle** "only show when looking at entity" mode (view-cone filter)

Preferences are saved per player and persist across sessions.

## How It Works

1. **Registration** — Mods call `NameplateAPI.describe()` during `setup()` to register UI metadata, and `NameplateAPI.register()` at runtime to set per-entity text via the `NameplateData` ECS component.

2. **Aggregation** — The `NameplateAggregatorSystem` ticks every frame. For each visible entity with a `NameplateData` component, it reads the segment entries, applies the viewer's preferences (ordering, enabled/disabled filters), composites the text, and queues a nameplate update to each viewer.

3. **View-cone filtering** — When enabled, the aggregator uses dot-product math to check if the viewer is looking at the entity (within a ~25 degree half-angle cone at up to 30 blocks range).

4. **Preferences** — Each player's segment chain, ordering, and settings are stored by `NameplatePreferenceStore` and persisted to disk.

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
      INameplateRegistry.java             Internal registry interface
      NameplateException.java             Base exception
      NameplateNotInitializedException.java
      NameplateArgumentException.java

  nameplate-server/
    src/main/java/.../server/
      NameplateBuilderPlugin.java         Server plugin entry point
      NameplateAggregatorSystem.java      Per-tick nameplate compositor
      NameplateRegistry.java              Segment metadata store
      NameplateBuilderPage.java           Player UI page
      NameplateBuilderCommand.java        /nameplatebuilder command
      NameplatePreferenceStore.java       Per-player preference persistence
      SegmentKey.java                     record(pluginId, segmentId)
    src/main/resources/
      Common/UI/Custom/Pages/
        NameplateBuilder_Editor.ui        UI layout definition

  nameplate-example-mod/
    src/main/java/.../example/
      NameplateExamplePlugin.java         Example: describe + register
      LifetimeNameplateSystem.java        Example: tick-based updates
```

## License

This project is licensed under the [MIT License](LICENSE).
