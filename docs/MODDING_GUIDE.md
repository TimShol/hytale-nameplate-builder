# Modding Guide

Step-by-step guide for mod developers who want to add their own nameplate blocks to NameplateBuilder.

> **Looking for the main README?** See [README.md](../README.md) for an overview, features, and screenshots.
> **Looking for internals?** See [Project Structure](PROJECT_STRUCTURE.md) for architecture and file details.

---

## How NameplateBuilder Works (30-Second Version)

NameplateBuilder is a **central nameplate aggregator**. Instead of each mod fighting over the single `Nameplate` component, mods register named **segments** (like `"health"`, `"guild"`, `"tier"`), and NameplateBuilder composites them into one nameplate string per entity, per viewer, every tick.

Players get a UI (`/npb`) to pick which segments they see, reorder them, choose display formats, and set separators. Server admins can force or disable segments globally.

**Your job as a modder** is simple:
1. Tell NameplateBuilder about your segments (names, targets, examples, variants)
2. Push text values onto entities at runtime

That's it. NameplateBuilder handles the rest — compositing, per-player preferences, the UI, persistence, death cleanup, everything.

---

## Step 1 — Set Up Your Project

### 1a. Add the manifest dependency

In your mod's `manifest.json`, add NameplateBuilder as a dependency so it loads before your mod:

```json
{
  "Dependencies": {
    "Frotty27:NameplateBuilder": "*"
  }
}
```

This is **required**. Without it, calling the API will throw `NameplateNotInitializedException` because NameplateBuilder hasn't loaded yet.

### 1b. Add the API jar to your build

In your `build.gradle`, add the API jar as a `compileOnly` dependency:

```groovy
dependencies {
    // Point this to wherever you placed the NameplateBuilder API jar
    compileOnly files('libs/NameplateBuilder-API-1.0.0.jar')
}
```

You only need the lightweight API jar (`nameplate-api`), not the full server plugin. The API jar contains just the classes you interact with:

| Class | What it does |
|-------|-------------|
| `NameplateAPI` | Main entry point — all your calls go through here |
| `NameplateData` | The ECS component that holds segment text on entities |
| `SegmentTarget` | Enum: `ALL`, `PLAYERS`, or `NPCS` — UI hint for which entities a segment applies to |
| `NameplateException` | Base exception class |
| `NameplateNotInitializedException` | Thrown if NameplateBuilder hasn't loaded yet |
| `NameplateArgumentException` | Thrown if you pass null/blank arguments |

### 1c. Import the API

```java
import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
```

---

## Step 2 — Describe Your Segments

In your plugin's `setup()` method, call `NameplateAPI.describe()` to tell the UI about each segment you plan to use. This is **optional but highly recommended** — without it, the UI shows the raw segment ID instead of a nice display name.

### What `describe()` does

It registers **UI metadata only**. It does NOT create any ECS components or affect entities. It just tells the Nameplate Builder UI: "Hey, my mod has a segment called `health`, show it as `Health Bar`, it's for all entities, and it looks like `67/67`."

### The `describe()` overloads

There are three overloads, from simplest to most complete:

```java
@Override
protected void setup() {

    // ─── Overload 1: Just a name ───
    // Shows up in the UI as "Custom Tag" with target [All] and no example preview.
    // Good for segments where the value is unpredictable.
    NameplateAPI.describe(this, "custom-tag", "Custom Tag");


    // ─── Overload 2: Name + target ───
    // The SegmentTarget tells the UI which entity types this segment is meant for.
    // It shows as a tag next to the author, e.g. "by YourMod [NPCs]".
    // This is purely a UI hint — it does NOT restrict which entities you can
    // actually register the segment on at runtime.
    NameplateAPI.describe(this, "faction", "Faction", SegmentTarget.NPCS);


    // ─── Overload 3: Name + target + example (RECOMMENDED) ───
    // The example text is shown as a preview in the UI so players can see
    // what the segment looks like before enabling it.
    // This is the most informative overload — use it whenever possible.
    NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL, "67/67");
    NameplateAPI.describe(this, "guild", "Guild Tag", SegmentTarget.PLAYERS, "[Warriors]");
    NameplateAPI.describe(this, "tier", "Elite Tier", SegmentTarget.NPCS, "[Elite]");
    NameplateAPI.describe(this, "level", "Level", SegmentTarget.ALL, "Lv. 42");
}
```

### `SegmentTarget` values

| Target | UI Tag | Use when... |
|--------|--------|-------------|
| `SegmentTarget.ALL` | `[All]` | The segment applies to any entity (players, NPCs, etc.) |
| `SegmentTarget.PLAYERS` | `[Players]` | The segment only makes sense on players (e.g. guild tag, rank) |
| `SegmentTarget.NPCS` | `[NPCs]` | The segment only makes sense on NPCs (e.g. faction, mood, bounty) |

The target is **only a UI hint**. You can still register a `PLAYERS`-targeted segment on an NPC at runtime if you want to. The UI just uses it to help players understand what each segment is for.

---

## Step 3 — Define Format Variants (Optional)

If your segment supports multiple display formats, register variant names so players can pick their preferred format via the UI:

```java
@Override
protected void setup() {
    // First, describe the segment
    NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL, "67/67");

    // Then register format variants for it.
    // Index 0 is always the default format.
    NameplateAPI.describeVariants(this, "health", List.of(
        "Current/Max",       // variant 0 (default): "42/67"
        "Percentage",        // variant 1:           "63%"
        "Bar"                // variant 2:           "||||||------"
    ));

    // Another example — a level segment with 3 display styles
    NameplateAPI.describe(this, "level", "Level", SegmentTarget.ALL, "Lv. 42");
    NameplateAPI.describeVariants(this, "level", List.of(
        "Compact",           // variant 0 (default): "Lv. 42"
        "Full",              // variant 1:           "Level 42"
        "Number Only"        // variant 2:           "42"
    ));
}
```

At runtime, you push text for **each variant** using suffixed keys:

```java
// Variant 0 (default) — uses the base key
data.setText("health", currentHp + "/" + maxHp);

// Variant 1 — uses ".1" suffix
data.setText("health.1", percent + "%");

// Variant 2 — uses ".2" suffix
data.setText("health.2", barString);
```

The aggregator automatically reads the viewer's selected variant and picks the right suffixed key. If a suffixed key doesn't exist, it falls back to the base key. So at minimum, always set the base key.

> **Tip:** Variant names can include parenthesized examples, e.g. `"Percentage (63%)"`. The UI extracts the value inside parentheses to show as a preview.

---

## Step 4 — Register Text on Entities

This is the core of the API. At runtime, you push text to entities so it shows up in their nameplate.

### Basic usage — register, update, remove

```java
// ─── Register a segment on an entity ───
// If the entity doesn't have a NameplateData component yet, one is
// created and attached automatically.
NameplateAPI.register(store, entityRef, "health", "67/67");
NameplateAPI.register(store, entityRef, "guild", "[Warriors]");
NameplateAPI.register(store, entityRef, "tier", "[Elite]");

// ─── Update a segment ───
// Just call register() again. It overwrites the old value in-place.
// No need to remove first. No flashing. Efficient.
NameplateAPI.register(store, entityRef, "health", "23/67");

// ─── Remove a single segment ───
// The entity keeps its other segments. If this was the last segment,
// the NameplateData component is automatically removed from the entity.
NameplateAPI.remove(store, entityRef, "health");

// ─── Remove ALL nameplate data from an entity ───
// Wipes every segment at once. Use this when an entity should have
// no nameplate at all (e.g. going invisible, despawning).
store.tryRemoveComponent(entityRef, NameplateAPI.getComponentType());
```

### Where you can call `register()` and `remove()`

These methods work **anywhere you have access to the `Store` and a `Ref`**: event handlers, command handlers, custom systems, etc.

**One exception:** if you're inside an `EntityTickingSystem` and the entity does NOT already have a `NameplateData` component, `register()` will try to call `store.addComponent()`, which throws `IllegalStateException` because the store is locked during tick processing. See [Step 5](#step-5--attach-nameplates-to-npcs-on-spawn) for the correct pattern.

If the entity **already has** the component, `register()` just mutates the internal map — which is safe from tick systems.

---

## Step 5 — Attach Nameplates to NPCs on Spawn

The recommended pattern for giving NPCs nameplate data when they spawn is an `EntityTickingSystem`. Here's a complete, working example:

```java
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class MyNpcNameplateSystem extends EntityTickingSystem<EntityStore> {

    // Change this to match the NPC role you want to target
    private static final String ROLE_NAME = "Kweebec";

    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    MyNpcNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.npcType = NPCEntity.getComponentType();
        this.nameplateDataType = nameplateDataType;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        // Query all entities that have the NPCEntity component
        return Archetype.of(npcType);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        // Filter by NPC role name
        NPCEntity npc = chunk.getComponent(index, npcType);
        if (npc == null || !ROLE_NAME.equals(npc.getRoleName())) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        // Skip if this entity already has nameplate data (already initialized)
        if (store.getComponent(entityRef, nameplateDataType) != null) {
            return;
        }

        // ── First time seeing this NPC — seed its nameplate data ──
        NameplateData data = new NameplateData();
        data.setText("health", "100/100");
        data.setText("level", "Lv. 10");
        data.setText("faction", "<Forest>");

        // IMPORTANT: Use commandBuffer.putComponent(), NOT store.addComponent().
        // The store is locked during tick processing — direct writes throw.
        // putComponent() is an upsert (add-or-replace), so it's safe even if
        // another system adds the component between our read and the buffer executing.
        commandBuffer.putComponent(entityRef, nameplateDataType, data);
    }
}
```

Register the system in your plugin's `setup()`:

```java
@Override
protected void setup() {
    // Describe your segments first
    NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL, "100/100");
    NameplateAPI.describe(this, "level", "Level", SegmentTarget.ALL, "Lv. 10");
    NameplateAPI.describe(this, "faction", "Faction", SegmentTarget.NPCS, "<Forest>");

    // Get the NameplateData component type (needed by the system constructor)
    ComponentType<EntityStore, NameplateData> nameplateDataType = NameplateAPI.getComponentType();

    // Register your tick system
    getEntityStoreRegistry().registerSystem(new MyNpcNameplateSystem(nameplateDataType));
}
```

### Why `CommandBuffer` instead of `store.addComponent()`?

Inside an `EntityTickingSystem`, the `Store` is **locked for structural changes** (adding/removing components). If you call `store.addComponent()` directly, it throws:

```
IllegalStateException: Store is currently processing
```

The `CommandBuffer` queues changes and executes them after the system finishes its tick. Reading (`store.getComponent()`) and mutating existing component data in-place (`data.setText()`) are safe.

### Why `putComponent()` instead of `addComponent()`?

`addComponent()` throws if the component already exists. `putComponent()` is an **upsert** (add or replace) — safe against race conditions when multiple systems or mods might initialize the same entity.

---

## Step 6 — Update Segments Every Tick

Once an entity has a `NameplateData` component, you can update its text every tick safely. This is how you keep dynamic values (like health, lifetime timers, buff durations) up to date.

```java
final class MyTickUpdater extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    MyTickUpdater(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.nameplateDataType = nameplateDataType;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        // Query all entities that have NameplateData
        return Archetype.of(nameplateDataType);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        NameplateData data = chunk.getComponent(index, nameplateDataType);
        if (data == null) {
            return;
        }

        // Calling setText() every tick is safe — it updates the internal map
        // value in place. No component is added or removed, so there's no
        // flashing. The aggregator reads the latest value on the next nameplate tick.
        data.setText("health", computeHealthText(store, chunk, index));
    }

    private String computeHealthText(Store<EntityStore> store,
                                     ArchetypeChunk<EntityStore> chunk, int index) {
        // Your health computation logic here
        return "42/67";
    }
}
```

Calling `data.setText()` every tick is **cheap** — it's just a `HashMap.put()`. The aggregator reads the latest values when it composites the nameplate string, so updates are reflected immediately.

---

## Step 7 — Hidden Metadata Keys

Keys that start with `_` (underscore) are **hidden metadata**. They are stored in the `NameplateData` component but are **never shown** in the nameplate output or the player UI. Use them to store per-entity internal state alongside visible segments.

```java
// Store a spawn tick so you can compute per-entity lifetime
data.setText("_spawn_tick", String.valueOf(globalTick));

// Read it back later — this key is invisible to players
String spawnTick = data.getText("_spawn_tick");

// Store any internal state you want alongside the visible segments
data.setText("_last_hit_by", attackerName);
data.setText("_phase", "enraged");
```

This is useful when you need per-entity state inside a tick system but don't want to register a separate ECS component for it. The `_` prefix convention is enforced by the aggregator — it skips any key starting with underscore.

---

## Step 8 — Unregistering and Cleanup

### You do NOT need to clean up on death

NameplateBuilder **automatically clears nameplates when an entity dies**. When an entity receives a `DeathComponent`, the aggregator sends an empty nameplate to all viewers and removes the `NameplateData` component. You don't need to handle this yourself.

### You do NOT need to undescribe on shutdown

When your plugin unloads, NameplateBuilder **automatically removes all segment descriptions** registered by your plugin. You don't need to call `undescribe()` in a shutdown hook.

### `undescribe()` is for runtime removal only

The `undescribe()` method exists for edge cases where you want to dynamically remove a segment from the UI **while the server is running** (e.g., a minigame mod that adds/removes segments based on game phase):

```java
// Remove the "bounty" segment from the UI (e.g. bounty system disabled mid-game)
NameplateAPI.undescribe(this, "bounty");
```

After calling `undescribe()`:
- The segment disappears from the Nameplate Builder UI
- Existing `NameplateData` on entities is **not** affected — the text stays until you explicitly remove it or the entity dies
- Players who had it in their chain will no longer see it rendered

For the vast majority of mods, you will **never need to call `undescribe()`**.

---

## Complete Example — Putting It All Together

Here's a full plugin that registers 3 segments with different targets, one with format variants, and a tick system that initializes NPC nameplates and keeps health updated:

```java
package com.example.mymod;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public final class MyModPlugin extends JavaPlugin {

    public MyModPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        // ── Step 2: Describe segments for the UI ──

        // Health — for all entities, with 3 format variants
        NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL, "100/100");
        NameplateAPI.describeVariants(this, "health", List.of(
            "Current/Max",    // variant 0 (default)
            "Percentage",     // variant 1
            "Bar"             // variant 2
        ));

        // Guild — for players only
        NameplateAPI.describe(this, "guild", "Guild Tag", SegmentTarget.PLAYERS, "[Warriors]");

        // Faction — for NPCs only
        NameplateAPI.describe(this, "faction", "Faction", SegmentTarget.NPCS, "<Undead>");


        // ── Step 5: Register a tick system for NPC spawn initialization ──

        ComponentType<EntityStore, NameplateData> nameplateDataType =
            NameplateAPI.getComponentType();

        getEntityStoreRegistry().registerSystem(
            new MyNpcNameplateSystem(nameplateDataType));
    }
}
```

With `MyNpcNameplateSystem` from [Step 5](#step-5--attach-nameplates-to-npcs-on-spawn) handling the spawn initialization, and the format variant text being pushed in a tick system from [Step 6](#step-6--update-segments-every-tick), your segments will show up in the Nameplate Builder UI for players to add to their chain, reorder, format, and customize.

---

## API Reference

### `NameplateAPI` — Static Methods

| Method | Description |
|--------|-------------|
| `describe(plugin, segmentId, displayName)` | Register UI metadata. Defaults to `SegmentTarget.ALL`, no example. |
| `describe(plugin, segmentId, displayName, target)` | Register UI metadata with an entity target hint. |
| `describe(plugin, segmentId, displayName, target, example)` | Register UI metadata with target hint and example preview text. |
| `describeVariants(plugin, segmentId, variantNames)` | Register format variant names for a segment. Index 0 is the default. |
| `undescribe(plugin, segmentId)` | Remove a segment description from the UI at runtime. |
| `register(store, entityRef, segmentId, text)` | Set or update nameplate text for a segment on an entity. |
| `remove(store, entityRef, segmentId)` | Remove a segment's text from an entity. Auto-removes the component if empty. |
| `getComponentType()` | Get the `ComponentType<EntityStore, NameplateData>` for advanced use cases. |

### `NameplateData` — Instance Methods

| Method | Description |
|--------|-------------|
| `setText(key, text)` | Set or update a segment's text. Pass `null` to remove. |
| `getText(key)` | Get a segment's current text. Returns `null` if not set. |
| `removeText(key)` | Remove a segment. |
| `getEntries()` | Unmodifiable view of all key-value entries. |
| `isEmpty()` | Returns `true` if no entries exist. |

### `SegmentTarget` — Enum

| Value | UI Tag | Description |
|-------|--------|-------------|
| `ALL` | `[All]` | Applies to all entity types |
| `PLAYERS` | `[Players]` | Intended for player entities |
| `NPCS` | `[NPCs]` | Intended for NPC entities |

### Exceptions

| Exception | When it's thrown |
|-----------|-----------------|
| `NameplateNotInitializedException` | You called the API before NameplateBuilder loaded. Check your `manifest.json` dependency. |
| `NameplateArgumentException` | You passed `null` or blank to a required parameter. |

Both extend `NameplateException`, which extends `RuntimeException`.

---

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Calling `store.addComponent()` inside a tick system | Use `commandBuffer.putComponent()` instead |
| Calling `NameplateAPI.register()` inside a tick system on an entity without data | Build a `NameplateData` manually and use `commandBuffer.putComponent()` |
| Forgetting to add the manifest dependency | Add `"Frotty27:NameplateBuilder": "*"` to your `manifest.json` Dependencies |
| Calling `undescribe()` in a shutdown hook | Not needed — NameplateBuilder auto-cleans on plugin unload |
| Removing nameplate data on entity death | Not needed — NameplateBuilder auto-cleans on death |
| Pushing variant text without the base key | Always set the base key (`"health"`) — suffixed keys (`.1`, `.2`) are optional fallbacks |

---

## Further Reading

- **[Example Mod](../nameplate-example-mod/)** — Full working source code showing all patterns
- **[Project Structure](PROJECT_STRUCTURE.md)** — Internal architecture for contributors
- **[README](../README.md)** — Feature overview, screenshots, and admin documentation
