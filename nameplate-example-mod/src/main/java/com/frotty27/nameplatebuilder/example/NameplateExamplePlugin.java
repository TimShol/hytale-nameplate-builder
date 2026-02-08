package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Example mod demonstrating the NameplateBuilder API.
 *
 * <h3>Modder workflow:</h3>
 * <ol>
 *   <li>In {@code setup()} — call {@link NameplateAPI#describe} to give your
 *       segments a human-readable name in the Nameplate Builder UI.</li>
 *   <li>At runtime — call {@link NameplateAPI#register} to set nameplate text on
 *       an entity, and {@link NameplateAPI#remove} to clear it.</li>
 * </ol>
 *
 * <p>The component persists on the entity. Call {@code register()} again when the
 * value changes — no need to remove and re-register. The aggregator reads the
 * current values every tick automatically.</p>
 *
 * <h3>Tick-based example:</h3>
 * <p>The "lifetime" segment is updated every tick by {@link LifetimeNameplateSystem}
 * to demonstrate that updating text each tick does not cause flashing — the component
 * stays on the entity and only its internal map value changes.</p>
 */
public final class NameplateExamplePlugin extends JavaPlugin {

    public NameplateExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        // ── Describe segments for the UI ──
        // Each describe() call registers a human-readable name for the
        // Nameplate Builder UI. This is optional — undescribed segments
        // still work but show their raw ID in the UI.

        NameplateAPI.describe(this, "health", "Health Bar");
        NameplateAPI.describe(this, "tier", "Elite Tier");
        NameplateAPI.describe(this, "level", "Level");
        NameplateAPI.describe(this, "buff", "Active Buff");
        NameplateAPI.describe(this, "guild", "Guild Tag");
        NameplateAPI.describe(this, "title", "Player Title");
        NameplateAPI.describe(this, "faction", "Faction");
        NameplateAPI.describe(this, "score", "Score");
        NameplateAPI.describe(this, "rank", "Server Rank");
        NameplateAPI.describe(this, "mood", "Mood");
        NameplateAPI.describe(this, "quest", "Active Quest");
        NameplateAPI.describe(this, "clan", "Clan");
        NameplateAPI.describe(this, "bounty", "Bounty");
        NameplateAPI.describe(this, "status", "Online Status");
        NameplateAPI.describe(this, "custom-tag", "Custom Tag");
        NameplateAPI.describe(this, "vip-label", "VIP Label");
        NameplateAPI.describe(this, "lifetime", "Lifetime");

        // ── Register tick-based system ──
        // The LifetimeNameplateSystem updates the "lifetime" segment every tick
        // on any entity that has it set, demonstrating per-tick text updates.
        ComponentType<EntityStore, NameplateData> nameplateDataType = NameplateAPI.getComponentType();
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
        NameplateAPI.register(store, entityRef, "status", "Online");
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
