package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

final class LifetimeNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String SEGMENT_ID = "lifetime";
    private static final String SPAWN_TICK_KEY = "_lifetime_tick";

    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    private long globalTick;

    LifetimeNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.nameplateDataType = nameplateDataType;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(nameplateDataType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        globalTick++;

        NameplateData data = chunk.getComponent(index, nameplateDataType);
        if (data == null) {
            return;
        }

        String existing = data.getText(SEGMENT_ID);
        if (existing == null) {
            return;
        }

        String spawnTickStr = data.getText(SPAWN_TICK_KEY);
        long spawnTick;
        if (spawnTickStr == null) {
            spawnTick = globalTick;
            data.setText(SPAWN_TICK_KEY, String.valueOf(spawnTick));
        } else {
            try {
                spawnTick = Long.parseLong(spawnTickStr);
            } catch (NumberFormatException e) {
                spawnTick = globalTick;
                data.setText(SPAWN_TICK_KEY, String.valueOf(spawnTick));
            }
        }

        long elapsed = globalTick - spawnTick;
        int seconds = (int) (elapsed / 20);
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        String text;
        if (minutes > 0) {
            text = minutes + "m " + remainingSeconds + "s";
        } else {
            text = remainingSeconds + "s";
        }

        data.setText(SEGMENT_ID, text);
    }
}
