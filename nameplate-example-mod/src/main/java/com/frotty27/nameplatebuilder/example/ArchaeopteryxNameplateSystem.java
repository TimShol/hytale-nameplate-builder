package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Spawn initializer and per-tick updater for Archaeopteryx NPC nameplates.
 *
 * <p>This is the <b>recommended pattern</b> for giving NPCs nameplate data on spawn
 * and keeping live data (like health) up to date. To adapt it for your own mod:</p>
 * <ol>
 *   <li>Change {@link #ROLE_NAME} to match your target NPC role (e.g. {@code "Kweebec"}).</li>
 *   <li>Replace the segment IDs and default values in {@link #initializeEntity} with your own.</li>
 *   <li>Adjust the per-tick update logic in {@link #updateEntity} for your segments.</li>
 *   <li>Register the system in your plugin's {@code setup()} via
 *       {@code getEntityStoreRegistry().registerSystem(new YourSystem(nameplateDataType))}.</li>
 * </ol>
 *
 * <h3>How it works</h3>
 * <p>The system queries all entities with an {@link NPCEntity} component. Each tick:</p>
 * <ul>
 *   <li><b>New entities</b> (no {@link NameplateData}): seeds all default segments and
 *       ensures a {@link Nameplate} component is present via the {@link CommandBuffer}.</li>
 *   <li><b>Existing entities</b> (has {@link NameplateData}): updates the "health"
 *       segment from the entity's actual {@link EntityStatMap} values, so the
 *       nameplate reflects real-time health unique to each entity.</li>
 * </ul>
 *
 * <h3>Why CommandBuffer?</h3>
 * <p>Inside an {@code EntityTickingSystem}, the {@code Store} is locked for writes.
 * Calling {@code store.addComponent()} directly throws
 * {@code IllegalStateException: Store is currently processing}. All structural
 * changes (add/remove components) must go through the {@link CommandBuffer}, which
 * defers execution until after the system finishes. Reading via
 * {@code store.getComponent()} and mutating component data in place are safe.</p>
 *
 * @see NameplateExamplePlugin#setup()
 * @see LifetimeNameplateSystem
 */
final class ArchaeopteryxNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String ROLE_NAME = "Archaeopteryx";

    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, Nameplate> nameplateType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    ArchaeopteryxNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.npcType = NPCEntity.getComponentType();
        this.nameplateType = Nameplate.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(npcType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npc = chunk.getComponent(index, npcType);
        if (npc == null || !ROLE_NAME.equals(npc.getRoleName())) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        NameplateData existing = store.getComponent(entityRef, nameplateDataType);

        if (existing == null) {
            initializeEntity(entityRef, store, commandBuffer);
        } else {
            updateEntity(entityRef, store, existing);
        }
    }

    /**
     * One-shot initialization for a newly spawned Archaeopteryx.
     * Seeds all NPC-applicable segments with default values.
     */
    private void initializeEntity(Ref<EntityStore> entityRef,
                                  Store<EntityStore> store,
                                  CommandBuffer<EntityStore> commandBuffer) {

        // Ensure the entity has a Nameplate component (required by the aggregator)
        Nameplate nameplate = store.getComponent(entityRef, nameplateType);
        if (nameplate == null) {
            commandBuffer.addComponent(entityRef, nameplateType, new Nameplate());
        }

        // Read actual health for the initial value
        String healthText = readHealthText(store, entityRef);

        // Seed all NPC-applicable segments
        NameplateData data = new NameplateData();
        data.setText("health", healthText);
        data.setText("tier", "[Elite]");
        data.setText("level", "Lv. 5");
        data.setText("guild", "[Warriors]");
        data.setText("title", "The Brave");
        data.setText("faction", "Wilderness");
        data.setText("mood", "Calm");
        data.setText("bounty", "50g");
        data.setText("lifetime", "0s");
        commandBuffer.addComponent(entityRef, nameplateDataType, data);
    }

    /**
     * Per-tick update for entities that already have nameplate data.
     * Updates the "health" segment from the entity's real stats.
     */
    private void updateEntity(Ref<EntityStore> entityRef,
                              Store<EntityStore> store,
                              NameplateData data) {

        // Only update health if the entity has that segment set
        if (data.getText("health") != null) {
            data.setText("health", readHealthText(store, entityRef));
        }
    }

    /**
     * Read the entity's current and max health from its {@link EntityStatMap}.
     *
     * @return formatted health string, e.g. {@code "42/67"}
     */
    private String readHealthText(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap != null) {
            EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
            if (health != null) {
                int current = Math.round(health.get());
                int max = Math.round(health.getMax());
                return current + "/" + max;
            }
        }
        return "?/?";
    }
}
