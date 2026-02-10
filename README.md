# NameplateBuilder

A server-side nameplate aggregator for Hytale that lets multiple mods contribute text segments to entity nameplates, with a built-in player UI for customization.

### Downloads

[![CurseForge Server Plugin](https://img.shields.io/badge/Server_Plugin-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/PLACEHOLDER_SERVER_SLUG)
[![CurseForge API](https://img.shields.io/badge/API_(for_mod_devs)-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/PLACEHOLDER_API_SLUG)
[![GitHub Example Mod](https://img.shields.io/badge/Example_Mod-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/TimShol/hytale-nameplate-builder/releases/tag/v1.0.0)

<!-- TODO: Replace PLACEHOLDER_SERVER_SLUG, PLACEHOLDER_API_SLUG, and PLACEHOLDER_USER with actual values -->

![Editor UI overview](docs/screenshots/editor-overview.png)
<!-- SCREENSHOT: Full editor window with several blocks in the chain, some in available, and preview visible -->

## Overview

NameplateBuilder solves a core problem for modders and players for Hytale: when multiple mods want to display information above entities (health, guild tags, titles, ranks, etc.), they conflict over the single `Nameplate` component. NameplateBuilder acts as a central aggregator — each mod registers its own named segments, and the system composites them into a single nameplate string per viewer, per entity, every tick (in an efficient way).

Players get a UI to choose which segments they see, reorder them, customize separators between individual segments, pick format variants, configure prefix/suffix wrapping, adjust bar display styles, configure a vertical nameplate offset, and toggle a "only show when looking at entity" mode. Server administrators can force specific segments to always display for all players, disable segments globally, and configure a custom server name. A coloured welcome message is shown on join. Nameplates are automatically cleared when an entity dies.

## Features

- **Multi-mod nameplate aggregation** — Any number of mods can register their own named segments (health, guild tag, tier, title, etc.) and NameplateBuilder composites them into a single nameplate per entity
- **Per-player customization UI** — Players open `/npb` to browse, search, add, remove, and reorder nameplate segments from all installed mods
- **Dashboard with sidebar** — The UI is organized with a left sidebar (General settings, NPC tab, Player tab, Admin tab) and a main content area that switches between panels
- **Per-block separators** — Each segment in the chain can have its own separator to the next segment (or no separator at all), allowing fine-grained control over how segments are joined
- **Example text** — Mods can provide example text for segments (e.g. `"67/67"` for health) shown as a preview bar in the UI, helping players understand what each segment displays before enabling it
- **Nameplate offset** — Configurable vertical offset value per player, using invisible anchor entities positioned above the real entity for hologram-style rendering (accepts both comma and dot decimal separators)
- **View-cone filtering** — Optional "only show when looking at" mode hides nameplates for entities outside a ~25 degree view cone at up to 30 blocks range
- **Death cleanup** — Nameplates are automatically cleared when an entity dies, instead of lingering through the death animation
- **Live preview** — The UI shows a real-time preview of the composited nameplate text with the player's current segment chain and separators
- **Segment target hints** — Mods tag segments as `[All]`, `[Players]`, or `[NPCs]` so players know at a glance which segments are relevant
- **Admin required segments** — Server owners can force specific segments to always display for all players via a two-column admin panel (requires `nameplatebuilder.admin` permission). Move segments between "Available" and "Required" columns; required segments appear with a yellow tint and cannot be removed from the chain
- **Admin disabled segments** — Admins can globally disable specific segments so they are hidden from all players entirely — disabled segments cannot be enabled, added to chains, or seen in any available list. When every registered segment is disabled, the aggregator blanks all nameplates globally and the welcome message reports nameplates as disabled
- **Admin server name** — Admins can set a custom server display name (via the Settings sub-tab) that appears in the join welcome message (e.g. `[MyServer] - Use /npb to customize your nameplates.`). Defaults to "NameplateBuilder" if left blank
- **Welcome message** — A coloured join message is sent to players on connect: green when nameplates are available (`Use /npb to customize your nameplates.`) or red when all segments are admin-disabled (`Nameplates are disabled on this server.`). Players can toggle this message off via General settings
- **Format variants** — Mods can register multiple display formats per segment (e.g. health as `"42/67"`, `"63%"`, or `"||||||------"`). Players choose their preferred format via a popup in the editor. The selected variant persists per player per segment
- **Built-in segments** — NameplateBuilder ships with **Player Name** (with an anonymize variant that shows "Player" instead of real names), **Health**, **Stamina**, and **Mana** (each with current/max, percentage, and visual bar variants) out of the box. Built-in segments are shown with a distinct warm-purple tint in the available blocks list
- **Prefix/suffix wrapping** — For segments that support it (e.g. Health), players can type custom prefix and suffix text (e.g. `"HP: ["` and `"]"`) to wrap the segment output
- **Bar empty-fill customization** — Players can customize the empty-slot character used in visual bar variants (default: `"-"`, but can be changed to any character like `"."`, `"_"`, `"░"`, etc.)
- **Confirm/cancel workflow** — The format popup uses a confirm/cancel pattern: variant selection, prefix/suffix, and bar style changes are previewed live but only persisted on Confirm. Cancel reverts all changes to their original values
- **Hidden metadata keys** — Keys prefixed with `_` are stored in the component but never displayed, useful for per-entity internal state
- **Persistent preferences** — All player settings (chain order, separators, offset, toggles, variant selections, prefix/suffix, bar style) and admin config are saved to disk and survive server restarts
- **Safe concurrent initialization** — Uses `putComponent()` (upsert) pattern to avoid race conditions when multiple mods initialize the same entity

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

## Player UI

Players open the Nameplate Builder editor via the `/nameplatebuilder` command (aliases: `/npb`, `/nameplateui`). The UI features a sidebar on the left with five sections:

### Sidebar

| Section | Contents |
|---------|----------|
| **GENERAL** | Settings — master enable/disable, look-at toggle, vertical offset, welcome message toggle |
| **NAMEPLATES** | NPCs — segment chain editor for NPC nameplates |
| | Players — segment chain editor for player nameplates |
| | Disabled — read-only view of all admin-disabled segments |
| **ADMIN** | Required — required segments panel (only visible with `nameplatebuilder.admin` permission) |
| | Disabled — disabled segments panel |
| | Settings — server name configuration |

### Editor Tabs (NPCs / Players)

Each editor tab lets players customize nameplates for a specific entity type:

- **Chain** — the player's active segment chain, shown as blocks with move left/right and remove buttons. Each block shows a preview example bar (if the mod provided example text) and a Format button when the segment has multiple variants. The Format button turns green and shows "Formatted" when a non-default variant is selected
- **Format popup** — opened from the Format button on chain blocks. Shows variant options to choose from, optional prefix/suffix text fields, and optional bar empty-fill customization. Uses a Confirm/Cancel workflow — changes are previewed live but only saved on Confirm
- **Preview** — real-time composited text preview with the player's current chain and separators, truncated with ellipsis if too long
- **Available Blocks** — all registered segments not yet in the chain, with search/filter, pagination, and an Add button per block
- **Clear All** — removes all blocks from the chain in one click
- **Save** — persists the current chain, ordering, and settings to disk

### General Tab

- **Enable Nameplates** — master toggle to show/hide all nameplates
- **Only Show When Looking** — view-cone filter toggle
- **Show Welcome Message** — toggle the coloured join message on/off
- **Offset** — vertical nameplate offset (accepts both `,` and `.` as decimal separators, clamped to -5.0 to 5.0)

### Admin Tab

Visible only to players with the `nameplatebuilder.admin` permission. Contains three sub-tabs:

#### Required Sub-Tab

Two-column layout with a vertical divider. Available blocks are shown with a green tint and the "REQUIRED SEGMENTS" title is orange:

- **Left column ("Available")** — segments that are not required (excludes disabled segments). Each row shows the segment block with a `>` button to move it to the required column
- **Right column ("Required")** — segments that are forced on all players. Each row shows a `<` button to move it back, with a yellow-tinted background (`#3d3820`)

Required segments are:

- Always displayed in every player's nameplate chain (cannot be removed)
- Shown with a yellow-tinted background in the editor
- Always included in the aggregated nameplate output, even if a player disabled them
- Still reorderable by players (move left/right still works)
- Cannot simultaneously be disabled (setting one clears the other)

Each column has independent pagination (7 rows per page). The panel has Save and Reset buttons. Reset clears all required segments.

#### Disabled Sub-Tab

Same two-column layout, using red-tinted blocks and headers:

- **Left column ("Available")** — segments that are not disabled (excludes required segments). Each row has a `>` button to move it to the disabled column
- **Right column ("Disabled")** — segments hidden from all players. Red-tinted backgrounds (`#3d2020`)

When all registered segments are disabled, the aggregator blanks all nameplates globally and the join message switches to the red "disabled" variant.

Each column has independent pagination (7 rows per page) with Save and Reset buttons.

#### Settings Sub-Tab

- **Server Name** — text field for the server display name shown in the join welcome message. Defaults to "NameplateBuilder" if left blank. Has Save and Reset buttons.

Admin configuration is persisted in `admin_config.txt` with `R|pluginId|segmentId` for required, `D|pluginId|segmentId` for disabled, and `S|serverName` for the server name.

### Disabled Tab (Player View)

Visible to all players in the NAMEPLATES sidebar section. Shows a read-only 4×4 grid of all admin-disabled segments so players can see what has been turned off by the server. Red-tinted backgrounds, no action buttons, with pagination (16 per page).

When a player has no blocks enabled, entities managed by NameplateBuilder show "Type /npb to customize" as a hint instead of the raw entity ID.

Preferences are saved per player and persist across sessions.

### Screenshots

#### Editor overview
![Editor UI overview](docs/screenshots/editor-overview.png)
<!-- SCREENSHOT: Full editor window showing the sidebar on the left with GENERAL/NAMEPLATES/ADMIN sections, and the NPC tab active with chain blocks, separators, preview bar, and available blocks grid -->

#### Editor with blocks in chain
![Editor with chain](docs/screenshots/editor-chain.png)
<!-- SCREENSHOT: Editor showing 2-4 blocks in the chain section with example bars visible, separators between them, and more blocks in Available below -->

#### Editor with empty chain
![Editor empty](docs/screenshots/editor-empty.png)
<!-- SCREENSHOT: Editor with "No blocks added yet" in the chain section, all blocks in Available, showing the Clear All and Preview row -->

#### Available blocks with example text
![Available blocks](docs/screenshots/available-blocks.png)
<!-- SCREENSHOT: Close-up of the Available Blocks section showing several blocks with their [All]/[Players]/[NPCs] target tags, author names, and example preview bars (e.g. "67/67", "[Warriors]") -->

#### General settings tab
![General settings](docs/screenshots/general-settings.png)
<!-- SCREENSHOT: General tab showing the Enable Nameplates toggle, Only Show When Looking toggle, Offset field, and Save button -->

#### Admin required segments panel
![Admin panel](docs/screenshots/admin-required.png)
<!-- SCREENSHOT: Admin Required sub-tab showing the two-column layout — left "AVAILABLE" column (green-tinted blocks) with ">" buttons, orange "REQUIRED SEGMENTS" title, 5px vertical divider in the center, right "REQUIRED" column (yellow-tinted) with "<" buttons and segment blocks, pagination under each column, and Save/Reset buttons at the bottom -->

#### Admin disabled segments panel
![Admin disabled](docs/screenshots/admin-disabled.png)
<!-- SCREENSHOT: Admin Disabled sub-tab showing two-column layout — left "AVAILABLE" column (green-tinted) with ">" buttons, right "DISABLED" column (red-tinted #3d2020 backgrounds) with "<" buttons, pagination under each column, Save/Reset buttons -->

#### Admin settings panel
![Admin settings](docs/screenshots/admin-settings.png)
<!-- SCREENSHOT: Admin Settings sub-tab showing the Server Name text field with label, description text explaining what it does, and Save/Reset buttons with feedback label -->

#### Player disabled segments tab
![Player disabled](docs/screenshots/player-disabled.png)
<!-- SCREENSHOT: The Disabled tab in NAMEPLATES section showing a read-only 4×4 grid of admin-disabled segments with red-tinted backgrounds, no action buttons, title "DISABLED SEGMENTS", and pagination -->

#### Required segment with yellow outline
![Required segment](docs/screenshots/required-segment.png)
<!-- SCREENSHOT: Editor tab showing a required segment in the chain with yellow-tinted background, no remove button visible, alongside normal green-tinted chain blocks -->

#### Preview bar
![Preview](docs/screenshots/preview.png)
<!-- SCREENSHOT: Close-up of the Preview bar showing composited text like "Health Bar - Guild Tag - Elite Tier" -->

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

#### Format popup (variant selection)
![Format popup](docs/screenshots/format-popup.png)
<!-- SCREENSHOT: The "Select Format" popup for Health, showing the three variants (Current/Max, Percentage, Bar) with the Bar variant highlighted in green, plus the Prefix/Suffix and Bar Style sections below with example values like "HP: [" and "]" -->

#### Format popup with prefix/suffix
![Format prefix suffix](docs/screenshots/format-prefix-suffix.png)
<!-- SCREENSHOT: Close-up of the Format popup's Prefix/Suffix section and Bar Style section, showing "Before: HP: [" and "After: ]" and "Empty: -" fields filled in -->

#### Chain block with "Formatted" indicator
![Formatted chain block](docs/screenshots/chain-formatted.png)
<!-- SCREENSHOT: A chain block (e.g. Health) showing the green "Formatted" button indicating a non-default variant is selected, with the preview bar showing the formatted output (e.g. "||||||------") -->

#### Built-in blocks in available list
![Built-in blocks](docs/screenshots/builtin-blocks.png)
<!-- SCREENSHOT: The Available Blocks section showing built-in segments (Health, Player Name) with their warm-purple background color, distinct from normal mod blocks -->

#### In-world nameplate with bar variant
![Bar nameplate](docs/screenshots/nameplate-bar.png)
<!-- SCREENSHOT: An entity in the game world showing a nameplate with the health bar variant, e.g. "HP: [||||||------]" with custom prefix/suffix -->

#### Player name anonymized
![Anonymized name](docs/screenshots/nameplate-anonymized.png)
<!-- SCREENSHOT: A player entity showing "Player" instead of their real name, demonstrating the anonymize variant -->

#### Welcome message (nameplates enabled)
![Welcome enabled](docs/screenshots/welcome-enabled.png)
<!-- SCREENSHOT: Chat showing the green welcome message: "[ServerName] - Use /npb to customize your nameplates." in #55FF55 color -->

#### Welcome message (nameplates disabled)
![Welcome disabled](docs/screenshots/welcome-disabled.png)
<!-- SCREENSHOT: Chat showing the red welcome message: "[ServerName] - Nameplates are disabled on this server." in #FF5555 color -->

## How It Works

1. **Registration** — Mods call `NameplateAPI.describe()` during `setup()` to register UI metadata (display name, target hint, example text), and `NameplateAPI.register()` at runtime to set per-entity text via the `NameplateData` ECS component.

2. **NPC initialization** — An `EntityTickingSystem` checks for newly spawned NPCs that don't yet have `NameplateData`. On their first tick, the system seeds default segments by adding a `NameplateData` component via the `CommandBuffer`. The aggregator picks up any visible entity that has `NameplateData` — no native `Nameplate` component is needed. Subsequent ticks skip already-initialized entities.

3. **Aggregation** — The `NameplateAggregatorSystem` ticks every frame. For each visible entity with a `NameplateData` component, it resolves segment keys from the component (skipping hidden `_`-prefixed keys and admin-disabled segments), applies the viewer's preferences (ordering, enabled/disabled, format variants, prefix/suffix wrapping, bar fill replacement, per-block separators), enforces admin-required segments, composites the text, and queues a nameplate update to each viewer. If all segments are disabled, it shows a hint message instead of the raw entity ID.

4. **Required segment enforcement** — Segments moved to the "Required" column by an admin always display in the aggregated output, regardless of the viewer's personal preferences. In the editor, required segments appear with a yellow-tinted background and cannot be removed from the chain (but can still be reordered).

5. **Disabled segment enforcement** — Segments moved to the "Disabled" column by an admin are hidden globally: they are excluded from all player chains, filtered from available-block lists, and skipped by the aggregator. A segment cannot be both required and disabled simultaneously. When all registered segments are disabled, the aggregator blanks all nameplates and the join message switches to the red "disabled" variant.

6. **Death cleanup** — When an entity receives a `DeathComponent`, the aggregator sends an empty nameplate update to all viewers (clearing the displayed text immediately) and removes the `NameplateData` component so no further updates are produced. Without this, nameplates would linger through the death animation until the entity model despawns.

7. **View-cone filtering** — When enabled, the aggregator uses dot-product math to check if the viewer is looking at the entity (within a ~25 degree half-angle cone at up to 30 blocks range). Entities outside the cone receive an empty nameplate update to prevent the default ID from bleeding through.

8. **Anchor entities** — When a player configures a vertical offset, an invisible "anchor" entity (a bare `ProjectileComponent` with `Intangible` and `NetworkId`) is spawned above the real entity. The aggregator routes nameplate text to the anchor instead of the real entity, creating a hologram-style offset effect. Anchors follow the real entity every tick and are automatically cleaned up on death or when the offset returns to zero.

9. **Preferences** — Each player's segment chain, ordering, per-block separators, format variant selections, prefix/suffix text, bar empty-fill character, offset, welcome message toggle, and settings are stored by `NameplatePreferenceStore` and persisted to disk. Admin configuration (required segments, disabled segments, server name) is stored separately by `AdminConfigStore`.

10. **Format variant resolution** — When a viewer has selected a non-default variant (index > 0) for a segment, the aggregator looks up the suffixed key (e.g. `"health.2"` for variant 2). If found, that text is used; otherwise it falls back to the base key. After variant resolution, bar placeholder characters are replaced with the viewer's custom empty fill, and prefix/suffix wrapping is applied.

11. **Welcome message** — When a player joins (via `PlayerReadyEvent`), a coloured message is sent: green if any segments are available, red if all segments are admin-disabled. The message includes the configured server name (or "NameplateBuilder" as default). Players can disable the message via the General settings tab.

## Permissions

| Permission | Description |
|------------|-------------|
| `nameplatebuilder.admin` | Grants access to the admin tabs (Required, Disabled, Settings) in the UI. Without this permission, the ADMIN section is hidden from the sidebar. |

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
      DefaultSegmentSystem.java           Built-in segments (Player Name, Health, Stamina, Mana)
      NameplateRegistry.java              Segment metadata store (with variant support)
      NameplateBuilderPage.java           Player UI page (sidebar, editor, format popup)
      NameplateBuilderCommand.java        /npb command with admin permission check
      NameplatePreferenceStore.java       Per-player preference persistence
      AdminConfigStore.java               Server-wide admin config (required, disabled, server name)
      AnchorEntityManager.java            Invisible anchor entity lifecycle
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
