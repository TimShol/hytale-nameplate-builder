package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface SegmentBuilder {

    SegmentBuilder resolver(SegmentResolver resolver);

    SegmentBuilder requires(ComponentType<EntityStore, ?> componentType);

    SegmentBuilder cacheTicks(int ticks);

    SegmentBuilder enabledByDefault();

    SegmentBuilder enabledByDefault(SegmentTarget target);

    SegmentBuilder overridable();
}
