package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Static entry point for the Nameplate Builder API.
 *
 * <p>Mods use this class to describe their nameplate segments (for the player UI)
 * and to register/remove nameplate text on individual entities.</p>
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * // In setup() — describe your segment for the UI (optional but recommended)
 * NameplateAPI.describe(this, "health", "Health Bar");
 *
 * // At runtime — register nameplate text to an entity
 * NameplateAPI.register(store, entityRef, "health", "67/67");
 *
 * // Update when value changes — just call register() again
 * NameplateAPI.register(store, entityRef, "health", "23/67");
 *
 * // Remove a single segment from an entity
 * NameplateAPI.remove(store, entityRef, "health");
 *
 * // Remove all nameplate data from an entity
 * store.tryRemoveComponent(entityRef, NameplateAPI.getComponentType());
 * }</pre>
 *
 * <h3>Attaching nameplates to NPCs on spawn</h3>
 * <p>The recommended approach is an {@code EntityTickingSystem} that queries for
 * {@code NPCEntity}, checks the role name, and calls {@link #register} on entities
 * that don't yet have a {@link NameplateData} component. The first {@code register()}
 * call creates and attaches the component automatically, so subsequent ticks skip
 * the entity. See {@code ArchaeopteryxNameplateSystem} in the example mod for a
 * full working implementation.</p>
 *
 * <h3>Tick-based updates</h3>
 * <p>Calling {@link #register} every tick is safe — it updates the internal map
 * value in place without adding or removing the component, so there is no
 * flashing. For direct access inside a tick system, use
 * {@link NameplateData#setText} on the component instance instead.</p>
 *
 * <h3>Death cleanup</h3>
 * <p>When an entity receives a {@code DeathComponent}, the aggregator
 * automatically sends an empty nameplate to all viewers and removes the
 * {@link NameplateData} component. Mods do not need to handle death cleanup
 * themselves — nameplates are cleared as soon as the entity dies.</p>
 *
 * <p><b>Dependency:</b> your mod's {@code manifest.json} must declare
 * {@code "Frotty27:NameplateBuilder": "*"} in its Dependencies to ensure
 * the API is available before your plugin loads.</p>
 *
 * @see NameplateData
 * @see NameplateNotInitializedException
 * @see NameplateArgumentException
 */
public final class NameplateAPI {

    private static volatile INameplateRegistry registry;
    private static volatile ComponentType<EntityStore, NameplateData> componentType;

    private NameplateAPI() {
    }

    // ── Internal setters (called by NameplateBuilder server plugin) ──
    // These must be public for cross-module access but are NOT part of the mod API.
    // External mods should never call these — doing so will break the system.

    /**
     * Called internally by the NameplateBuilder server plugin during startup.
     *
     * <p><b>Internal — do not call from external mods.</b></p>
     */
    public static void setRegistry(INameplateRegistry registry) {
        NameplateAPI.registry = registry;
    }

    /**
     * Called internally after registering the {@link NameplateData} component.
     *
     * <p><b>Internal — do not call from external mods.</b></p>
     */
    public static void setComponentType(ComponentType<EntityStore, NameplateData> type) {
        NameplateAPI.componentType = type;
    }

    // ── Describe (UI metadata) ──

    /**
     * Describe a nameplate segment for the player UI.
     *
     * <p>This registers UI metadata (display name, author) so the Nameplate Builder
     * UI can show a human-readable block for this segment. Calling this is
     * <b>optional</b> — if skipped, the UI will show the raw segment ID instead.</p>
     *
     * <p>Defaults the target to {@link SegmentTarget#ALL}. Use
     * {@link #describe(JavaPlugin, String, String, SegmentTarget)} to specify
     * a more specific target.</p>
     *
     * <p>Call once during your plugin's {@code setup()} method:</p>
     * <pre>{@code
     * NameplateAPI.describe(this, "health", "Health Bar");
     * }</pre>
     *
     * @param plugin      the plugin owning this segment
     * @param segmentId   a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void describe(JavaPlugin plugin, String segmentId, String displayName) {
        describe(plugin, segmentId, displayName, SegmentTarget.ALL);
    }

    /**
     * Describe a nameplate segment for the player UI, with an entity target hint.
     *
     * <p>The {@link SegmentTarget} is shown as a tag in the UI (e.g. {@code [Players]},
     * {@code [NPCs]}) so players know which entities the segment is relevant to.
     * This is purely informational — it does not restrict which entities the segment
     * can be registered on at runtime.</p>
     *
     * <pre>{@code
     * NameplateAPI.describe(this, "health", "Health Bar", SegmentTarget.ALL);
     * NameplateAPI.describe(this, "status", "Online Status", SegmentTarget.PLAYERS);
     * NameplateAPI.describe(this, "tier", "Elite Tier", SegmentTarget.NPCS);
     * }</pre>
     *
     * @param plugin      the plugin owning this segment
     * @param segmentId   a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @param target      the entity target hint shown as a tag in the UI
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     * @see SegmentTarget
     */
    public static void describe(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(displayName, "displayName");
        requireNonNull(target, "target");
        getRegistry().describe(plugin, segmentId, displayName, target);
    }

    /**
     * Remove a segment description from the UI.
     *
     * <p>After this call the segment will no longer appear in the Nameplate Builder UI.
     * Existing {@link NameplateData} components on entities are <b>not</b> affected.</p>
     *
     * @param plugin    the plugin that described the segment
     * @param segmentId the segment identifier
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void undescribe(JavaPlugin plugin, String segmentId) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        getRegistry().undescribe(plugin, segmentId);
    }

    // ── Register / Remove (per-entity text) ──

    /**
     * Register (or update) nameplate text for a segment on an entity.
     *
     * <p>If the entity does not yet have a {@link NameplateData} component,
     * one is created and attached automatically. If the segment already has
     * text on this entity, it is overwritten.</p>
     *
     * <pre>{@code
     * NameplateAPI.register(store, entityRef, "health", "67/67");
     * }</pre>
     *
     * <p><b>Important:</b> this method calls {@code store.addComponent()} internally
     * when the entity doesn't already have a {@link NameplateData} component. This
     * means it <b>cannot</b> be called from inside an {@code EntityTickingSystem}
     * (the store is locked for writes during system processing). For tick-system
     * initialization, build a {@link NameplateData} manually and use
     * {@code commandBuffer.putComponent()} instead ({@code putComponent} is an
     * upsert — safe even if another system adds the component between the read
     * and the command buffer executing). See
     * {@code ArchaeopteryxNameplateSystem} in the example mod.</p>
     *
     * <p>However, if the entity <b>already has</b> the component, this method only
     * mutates the existing map in place — which is safe from tick systems.</p>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     * @param segmentId the segment identifier (should match what was passed to {@link #describe})
     * @param text      the text to display
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     * @throws IllegalStateException            if the entity has no NameplateData and this is called
     *                                          from inside an EntityTickingSystem (store is locked)
     */
    public static void register(Store<EntityStore> store,
                                Ref<EntityStore> entityRef,
                                String segmentId,
                                String text) {
        requireNonNull(store, "store");
        requireNonNull(entityRef, "entityRef");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(text, "text", "must not be blank — use remove() to clear");

        ComponentType<EntityStore, NameplateData> type = getComponentType();
        NameplateData data = store.getComponent(entityRef, type);
        if (data == null) {
            data = new NameplateData();
            data.setText(segmentId, text);
            store.addComponent(entityRef, type, data);
        } else {
            data.setText(segmentId, text);
        }
    }

    /**
     * Remove a single segment's text from an entity.
     *
     * <p>If the entity's {@link NameplateData} component becomes empty after
     * removal, the component is automatically removed from the entity.</p>
     *
     * <pre>{@code
     * NameplateAPI.remove(store, entityRef, "health");
     * }</pre>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     * @param segmentId the segment identifier to remove
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void remove(Store<EntityStore> store,
                              Ref<EntityStore> entityRef,
                              String segmentId) {
        requireNonNull(store, "store");
        requireNonNull(entityRef, "entityRef");
        requireNonBlank(segmentId, "segmentId");

        ComponentType<EntityStore, NameplateData> type = getComponentType();
        NameplateData data = store.getComponent(entityRef, type);
        if (data != null) {
            data.removeText(segmentId);
            if (data.isEmpty()) {
                store.tryRemoveComponent(entityRef, type);
            }
        }
    }

    // ── Component type access ──

    /**
     * Returns the {@link ComponentType} for {@link NameplateData}.
     *
     * <p>Most mods should use {@link #register} and {@link #remove} instead.
     * This is exposed for advanced use cases like bulk removal via
     * {@code store.tryRemoveComponent(ref, NameplateAPI.getComponentType())}.</p>
     *
     * @return the registered component type for NameplateData
     * @throws NameplateNotInitializedException if NameplateBuilder has not finished loading
     */
    public static ComponentType<EntityStore, NameplateData> getComponentType() {
        ComponentType<EntityStore, NameplateData> current = componentType;
        if (current == null) {
            throw new NameplateNotInitializedException(
                    "NameplateData component not registered. "
                            + "Ensure NameplateBuilder is installed and your manifest.json declares "
                            + "\"Frotty27:NameplateBuilder\": \"*\" in Dependencies.");
        }
        return current;
    }

    /** Returns the backing registry, or throws if NameplateBuilder is not loaded. */
    static INameplateRegistry getRegistry() {
        INameplateRegistry current = registry;
        if (current == null) {
            throw new NameplateNotInitializedException(
                    "Nameplate API not initialized. "
                            + "Ensure NameplateBuilder is installed and your manifest.json declares "
                            + "\"Frotty27:NameplateBuilder\": \"*\" in Dependencies.");
        }
        return current;
    }

    // ── Validation helpers ──

    private static void requireNonNull(Object value, String parameterName) {
        if (value == null) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' must not be null");
        }
    }

    private static void requireNonBlank(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' must not be null or blank");
        }
    }

    private static void requireNonBlank(String value, String parameterName, String detail) {
        if (value == null || value.isBlank()) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' " + detail);
        }
    }
}
