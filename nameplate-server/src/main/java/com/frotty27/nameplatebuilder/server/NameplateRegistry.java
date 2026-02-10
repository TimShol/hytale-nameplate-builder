package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.INameplateRegistry;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.List;
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
    public void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example) {
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
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example, List.of(), false, false));
    }

    /**
     * Describe a built-in segment owned by the NameplateBuilder plugin itself.
     * Built-in segments are shown with a distinct color in the UI.
     */
    void describeBuiltIn(String pluginId, String segmentId, String displayName,
                         SegmentTarget target, String example) {
        String pluginName = "NameplateBuilder";
        String pluginAuthor = pluginId.contains(":") ? pluginId.substring(0, pluginId.indexOf(':')) : pluginId;
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        segments.put(key, new Segment(pluginId, pluginName, pluginAuthor, displayName, target, example, List.of(), true, false));
    }

    @Override
    public void describeVariants(JavaPlugin plugin, String segmentId, List<String> variantNames) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(variantNames, "variantNames");

        SegmentKey key = new SegmentKey(toPluginId(plugin), segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    List.copyOf(variantNames), existing.builtIn(), existing.supportsPrefixSuffix()));
        }
    }

    /**
     * Describe variants for a built-in segment (no JavaPlugin reference needed).
     */
    void describeVariantsInternal(String pluginId, String segmentId, List<String> variantNames) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    List.copyOf(variantNames), existing.builtIn(), existing.supportsPrefixSuffix()));
        }
    }

    /**
     * Mark a segment as supporting prefix/suffix text wrapping.
     * When enabled, the variant popup will show prefix/suffix text input fields.
     */
    void setSupportsPrefixSuffix(String pluginId, String segmentId) {
        SegmentKey key = new SegmentKey(pluginId, segmentId);
        Segment existing = segments.get(key);
        if (existing != null) {
            segments.put(key, new Segment(existing.pluginId(), existing.pluginName(), existing.pluginAuthor(),
                    existing.displayName(), existing.target(), existing.example(),
                    existing.variants(), existing.builtIn(), true));
        }
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

    /**
     * UI metadata for a described segment.
     *
     * @param pluginId              canonical plugin identifier ({@code "Group:Name"})
     * @param pluginName            human-readable plugin name (group prefix stripped)
     * @param pluginAuthor          plugin author (group portion of the identifier)
     * @param displayName           user-facing segment label shown in the editor
     * @param target                entity target hint ({@link SegmentTarget})
     * @param example               optional preview text (e.g. {@code "67/69"})
     * @param variants              ordered list of format variant names; index 0 is the
     *                              default. Variant names may contain parenthesized
     *                              examples (e.g. {@code "Percentage (69%)"}) — the
     *                              editor extracts the value inside parentheses for
     *                              the chain preview bar.
     * @param builtIn               {@code true} for segments provided by NameplateBuilder
     *                              itself (shown with a distinct color in the available list)
     * @param supportsPrefixSuffix  {@code true} to show prefix/suffix text fields and
     *                              bar empty-fill customization in the format popup
     */
    record Segment(String pluginId, String pluginName, String pluginAuthor,
                   String displayName, SegmentTarget target, String example,
                   List<String> variants, boolean builtIn, boolean supportsPrefixSuffix) {
    }
}
