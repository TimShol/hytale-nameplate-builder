package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.SegmentBuilder;
import com.frotty27.nameplatebuilder.api.SegmentResolver;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

final class SegmentBuilderImpl implements SegmentBuilder {

    private final SegmentKey key;
    private final Map<SegmentKey, NameplateRegistry.Segment> segments;

    SegmentBuilderImpl(SegmentKey key, Map<SegmentKey, NameplateRegistry.Segment> segments) {
        this.key = key;
        this.segments = segments;
    }

    @Override
    public SegmentBuilder resolver(SegmentResolver resolver) {
        NameplateRegistry.Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new NameplateRegistry.Segment(
                    existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), existing.supportsPrefixSuffix(),
                    resolver, existing.requiredComponent(), existing.cacheTicks()));
        }
        return this;
    }

    @Override
    public SegmentBuilder requires(ComponentType<EntityStore, ?> componentType) {
        NameplateRegistry.Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new NameplateRegistry.Segment(
                    existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), componentType, existing.cacheTicks()));
        }
        return this;
    }

    @Override
    public SegmentBuilder cacheTicks(int ticks) {
        NameplateRegistry.Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new NameplateRegistry.Segment(
                    existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), existing.requiredComponent(), Math.max(1, ticks)));
        }
        return this;
    }
}
