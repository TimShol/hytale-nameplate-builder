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

/**
 * Example {@link EntityTickingSystem} that updates a per-entity "lifetime" segment.
 *
 * <p>Each entity's lifetime is tracked individually by storing the spawn tick
 * inside the {@link NameplateData} component under a hidden key
 * ({@code "_lifetime_tick"}). On each tick, the system reads the spawn tick,
 * computes the elapsed time, and formats it as human-readable text
 * (e.g. {@code "1m 23s"}).</p>
 *
 * <p>This demonstrates that calling {@link NameplateData#setText} each tick does
 * <b>not</b> cause flashing — the component stays on the entity and only its
 * internal map value is updated in place.</p>
 */
final class LifetimeNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String SEGMENT_ID = "lifetime";
    /** Hidden key storing the global tick at which this entity was initialized. */
    private static final String SPAWN_TICK_KEY = "_lifetime_tick";

    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    /** Global tick counter, incremented once per system tick. */
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
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        globalTick++;

        NameplateData data = chunk.getComponent(index, nameplateDataType);
        if (data == null) {
            return;
        }

        // Only update entities that have a "lifetime" entry already set
        String existing = data.getText(SEGMENT_ID);
        if (existing == null) {
            return;
        }

        // Read or initialize the per-entity spawn tick
        String spawnTickStr = data.getText(SPAWN_TICK_KEY);
        long spawnTick;
        if (spawnTickStr == null) {
            // First tick for this entity — record the current global tick
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

        // Compute elapsed time for this specific entity
        long elapsed = globalTick - spawnTick;
        int seconds = (int) (elapsed / 20); // 20 ticks per second
        int minutes = seconds / 60;
        int secs = seconds % 60;

        String text;
        if (minutes > 0) {
            text = minutes + "m " + secs + "s";
        } else {
            text = secs + "s";
        }

        data.setText(SEGMENT_ID, text);
    }
}
