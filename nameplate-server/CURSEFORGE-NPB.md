# **NameplateBuilder**

Customize the Nameplate above NPCs & Players in Hytale. Any mod can integrate and add their "segment" to this - you control how it looks.

Health bars, elite titles, guild tags, ranks for both NPCs & Players - drag and drop them into any order, pick display formats, and tweak separators. Talking about interesting features…

![NameplateBuilder Preview](https://media.forgecdn.net/attachments/description/1460057/description_9c714cb4-6935-49ee-87fe-c2b493e7634c.gif)

[![Docs](https://img.shields.io/badge/Documentation_/_Guide-7C3AED?style=for-the-badge&logo=bookstack&logoColor=white)](https://docs.nameplatebuilder.frotty27.com/)
[![GitHub](https://img.shields.io/badge/GitHub-NameplateBuilder-7C3AED?style=for-the-badge&logo=github&logoColor=white)](https://github.com/TimShol/hytale-nameplate-builder)
[![Discord](https://img.shields.io/badge/Discord-Join_The_Community-7C3AED?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/t72K6sm3S2)

***

![Editor UI overview](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/editor-overview.png)

## Why?

In Hytale, each entity only has one nameplate. When multiple mods try to show their own info above an entity, they overwrite each other. NameplateBuilder solves this. It collects text from every mod (that has integrated using the API) and combines them into one nameplate that you can fully customize.

***

# Features

## What You Get Out of the Box

NameplateBuilder comes with built-in nameplate segments for **Player Name** (with an option to hide your real name), **Health**, **Stamina**, and **Mana**. Each one has multiple display styles - show health as `42/67`, as a percentage `63%`, or as a visual bar `||||||------`. Built-in segments are highlighted with a warm purple tint so you can tell them apart from mod-added ones.

Any mod that supports the NameplateBuilder API can add its own segments too (like elite tier labels, guild tags, or titles), and they all show up in your editor automatically.

## Customize Everything

Open the editor with `/npb` and make nameplates look exactly how you want:

*   **Drag and drop** to reorder segments in any order you like
*   **Choose display formats** - pick how each segment looks (numbers, percentages, bars, etc.)
*   **Custom separators** - change what appears between segments, or remove separators entirely
*   **Add text around segments** - wrap any segment with custom text (e.g. `HP: [42/67]`)
*   **Adjust bar style** - change the fill character used in bar displays
*   **Vertical offset** - slide nameplates up or down with a slider for a floating hologram look
*   **Live preview** - see your changes in real time before saving
*   **Search** - quickly find segments across all installed mods

## Smart Defaults

*   NPC nameplates only appear when you look at them (within 12 blocks). Player nameplates are always visible
*   Player nameplates hide automatically when you crouch (just like in Minecraft)
*   Nameplates disappear when an entity dies instead of lingering
*   New players start with a sensible default setup. Mods can mark their most important segments to be turned on automatically
*   The save button stays greyed out until you actually change something
*   All buttons and controls have tooltips explaining what they do

## Server Admin Controls

Server admins (`nameplatebuilder.admin` permission) get a full control panel:

*   **On/off switches** - Turn nameplates on or off for the whole server, for NPCs only, for players only, per mod, or per world/instance
*   **Lock the chain** - Force all players to see the same segment order. Players get a read-only view while admins can still make changes
*   **Required segments** - Make sure certain segments always show for everyone
*   **Hidden segments** - Remove segments from everyone's view entirely
*   **NPC blacklist** - Pick specific NPC types that should never get nameplates, with a searchable popup
*   **Pattern blacklist** - Use patterns like `Citizen.*` to block entire groups of NPCs at once. Comes with default patterns for Citizen, Mount, and Pet NPCs
*   **Server name and welcome message** - Set a custom name and toggle the join message on or off

When an admin disables something, players see "(Disabled by Admin)" and can't turn it back on.

***

# For Mod Developers

Want to add your own segments? Check out the [documentation](https://docs.nameplatebuilder.frotty27.com/) for the full API reference and a working example mod. You can:

*   Register segments with display names, examples, and multiple format variants
*   Have NameplateBuilder compute your segment text automatically using resolvers, or set it yourself
*   Mark up to 3 of your segments to be enabled by default for new players

***

# Performance

NameplateBuilder is built to be lightweight. Entities with no players nearby are skipped entirely, and results are cached wherever possible. You can test it yourself with `/npbbench <players> <seconds>` (e.g. `/npbbench 50 5` simulates the current amount of entities in your world for a whopping 50 players for 5 seconds and reports how much of the server's tick budget is used). Increasing the time (seconds) gives a more accurate result.

***

# Permissions

| Permission             |Description                                                               |
| ---------------------- |------------------------------------------------------------------------- |
| <code>nameplatebuilder.admin</code> |Shows the Admin section in the UI. Without it, admin controls are hidden. |

***

# Screenshots (to-be updated for v .6!)

### Welcome Messages

![Welcome enabled](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/welcome-enabled.png)

![Welcome disabled](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/welcome-disabled.png)

### Editor Overview

![Editor UI overview](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/editor-overview.png)

### General Settings

![General settings](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/general-settings.png)

### Chain Editor

![Editor with chain](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/editor-chain.png)

![Editor empty](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/editor-empty.png)

### Available Blocks

![Available blocks](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/available-blocks.png)

![Built-in blocks](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/builtin-blocks.png)

### Preview Bar

![Preview](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/preview.png)

### Search and Filter

![Search filter](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/editor-search.png)

### Format Customization

![Formatted chain block](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/chain-formatted.png)

![Format popup](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/format-popup.png) ![Format prefix suffix](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/format-prefix-suffix.png)

### Admin Panels

![Required segment](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/required-segment.png)

![Player disabled](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/player-disabled.png)

![Admin panel](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/admin-required.png)

![Admin disabled](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/admin-disabled.png)

![Admin settings](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/admin-settings.png)

### In-World Nameplates

![In-world nameplate](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/nameplate-ingame.png)

![Custom selection](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/nameplate-custom.png)

![Bar nameplate](https://github.com/TimShol/hytale-nameplate-builder/raw/main/docs/screenshots/nameplate-bar.png)