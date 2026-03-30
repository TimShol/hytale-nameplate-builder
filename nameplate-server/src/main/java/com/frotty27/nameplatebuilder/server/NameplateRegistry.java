package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.frotty27.nameplatebuilder.api.SegmentBuilder;
import com.frotty27.nameplatebuilder.api.SegmentResolver;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class NameplateRegistry implements INameplateRegistry {

    private final Map<SegmentKey, Segment> segments = new ConcurrentHashMap<>();

    @Override
    public SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(target, "target");

        String pluginId = toPluginId(plugin);
        String pluginName = plugin.getName();
        String pluginAuthor = plugin.getIdentifier().getGroup();

        if (pluginName != null && pluginName.contains(":")) {
            pluginName = pluginName.substring(pluginName.indexOf(':') + 1).trim();
        }
        if (pluginAuthor != null && pluginAuthor.contains(":")) {
            pluginAuthor = pluginAuthor.substring(0, pluginAuthor.indexOf(':'));
        }
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example,
                List.of(), false, false, null, null, 1));
        return new SegmentBuilderImpl(key, segments);
    }

    SegmentBuilder defineBuiltIn(String pluginId, String segmentId, String displayName,
                                 SegmentTarget target, String example) {
        String pluginName = "NameplateBuilder";
        String pluginAuthor = pluginId.contains(":") ? pluginId.substring(0, pluginId.indexOf(':')) : pluginId;
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example,
                List.of(), true, false, null, null, 1));
        return new SegmentBuilderImpl(key, segments);
    }

    @Override
    public void defineVariants(JavaPlugin plugin, String segmentId, List<String> variantNames) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(variantNames, "variantNames");

        SegmentKey key = new SegmentKey(toPluginId(plugin), segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    List.copyOf(variantNames), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks()));
        }
    }

    void defineVariantsInternal(String pluginId, String segmentId, List<String> variantNames) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    List.copyOf(variantNames), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks()));
        }
    }

    void setSupportsPrefixSuffix(String pluginId, String segmentId) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), true,
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks()));
        }
    }

    @Override
    public void undefine(JavaPlugin plugin, String segmentId) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        segments.remove(new SegmentKey(toPluginId(plugin), segmentId));
    }

    @Override
    public void undefineAll(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String pluginId = toPluginId(plugin);
        segments.keySet().removeIf(key -> key.pluginId().equals(pluginId));
    }

    void clear() {
        segments.clear();
    }

    Map<SegmentKey, Segment> getSegments() {
        return segments;
    }

    static String toPluginId(JavaPlugin plugin) {
        var id = plugin.getIdentifier();
        return id.getGroup() + ":" + id.getName();
    }

    record Segment(String pluginId, String pluginName, String pluginAuthor,
                   String displayName, SegmentTarget target, String example,
                   List<String> variants, boolean builtIn, boolean supportsPrefixSuffix,
                   SegmentResolver resolver,
                   ComponentType<EntityStore, ?> requiredComponent,
                   int cacheTicks) {
    }
}
