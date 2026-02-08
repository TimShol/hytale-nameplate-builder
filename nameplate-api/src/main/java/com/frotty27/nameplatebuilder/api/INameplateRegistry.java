package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.UUID;

/**
 * Internal registry interface backing the {@link NameplateAPI}.
 * Use {@link NameplateAPI} directly rather than this interface.
 */
public interface INameplateRegistry {

    /** @see NameplateAPI#register(JavaPlugin, String, String, INameplateTextProvider) */
    INameplateSegmentHandle register(JavaPlugin plugin, String segmentId,
                                     String displayName, INameplateTextProvider provider);

    /** @see NameplateAPI#register(JavaPlugin, String, String, String) */
    INameplateSegmentHandle register(JavaPlugin plugin, String segmentId,
                                     String displayName, String text);

    /** @see NameplateAPI#unregister(JavaPlugin, String) */
    void unregister(JavaPlugin plugin, String segmentId);

    /** @see NameplateAPI#unregisterAll(JavaPlugin) */
    void unregisterAll(JavaPlugin plugin);

    /** @see NameplateAPI#setNameplateText(JavaPlugin, String, UUID, String) */
    void setNameplateText(JavaPlugin plugin, String segmentId, UUID entityUuid, String text);

    /** @see NameplateAPI#setNameplateText(JavaPlugin, String, String) */
    void setNameplateText(JavaPlugin plugin, String segmentId, String text);
}
