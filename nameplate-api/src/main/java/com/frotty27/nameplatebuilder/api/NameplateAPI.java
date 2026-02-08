package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.UUID;

/**
 * Static entry point for the Nameplate Builder API.
 *
 * <p>Mods use this class to register nameplate segments, update their text,
 * and remove them when no longer needed. The API is backed by a registry
 * that is set by the NameplateBuilder server plugin during startup.</p>
 *
 * <p><b>Dependency:</b> your mod's {@code manifest.json} must declare
 * {@code "Frotty27:NameplateBuilder": "*"} in its Dependencies to ensure
 * the registry is available before your plugin loads.</p>
 */
public final class NameplateAPI {

    private static volatile INameplateRegistry registry;

    private NameplateAPI() {
    }

    /** Called internally by the NameplateBuilder server plugin during startup. */
    public static void setRegistry(INameplateRegistry registry) {
        NameplateAPI.registry = registry;
    }

    /** Returns the backing registry, or throws if NameplateBuilder is not loaded. */
    public static INameplateRegistry getRegistry() {
        INameplateRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException(
                    "Nameplate API not initialized. Ensure NameplateBuilder is installed on the server.");
        }
        return current;
    }

    /**
     * Register a nameplate segment with a dynamic text provider.
     * Use this when the text depends on which entity or viewer is involved.
     *
     * @param plugin   the plugin registering the segment
     * @param segmentId a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @param provider called each tick to produce the segment text
     * @return a handle that can be used to unregister the segment
     */
    public static INameplateSegmentHandle register(JavaPlugin plugin,
                                                   String segmentId,
                                                   String displayName,
                                                   INameplateTextProvider provider) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("'segmentId' must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("'displayName' must not be blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("'provider' must not be null");
        }
        return getRegistry().register(plugin, segmentId, displayName, provider);
    }

    /**
     * Register a nameplate segment with static text that applies to all entities.
     * This is the simplest way to add a nameplate block.
     *
     * <pre>{@code
     * NameplateAPI.register(this, "title", "Guild Title", "[MyGuild]");
     * }</pre>
     *
     * @param plugin   the plugin registering the segment
     * @param segmentId a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @param text the static text to display (can be changed later via {@link #setNameplateText})
     * @return a handle that can be used to unregister the segment
     */
    public static INameplateSegmentHandle register(JavaPlugin plugin,
                                                   String segmentId,
                                                   String displayName,
                                                   String text) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("'segmentId' must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("'displayName' must not be blank");
        }
        if (text == null) {
            throw new IllegalArgumentException("'text' must not be null");
        }
        return getRegistry().register(plugin, segmentId, displayName, text);
    }

    /**
     * Remove a previously registered segment.
     *
     * @param plugin   the plugin that registered the segment
     * @param segmentId the segment identifier used during registration
     */
    public static void unregister(JavaPlugin plugin, String segmentId) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("'segmentId' must not be blank");
        }
        getRegistry().unregister(plugin, segmentId);
    }

    /**
     * Remove all segments registered by a plugin.
     * This is called automatically by NameplateBuilder when the server shuts down,
     * so most mods do not need to call this.
     *
     * @param plugin the plugin whose segments should be removed
     */
    public static void unregisterAll(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        getRegistry().unregisterAll(plugin);
    }

    /**
     * Set the nameplate text for a specific entity.
     * This overrides both the dynamic provider and the global text for that entity only.
     * Pass {@code null} or blank to clear the per-entity override.
     *
     * @param plugin     the plugin that registered the segment
     * @param segmentId  the segment identifier
     * @param entityUuid the UUID of the entity to override text for
     * @param text       the text to show, or {@code null}/blank to clear
     */
    public static void setNameplateText(JavaPlugin plugin,
                                        String segmentId,
                                        UUID entityUuid,
                                        String text) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("'segmentId' must not be blank");
        }
        if (entityUuid == null) {
            throw new IllegalArgumentException("'entityUuid' must not be null");
        }
        getRegistry().setNameplateText(plugin, segmentId, entityUuid, text);
    }

    /**
     * Set the global nameplate text for a segment (applies to all entities).
     * This can be updated at any time after registration.
     *
     * <pre>{@code
     * NameplateAPI.setNameplateText(this, "title", "[NewGuild]");
     * }</pre>
     *
     * @param plugin    the plugin that registered the segment
     * @param segmentId the segment identifier (internal for your mod)
     * @param text      the nameplate text that will be displayed
     */
    public static void setNameplateText(JavaPlugin plugin,
                                        String segmentId,
                                        String text) {
        if (plugin == null) {
            throw new IllegalArgumentException("'plugin' must not be null");
        }
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("'segmentId' must not be blank");
        }
        getRegistry().setNameplateText(plugin, segmentId, text);
    }
}
