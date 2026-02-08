package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.frotty27.nameplatebuilder.api.INameplateSegmentHandle;
import com.frotty27.nameplatebuilder.api.INameplateTextProvider;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class NameplateRegistry implements INameplateRegistry {

    private final Map<SegmentKey, Segment> segments = new ConcurrentHashMap<>();

    @Override
    public INameplateSegmentHandle register(JavaPlugin plugin,
                                            String segmentId,
                                            String displayName,
                                            INameplateTextProvider provider) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(provider, "provider");

        String pluginId = toPluginId(plugin);
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, plugin.getName(), segmentId, displayName, provider));

        return () -> unregister(plugin, segmentId);
    }

    @Override
    public INameplateSegmentHandle register(JavaPlugin plugin,
                                            String segmentId,
                                            String displayName,
                                            String text) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(text, "text");

        String pluginId = toPluginId(plugin);
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment segment = new Segment(pluginId, plugin.getName(), segmentId, displayName, null);
        segment.setGlobalText(text);
        segments.put(key, segment);

        return () -> unregister(plugin, segmentId);
    }

    @Override
    public void unregister(JavaPlugin plugin, String segmentId) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        segments.remove(new SegmentKey(toPluginId(plugin), segmentId));
    }

    @Override
    public void unregisterAll(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String pluginId = toPluginId(plugin);
        segments.keySet().removeIf(key -> key.pluginId().equals(pluginId));
    }

    void clear() {
        segments.clear();
    }

    @Override
    public void setNameplateText(JavaPlugin plugin, String segmentId, UUID entityUuid, String text) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(entityUuid, "entityUuid");

        Segment segment = requireSegment(plugin, segmentId);
        if (text == null || text.isBlank()) {
            segment.entityText.remove(entityUuid);
        } else {
            segment.entityText.put(entityUuid, text);
        }
    }

    @Override
    public void setNameplateText(JavaPlugin plugin, String segmentId, String text) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        requireSegment(plugin, segmentId).setGlobalText(text);
    }

    Map<SegmentKey, Segment> getSegments() {
        return segments;
    }

    private Segment requireSegment(JavaPlugin plugin, String segmentId) {
        String pluginId = toPluginId(plugin);
        Segment segment = segments.get(new SegmentKey(pluginId, segmentId));
        if (segment == null) {
            throw new IllegalStateException("Segment not registered: " + pluginId + ":" + segmentId);
        }
        return segment;
    }

    static String toPluginId(JavaPlugin plugin) {
        var id = plugin.getIdentifier();
        return id.getGroup() + ":" + id.getName();
    }

    // ── Segment ──

    static final class Segment {
        private final String pluginId;
        private final String pluginName;
        private final String segmentId;
        private final String displayName;
        private final INameplateTextProvider provider;
        private final Map<UUID, String> entityText;
        private volatile String globalText;

        Segment(String pluginId, String pluginName, String segmentId,
                String displayName, INameplateTextProvider provider) {
            this.pluginId = pluginId;
            this.pluginName = pluginName;
            this.segmentId = segmentId;
            this.displayName = displayName;
            this.provider = provider;
            this.entityText = new ConcurrentHashMap<>();
        }

        String getPluginId()                 { return pluginId; }
        String getPluginName()               { return pluginName; }
        String getSegmentId()                { return segmentId; }
        String getDisplayName()              { return displayName; }
        INameplateTextProvider getProvider()   { return provider; }
        Map<UUID, String> getEntityText()    { return entityText; }
        String getGlobalText()               { return globalText; }
        void setGlobalText(String text)      { this.globalText = text; }

        @Override
        public String toString() {
            return "Segment[" + pluginId + ":" + segmentId + "]";
        }
    }
}
