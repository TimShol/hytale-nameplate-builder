package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

/**
 * Internal registry interface backing the {@link NameplateAPI}.
 *
 * <p><b>Do not implement this interface.</b> Use {@link NameplateAPI} directly.</p>
 */
public interface INameplateRegistry {

    /**
     * Convenience overload that defaults to {@link SegmentTarget#ALL}.
     *
     * @see NameplateAPI#describe(JavaPlugin, String, String)
     */
    default void describe(JavaPlugin plugin, String segmentId, String displayName) {
        describe(plugin, segmentId, displayName, SegmentTarget.ALL);
    }

    /** @see NameplateAPI#describe(JavaPlugin, String, String, SegmentTarget) */
    void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target);

    /** @see NameplateAPI#undescribe(JavaPlugin, String) */
    void undescribe(JavaPlugin plugin, String segmentId);

    /** Removes all segment descriptions registered by a plugin. Internal use only. */
    void undescribeAll(JavaPlugin plugin);
}
