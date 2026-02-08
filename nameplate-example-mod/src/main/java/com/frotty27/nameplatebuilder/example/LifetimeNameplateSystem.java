package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Example {@link EntityTickingSystem} that updates a nameplate segment every tick.
 *
 * <p>This demonstrates that calling {@link NameplateAPI#register} each tick does
 * <b>not</b> cause flashing — the {@link NameplateData} component stays on the
 * entity and only its internal map value is updated in place.</p>
 *
 * <p>Each tick, the "lifetime" segment text is recalculated from an internal
 * counter, showing how long (in seconds) the entity has been alive.</p>
 */
final class LifetimeNameplateSystem extends EntityTickingSystem<EntityStore> {

    private static final String SEGMENT_ID = "lifetime";

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleType;
    private final ComponentType<EntityStore, Nameplate> nameplateType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;

    private int tickCounter;

    LifetimeNameplateSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.visibleType = EntityTrackerSystems.Visible.getComponentType();
        this.nameplateType = Nameplate.getComponentType();
        this.nameplateDataType = nameplateDataType;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        // Only tick entities that have both Visible + NameplateData components
        return Archetype.of(visibleType, nameplateDataType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        // The tickCounter is shared across all entities — it counts global server ticks.
        // Each entity just reads the current value and formats it as seconds.
        // In a real mod you'd track per-entity spawn time instead.
        tickCounter++;

        NameplateData data = chunk.getComponent(index, nameplateDataType);
        if (data == null) {
            return;
        }

        // Only update entities that have a "lifetime" entry already set
        String existing = data.getText(SEGMENT_ID);
        if (existing == null) {
            return;
        }

        // Update the text in place — no component add/remove, no flashing
        int seconds = tickCounter / 20; // 20 ticks per second
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
