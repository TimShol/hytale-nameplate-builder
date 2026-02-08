package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

/**
 * Internal registry interface backing the {@link NameplateAPI}.
 * Use {@link NameplateAPI} directly rather than this interface.
 */
public interface INameplateRegistry {

    /** @see NameplateAPI#describe(JavaPlugin, String, String) */
    void describe(JavaPlugin plugin, String segmentId, String displayName);

    /** @see NameplateAPI#undescribe(JavaPlugin, String) */
    void undescribe(JavaPlugin plugin, String segmentId);

    /** @see NameplateAPI#undescribeAll(JavaPlugin) */
    void undescribeAll(JavaPlugin plugin);
}
