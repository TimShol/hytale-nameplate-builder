# Modding Guide

API reference, code examples, and integration guide for mod developers building on NameplateBuilder.

> **Looking for the main README?** See [README.md](../README.md) for an overview, features, and screenshots.
> **Looking for internals?** See [Project Structure](PROJECT_STRUCTURE.md) for architecture and file details.

## Architecture

```
nameplate-api/            Public API jar — compile against this
nameplate-server/         Server plugin — runs the aggregator, UI, and preferences
nameplate-example-mod/    Example mod demonstrating the API
```

**nameplate-api** exposes `NameplateAPI`, `NameplateData`, `SegmentTarget`, and the exception types. Mods depend on this at compile time.

**nameplate-server** registers the `NameplateData` ECS component, runs the `DefaultSegmentSystem` (built-in Player Name, Health, Stamina, Mana segments with NPC auto-attach) and `NameplateAggregatorSystem` tick system (including death cleanup, required/disabled segment enforcement, and global blanking), manages the player UI page, sends coloured welcome messages on join, and persists per-player preferences and admin configuration.

**nameplate-example-mod** shows the full modder workflow: describing segments with example text, attaching nameplates to NPCs on spawn, and updating segments every tick.

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
    // Basic — display name only (defaults to SegmentTarget.ALL, no example)
    NameplateAPI.describe(this, "health", "Health Bar");

    // With target hint — shown as a tag in the UI (e.g. [NPCs])
    NameplateAPI.describe(this, "tier", "Elite Tier", SegmentTarget.NPCS);

    // With target and example text — shown as a preview bar in the UI
    NameplateAPI.describe(this, "guild",  "Guild Tag",  SegmentTarget.PLAYERS, "[Warriors]");
    NameplateAPI.describe(this, "level",  "Level",      SegmentTarget.ALL,     "Lv. 42");
}
```

The `SegmentTarget` enum tells the UI which entity types a segment is intended for. The UI shows this as a tag next to the author (e.g. `by Frotty27 [NPCs]`), so players know at a glance which segments are relevant. Available targets:

| Target | Tag | Meaning |
|--------|-----|---------|
| `SegmentTarget.ALL` | `[All]` | Applies to all entities |
| `SegmentTarget.PLAYERS` | `[Players]` | Intended for player entities |
| `SegmentTarget.NPCS` | `[NPCs]` | Intended for NPC entities |

This is purely a UI hint — it does not enforce restrictions at runtime. The no-target overload `describe(plugin, id, name)` defaults to `ALL`.

The optional `example` parameter provides preview text shown in the UI so players can see what the segment looks like before enabling it (e.g. `"67/67"` for health, `"[Warriors]"` for guild tag). Pass `null` or omit the parameter for no example.

This step is optional. Undescribed segments still work — they just show the raw segment ID in the UI instead of a display name.

### 3. Define format variants (optional)

If your segment supports multiple display formats, register variant names after describing the segment. Players can then pick their preferred format via the editor's Format popup:

```java
@Override
protected void setup() {
    NameplateAPI.describe(this, "level", "Level", SegmentTarget.ALL, "Lv. 42");

    // Register 3 format variants for level — index 0 is the default
    NameplateAPI.describeVariants(this, "level", List.of(
        "Compact",         // variant 0: "Lv. 42"
        "Full",            // variant 1: "Level 42"
        "Number Only"      // variant 2: "42"
    ));
}
```

At runtime, push text for each variant using suffixed segment keys:

```java
// Variant 0 (default) uses the base key
data.setText("level", "Lv. " + level);
// Variant 1 uses ".1" suffix
data.setText("level.1", "Level " + level);
// Variant 2 uses ".2" suffix
data.setText("level.2", String.valueOf(level));
```

The aggregator automatically reads the viewer's selected variant index and looks up the corresponding suffixed key. If the suffixed key is not found, it falls back to the base key.

Variant names can include parenthesized examples (e.g. `"Percentage (69%)"`) — the editor extracts the value inside parentheses to show as a preview in the chain block.

### 4. Register text on entities

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

### 5. Attach nameplates to NPCs on spawn

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

### 6. Tick-based updates

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

### 7. Hidden metadata keys

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
| `describe(plugin, segmentId, displayName)` | Register UI metadata (defaults to `SegmentTarget.ALL`, no example) |
| `describe(plugin, segmentId, displayName, target)` | Register UI metadata with entity target hint |
| `describe(plugin, segmentId, displayName, target, example)` | Register UI metadata with target hint and example text |
| `describeVariants(plugin, segmentId, variantNames)` | Register format variant names for a segment (index 0 = default) |
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
