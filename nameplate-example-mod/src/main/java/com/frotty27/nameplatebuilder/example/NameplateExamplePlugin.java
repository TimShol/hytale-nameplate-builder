package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

/**
 * Example mod demonstrating the NameplateBuilder API.
 *
 * <p>This plugin shows the three main patterns that modders need:</p>
 *
 * <h3>1. Describe segments (UI metadata)</h3>
 * <p>In {@code setup()}, call {@link NameplateAPI#describe} to register a
 * human-readable display name for each segment. This is optional but
 * recommended — without it the player UI shows the raw segment ID.</p>
 *
 * <h3>2. Attach nameplates to NPCs on spawn</h3>
 * <p>The {@link ArchaeopteryxNameplateSystem} demonstrates how to detect newly
 * spawned NPCs by role name and seed their initial nameplate data. It extends
 * {@code EntityTickingSystem}, queries for the {@code NPCEntity} component,
 * checks that the entity doesn't already have {@link NameplateData}, and
 * calls {@link NameplateAPI#register} to create the component with default
 * segment values. This pattern works for any NPC role — just change the
 * role name constant.</p>
 *
 * <h3>3. Update segments every tick</h3>
 * <p>The {@link LifetimeNameplateSystem} updates the "lifetime" segment text
 * every server tick, showing that calling {@link NameplateAPI#register} (or
 * {@link NameplateData#setText} directly) each tick does <b>not</b> cause
 * flashing — the component stays on the entity and only its internal map
 * value changes in place.</p>
 *
 * <h3>Additional runtime examples</h3>
 * <p>The utility methods below ({@link #initializeEntity}, {@link #markElite},
 * {@link #updateHealth}, etc.) demonstrate event-driven updates: setting,
 * updating, and removing individual segments at any time.</p>
 *
 * @see ArchaeopteryxNameplateSystem
 * @see LifetimeNameplateSystem
 * @see NameplateAPI
 */
public final class NameplateExamplePlugin extends JavaPlugin {

    public NameplateExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        // ── Describe segments for the UI ──
        // Each describe() call registers a human-readable name and target
        // for the Nameplate Builder UI. The SegmentTarget enum tells players
        // which entity types the segment applies to (shown as a tag in the UI).
        // This is optional — undescribed segments still work but show the raw ID.

        // Segments applicable to all entities (shown in both NPC and Player tabs)
        // Note: "health" and "player-name" are built-in segments provided by NameplateBuilder
        // and do not need to be described here. They appear automatically in the UI.
        NameplateAPI.describe(this, "level", "Level", SegmentTarget.ALL, "Lv. 42");
        NameplateAPI.describeVariants(this, "level", List.of("Compact", "Full", "Number Only"));
        NameplateAPI.describe(this, "title", "Title", SegmentTarget.ALL, "The Brave");
        NameplateAPI.describe(this, "custom-tag", "Custom Tag", SegmentTarget.ALL, "[Custom]");

        // NPC-only segments
        NameplateAPI.describe(this, "buff", "Active Buff", SegmentTarget.NPCS, "Burning");
        NameplateAPI.describe(this, "faction", "Faction", SegmentTarget.NPCS, "<Undead>");
        NameplateAPI.describe(this, "mood", "Mood", SegmentTarget.NPCS, "Aggressive");
        NameplateAPI.describe(this, "quest", "Active Quest", SegmentTarget.NPCS, "[!!] Slay 10");
        NameplateAPI.describe(this, "bounty", "Bounty", SegmentTarget.NPCS, "500g");
        NameplateAPI.describe(this, "lifetime", "Lifetime", SegmentTarget.NPCS, "3m 24s");

        // Player-only segments
        NameplateAPI.describe(this, "guild", "Guild Tag", SegmentTarget.PLAYERS, "[Warriors]");
        NameplateAPI.describe(this, "score", "Score", SegmentTarget.PLAYERS, "1,250");
        NameplateAPI.describe(this, "rank", "Server Rank", SegmentTarget.PLAYERS, "VIP+");
        NameplateAPI.describe(this, "clan", "Clan", SegmentTarget.PLAYERS, "<Phoenix>");
        NameplateAPI.describe(this, "vip-label", "VIP Label", SegmentTarget.PLAYERS, "VIP");

        // ── Register tick-based systems ──
        ComponentType<EntityStore, NameplateData> nameplateDataType = NameplateAPI.getComponentType();

        // Attaches NameplateData to Archaeopteryx NPCs on their first tick (spawn).
        getEntityStoreRegistry().registerSystem(new ArchaeopteryxNameplateSystem(nameplateDataType));

        // Updates the "lifetime" segment every tick on any entity that has it set,
        // demonstrating per-tick text updates without flashing.
        getEntityStoreRegistry().registerSystem(new LifetimeNameplateSystem(nameplateDataType));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Runtime examples — call these from your systems / event handlers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Set up default nameplate text on an entity (e.g. on spawn).
     *
     * <p>Call this once when an entity enters the world. The component
     * persists — no need to re-register every tick.</p>
     *
     * <p>The "lifetime" segment is seeded with "0s" here; the
     * {@link LifetimeNameplateSystem} will update it every tick.</p>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void initializeEntity(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.register(store, entityRef, "health", "67/67");
        NameplateAPI.register(store, entityRef, "guild", "[Warriors]");
        NameplateAPI.register(store, entityRef, "title", "The Brave");
        NameplateAPI.register(store, entityRef, "rank", "VIP");
        NameplateAPI.register(store, entityRef, "lifetime", "0s");
    }

    /**
     * Mark an entity as elite — updates just the "tier" segment.
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void markElite(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.register(store, entityRef, "tier", "[Elite]");
    }

    /**
     * Remove an entity's elite status.
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void removeElite(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.remove(store, entityRef, "tier");
    }

    /**
     * Update health when it changes (event-driven, not every tick).
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     * @param current   current health
     * @param max       max health
     */
    public void updateHealth(Store<EntityStore> store, Ref<EntityStore> entityRef, int current, int max) {
        NameplateAPI.register(store, entityRef, "health", current + "/" + max);
    }

    /**
     * Apply boss overrides — multiple segments on the same entity.
     *
     * <p>Different systems can independently set their own segments.
     * The component holds all entries in a single map — no conflicts.</p>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void applyBossOverrides(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.register(store, entityRef, "health", "1000/1000");
        NameplateAPI.register(store, entityRef, "tier", "[World Boss]");
        NameplateAPI.register(store, entityRef, "quest", "[!!] Final Boss");
    }

    /**
     * Remove the buff segment at runtime (e.g. buff expired).
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void removeBuff(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.remove(store, entityRef, "buff");
    }

    /**
     * Remove all nameplate data from an entity entirely.
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     */
    public void clearAll(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        store.tryRemoveComponent(entityRef, NameplateAPI.getComponentType());
    }
}
