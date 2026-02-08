package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side implementation of {@link INameplateRegistry}.
 *
 * <p>Stores UI metadata (display name, author, plugin info, target) for each
 * described segment. The aggregator uses this metadata to match component entries
 * to human-readable UI blocks. Segments that are not described still work at
 * runtime — the UI falls back to showing the raw segment ID.</p>
 */
final class NameplateRegistry implements INameplateRegistry {

    private final Map<SegmentKey, Segment> segments = new ConcurrentHashMap<>();

    @Override
    public void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(target, "target");

        String pluginId = toPluginId(plugin);
        String pluginName = plugin.getName();
        String pluginAuthor = plugin.getIdentifier().getGroup();
        // Strip group prefix if present (e.g. "Frotty27:MyMod" → "MyMod")
        if (pluginName != null && pluginName.contains(":")) {
            pluginName = pluginName.substring(pluginName.indexOf(':') + 1).trim();
        }
        if (pluginAuthor != null && pluginAuthor.contains(":")) {
            pluginAuthor = pluginAuthor.substring(0, pluginAuthor.indexOf(':'));
        }
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target));
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

    record Segment(String pluginId, String pluginName, String pluginAuthor,
                   String displayName, SegmentTarget target) {
    }
}
