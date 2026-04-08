package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

@FunctionalInterface
public interface SegmentResolver {

    String resolve(Store<EntityStore> store, Ref<EntityStore> entityRef, int variantIndex);
}
