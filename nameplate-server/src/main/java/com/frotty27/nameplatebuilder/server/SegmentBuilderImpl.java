package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.SegmentBuilder;
import com.frotty27.nameplatebuilder.api.SegmentResolver;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class SegmentBuilderImpl implements SegmentBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ENABLED_BY_DEFAULT_PER_MOD = 3;
    private static final Map<String, Integer> enabledByDefaultCounts = new ConcurrentHashMap<>();

    private final SegmentKey key;
    private final Map<SegmentKey, NameplateRegistry.Segment> segments;
    private final AtomicInteger version;

    SegmentBuilderImpl(SegmentKey key, Map<SegmentKey, NameplateRegistry.Segment> segments, AtomicInteger version) {
        this.key = key;
        this.segments = segments;
        this.version = version;
    }

    @Override
    public SegmentBuilder resolver(SegmentResolver resolver) {
        NameplateRegistry.Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new NameplateRegistry.Segment(
                    existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), existing.supportsPrefixSuffix(),
                    resolver, existing.requiredComponent(), existing.cacheTicks(), existing.enabledByDefault()));
            version.incrementAndGet();
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
                    existing.resolver(), componentType, existing.cacheTicks(), existing.enabledByDefault()));
            version.incrementAndGet();
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
                    existing.resolver(), existing.requiredComponent(), Math.max(1, ticks), existing.enabledByDefault()));
            version.incrementAndGet();
        }
        return this;
    }

    @Override
    public SegmentBuilder enabledByDefault() {
        return enabledByDefault(SegmentTarget.ALL);
    }

    @Override
    public SegmentBuilder enabledByDefault(SegmentTarget target) {
        NameplateRegistry.Segment existing = segments.get(key);
        if (existing != null) {
            if (target != null) {
                String modId = existing.pluginId();
                int count = enabledByDefaultCounts.getOrDefault(modId, 0);
                if (count >= MAX_ENABLED_BY_DEFAULT_PER_MOD) {
                    LOGGER.atWarning().log("[NPB] Mod '%s' already has %d segments marked enabledByDefault (max %d). Ignoring for segment '%s'.",
                            modId, count, MAX_ENABLED_BY_DEFAULT_PER_MOD, key.segmentId());
                    return this;
                }
                enabledByDefaultCounts.merge(modId, 1, Integer::sum);
            } else if (existing.enabledByDefault() != null) {
                String modId = existing.pluginId();
                enabledByDefaultCounts.computeIfPresent(modId, (_, count) -> count <= 1 ? null : count - 1);
            }
            segments.put(key, new NameplateRegistry.Segment(
                    existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks(), target));
            version.incrementAndGet();
        }
        return this;
    }
}
