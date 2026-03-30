package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.List;

/**
 * Internal registry interface backing the {@link NameplateAPI}.
 *
 * <p><b>Do not implement this interface.</b> Use {@link NameplateAPI} directly.</p>
 */
public interface INameplateRegistry {

    /**
     * Convenience overload that defaults to {@link SegmentTarget#ALL} and no example.
     *
     * @see NameplateAPI#define(JavaPlugin, String, String)
     */
    default SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName) {
        return define(plugin, segmentId, displayName, SegmentTarget.ALL, null);
    }

    /**
     * Convenience overload that defaults to no example.
     *
     * @see NameplateAPI#define(JavaPlugin, String, String, SegmentTarget)
     */
    default SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        return define(plugin, segmentId, displayName, target, null);
    }

    /** @see NameplateAPI#define(JavaPlugin, String, String, SegmentTarget, String) */
    SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example);

    /**
     * Register display format variants for a segment.
     *
     * <p>Variant names are human-readable labels shown in the player UI (e.g.
     * {@code ["Current/Max", "Percentage"]}). The first entry (index 0) is always
     * the default variant. The list must include the default.</p>
     *
     * <p>When using resolvers, the {@code variantIndex} parameter passed to
     * {@link SegmentResolver#resolve} corresponds to these variant names.</p>
     *
     * @see NameplateAPI#defineVariants(JavaPlugin, String, List)
     */
    void defineVariants(JavaPlugin plugin, String segmentId, List<String> variantNames);

    /** @see NameplateAPI#undefine(JavaPlugin, String) */
    void undefine(JavaPlugin plugin, String segmentId);

    /** Removes all segment definitions registered by a plugin. Internal use only. */
    void undefineAll(JavaPlugin plugin);
}
