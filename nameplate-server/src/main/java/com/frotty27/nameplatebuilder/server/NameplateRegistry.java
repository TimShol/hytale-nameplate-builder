package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side implementation of {@link INameplateRegistry}.
 *
 * <p>Stores UI metadata (display name, author, plugin info) for each described
 * segment. The aggregator uses this metadata to match component entries to
 * human-readable UI blocks. Segments that are not described still work at
 * runtime — the UI falls back to showing the raw segment ID.</p>
 */
final class NameplateRegistry implements INameplateRegistry {

    private final Map<SegmentKey, Segment> segments = new ConcurrentHashMap<>();

    @Override
    public void describe(JavaPlugin plugin, String segmentId, String displayName) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayName, "displayName");

        String pluginId = toPluginId(plugin);
        String pluginAuthor = plugin.getIdentifier().getGroup();
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, plugin.getName(), pluginAuthor, segmentId, displayName));
    }

    @Override
    public void undescribe(JavaPlugin plugin, String segmentId) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        segments.remove(new SegmentKey(toPluginId(plugin), segmentId));
    }

    @Override
    public void undescribeAll(JavaPlugin plugin) {
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

    // ── Segment (UI metadata only) ──

    static final class Segment {
        private final String pluginId;
        private final String pluginName;
        private final String pluginAuthor;
        private final String segmentId;
        private final String displayName;

        Segment(String pluginId, String pluginName, String pluginAuthor,
                String segmentId, String displayName) {
            this.pluginId = pluginId;
            this.pluginName = pluginName;
            this.pluginAuthor = pluginAuthor;
            this.segmentId = segmentId;
            this.displayName = displayName;
        }

        String getPluginId()     { return pluginId; }
        String getPluginName()   { return pluginName; }
        String getPluginAuthor() { return pluginAuthor; }
        String getSegmentId()    { return segmentId; }
        String getDisplayName()  { return displayName; }

        @Override
        public String toString() {
            return "Segment[" + pluginId + ":" + segmentId + "]";
        }
    }
}
