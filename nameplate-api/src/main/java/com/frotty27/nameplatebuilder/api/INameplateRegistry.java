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
     * @see NameplateAPI#describe(JavaPlugin, String, String)
     */
    default void describe(JavaPlugin plugin, String segmentId, String displayName) {
        describe(plugin, segmentId, displayName, SegmentTarget.ALL, null);
    }

    /**
     * Convenience overload that defaults to no example.
     *
     * @see NameplateAPI#describe(JavaPlugin, String, String, SegmentTarget)
     */
    default void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        describe(plugin, segmentId, displayName, target, null);
    }

    /** @see NameplateAPI#describe(JavaPlugin, String, String, SegmentTarget, String) */
    void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example);

    /**
     * Register display format variants for a segment.
     *
     * <p>Variant names are human-readable labels shown in the player UI (e.g.
     * {@code ["Current/Max", "Percentage"]}). The first entry (index 0) is always
     * the default variant. The list must include the default.</p>
     *
     * <p>At runtime, mods push variant texts using suffixed keys in
     * {@link NameplateData}: the base {@code segmentId} holds variant 0, and
     * {@code segmentId + ".1"}, {@code segmentId + ".2"}, etc. hold the rest.</p>
     *
     * @see NameplateAPI#describeVariants(JavaPlugin, String, List)
     */
    void describeVariants(JavaPlugin plugin, String segmentId, List<String> variantNames);

    /** @see NameplateAPI#undescribe(JavaPlugin, String) */
    void undescribe(JavaPlugin plugin, String segmentId);

    /** Removes all segment descriptions registered by a plugin. Internal use only. */
    void undescribeAll(JavaPlugin plugin);
}
