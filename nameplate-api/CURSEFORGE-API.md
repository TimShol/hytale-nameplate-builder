# NameplateBuilder API

A lightweight compile-time API **for mod developers** who want to add their own nameplate segments to [NameplateBuilder](https://www.curseforge.com/hytale/mods/nameplatebuilder).

[![Docs](https://img.shields.io/badge/Documentation_/_Guide-7C3AED?style=for-the-badge&logo=bookstack&logoColor=white)](https://docs.nameplatebuilder.frotty27.com/)
[![GitHub](https://img.shields.io/badge/GitHub-NameplateBuilder_API-7C3AED?style=for-the-badge&logo=github&logoColor=white)](https://github.com/TimShol/hytale-nameplate-builder)
[![Discord](https://img.shields.io/badge/Discord-Join_The_Community-7C3AED?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/t72K6sm3S2)

***

## Who Needs This?

**Mod developers only.** This jar is a compile-time dependency for building mods that integrate with NameplateBuilder. It does not go in the `/mods` folder and should not be listed as a required CurseForge dependency for your mod.

*   **Server owners:** You only need the [NameplateBuilder server plugin](https://www.curseforge.com/hytale/mods/nameplatebuilder). Do not download this API jar.
*   **Mod developers:** Download this jar, place it in your `libs/` folder, and add it as a `compileOnly` dependency in your `build.gradle`.

If your mod fully integrates with NameplateBuilder (meaning you use it for nameplates instead of managing them yourself), mark the **server plugin** (not this API) as a required dependency in your CurseForge Project Settings.

***

## Setup

**`build.gradle`:**
```groovy
dependencies {
    compileOnly files('libs/NameplateBuilder-API-2.2.0.jar')
}
```

**`manifest.json`:**
```json
{
  "Dependencies": {
    "Frotty27:NameplateBuilder": ">=4.260326.7"
  }
}
```

***

## What's In the API?

| Class | Purpose |
| :--- | :--- |
| `NameplateAPI` | Main entry point - define segments, set text, clear text |
| `SegmentBuilder` | Fluent builder for resolvers, caching, defaults, and replacements |
| `SegmentResolver` | Functional interface for computing segment text per entity |
| `NameplateData` | ECS component that holds segment text on entities |
| `SegmentTarget` | Enum: `ALL`, `PLAYERS`, or `NPCS` |

***

## Quick Example

```java
@Override
protected void setup() {
    NameplateAPI.define(this, "bounty", "Bounty",
            SegmentTarget.NPCS, "$500")
        .enabledByDefault(SegmentTarget.NPCS)
        .resolver((store, entityRef, variantIndex) -> {
            BountyComponent bounty = store.getComponent(entityRef, bountyType);
            if (bounty == null) return null;
            return "$" + bounty.getAmount();
        });
}
```

That's it. No tick systems needed. NameplateBuilder calls your resolver automatically for every visible entity. Players can add "Bounty" to their chain, reorder it, and pick display formats via `/npb`.

See the [Quick Start guide](https://docs.nameplatebuilder.frotty27.com/modding/quick-start) for a full walkthrough.

***

## Additional Files

The download includes the `-javadoc.jar` and `-sources.jar` alongside the main API jar. Place all three in your `libs/` folder for full IDE support (autocomplete, inline docs, click-to-source).

***