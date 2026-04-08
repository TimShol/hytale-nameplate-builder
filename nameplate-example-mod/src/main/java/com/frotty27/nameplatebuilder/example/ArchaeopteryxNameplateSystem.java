package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

final class ArchaeopteryxNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String ROLE_NAME = "Archaeopteryx";

    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    ArchaeopteryxNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.npcType = NPCEntity.getComponentType();
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

    private void initializeEntity(Ref<EntityStore> entityRef,
                                  Store<EntityStore> store,
                                  CommandBuffer<EntityStore> commandBuffer) {

        String healthText = readHealthText(store, entityRef);

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
        commandBuffer.putComponent(entityRef, nameplateDataType, data);
    }

    private void updateEntity(Ref<EntityStore> entityRef,
                              Store<EntityStore> store,
                              NameplateData data) {

        if (data.getText("health") != null) {
            data.setText("health", readHealthText(store, entityRef));
        }
    }

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
