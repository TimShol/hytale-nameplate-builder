package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.frotty27.nameplatebuilder.api.SegmentBuilder;
import com.frotty27.nameplatebuilder.api.SegmentResolver;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class NameplateRegistry implements INameplateRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<SegmentKey, Segment> segments = new ConcurrentHashMap<>();
    private final Map<String, SegmentKey> segmentIdIndex = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger();
    private final AtomicInteger nextSegmentId = new AtomicInteger(0);
    private final Set<String> integratedModNames = ConcurrentHashMap.newKeySet();

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
        SegmentKey key = new SegmentKey(pluginId, segmentId, nextSegmentId.getAndIncrement());
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example,
                List.of(), false, false, null, null, 1, null));
        segmentIdIndex.putIfAbsent(segmentId, key);
        version.incrementAndGet();
        String modName = extractModNameFromPluginId(pluginId);
        if (!modName.isEmpty()) {
            integratedModNames.add(modName.toLowerCase(Locale.ROOT));
        }
        return new SegmentBuilderImpl(key, segments, version);
    }

    boolean isModIntegrated(String modName) {
        if (modName == null) return false;
        return integratedModNames.contains(modName.toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    SegmentBuilder defineBuiltIn(String pluginId, String segmentId, String displayName,
                                 SegmentTarget target, String example) {
        String pluginName = "NameplateBuilder";
        String pluginAuthor = pluginId.contains(":") ? pluginId.substring(0, pluginId.indexOf(':')) : pluginId;
        SegmentKey key = new SegmentKey(pluginId, segmentId, nextSegmentId.getAndIncrement());
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example,
                List.of(), true, false, null, null, 1, null));
        segmentIdIndex.put(segmentId, key);
        version.incrementAndGet();
        return new SegmentBuilderImpl(key, segments, version);
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
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks(), existing.enabledByDefault()));
            version.incrementAndGet();
        }
    }

    void defineVariantsInternal(String pluginId, String segmentId, List<String> variantNames) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    List.copyOf(variantNames), existing.builtIn(), existing.supportsPrefixSuffix(),
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks(), existing.enabledByDefault()));
            version.incrementAndGet();
        }
    }

    void setSupportsPrefixSuffix(String pluginId, String segmentId) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), true,
                    existing.resolver(), existing.requiredComponent(), existing.cacheTicks(), existing.enabledByDefault()));
            version.incrementAndGet();
        }
    }

    @Override
    public void undefine(JavaPlugin plugin, String segmentId) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        if (segments.remove(new SegmentKey(toPluginId(plugin), segmentId)) != null) {
            version.incrementAndGet();
        }
    }

    @Override
    public void undefineAll(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String pluginId = toPluginId(plugin);
        if (segments.keySet().removeIf(key -> key.pluginId().equals(pluginId))) {
            version.incrementAndGet();
        }
    }

    void clear() {
        segments.clear();
        segmentIdIndex.clear();
        version.incrementAndGet();
    }

    Map<SegmentKey, Segment> getSegments() {
        return segments;
    }

    SegmentKey findBySegmentId(String segmentId) {
        return segmentIdIndex.get(segmentId);
    }

    int getVersion() {
        return version.get();
    }

    int getMaxSegmentId() {
        return nextSegmentId.get();
    }

    static String toPluginId(JavaPlugin plugin) {
        var identifier = plugin.getIdentifier();
        return identifier.getGroup() + ":" + identifier.getName();
    }

    Set<SegmentKey> getEnabledByDefaultKeys() {
        Map<String, Integer> modDefaultCount = new HashMap<>();
        Set<SegmentKey> result = new LinkedHashSet<>();
        for (Map.Entry<SegmentKey, Segment> entry : segments.entrySet()) {
            Segment segment = entry.getValue();
            if (segment.enabledByDefault() != null) {
                String modId = segment.pluginId();
                int count = modDefaultCount.getOrDefault(modId, 0);
                if (count < 3) {
                    result.add(entry.getKey());
                    modDefaultCount.put(modId, count + 1);
                } else if (count == 3) {
                    LOGGER.atWarning().log("[NPB] Mod '%s' has more than 3 enabledByDefault segments; ignoring '%s'",
                            modId, entry.getKey().segmentId());
                    modDefaultCount.put(modId, count + 1);
                }
            }
        }
        return result;
    }

    private static String extractModNameFromPluginId(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return "";
        }
        int colonIndex = pluginId.indexOf(':');
        String name = colonIndex >= 0 ? pluginId.substring(colonIndex + 1) : pluginId;
        return name.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    record Segment(String pluginId, String pluginName, String pluginAuthor,
                   String displayName, SegmentTarget target, String example,
                   List<String> variants, boolean builtIn, boolean supportsPrefixSuffix,
                   SegmentResolver resolver,
                   ComponentType<EntityStore, ?> requiredComponent,
                   int cacheTicks, SegmentTarget enabledByDefault) {
    }
}
