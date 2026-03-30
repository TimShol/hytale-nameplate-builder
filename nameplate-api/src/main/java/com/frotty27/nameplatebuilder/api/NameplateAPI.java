package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

/**
 * Static entry point for the Nameplate Builder API.
 *
 * <p>Mods use this class to define nameplate segments and optionally provide
 * resolver functions that compute segment values per entity. For segments that
 * need per-entity state or event-driven updates, use {@link #setText} to push
 * values manually.</p>
 *
 * <h3>Resolver pattern (preferred)</h3>
 * <pre>{@code
 * // In setup() - define your segment with a resolver
 * NameplateAPI.define(this, "health", "Health", SegmentTarget.ALL, "67/69")
 *     .requires(EntityStatMap.getComponentType())
 *     .resolver((store, entityRef, variant) -> {
 *         EntityStatMap stats = store.getComponent(entityRef, statMapType);
 *         if (stats == null) return null;
 *         return Math.round(stats.get(health).get()) + "/" + Math.round(stats.get(health).getMax());
 *     });
 * }</pre>
 *
 * <h3>Manual pattern (for stateful or event-driven segments)</h3>
 * <pre>{@code
 * // In setup() - define the segment for the UI
 * NameplateAPI.define(this, "buff", "Active Buff", SegmentTarget.NPCS, "Shield +5");
 *
 * // At runtime - push text to a specific entity
 * NameplateAPI.setText(store, entityRef, "buff", "Shield +5");
 *
 * // Clear when the buff expires
 * NameplateAPI.clearText(store, entityRef, "buff");
 * }</pre>
 *
 * <h3>Death cleanup</h3>
 * <p>When an entity receives a {@code DeathComponent}, the aggregator
 * automatically sends an empty nameplate to all viewers and removes the
 * {@link NameplateData} component. Mods do not need to handle death cleanup.</p>
 *
 * <p><b>Dependency:</b> your mod's {@code manifest.json} must declare
 * {@code "Frotty27:NameplateBuilder": "*"} in its Dependencies.</p>
 *
 * @see SegmentResolver
 * @see SegmentBuilder
 * @see NameplateData
 */
public final class NameplateAPI {

    private static volatile INameplateRegistry registry;
    private static volatile ComponentType<EntityStore, NameplateData> componentType;

    private NameplateAPI() {
    }

    /**
     * Called internally by the NameplateBuilder server plugin during startup.
     *
     * <p><b>Internal - do not call from external mods.</b></p>
     */
    public static void setRegistry(INameplateRegistry registry) {
        NameplateAPI.registry = registry;
    }

    /**
     * Called internally after registering the {@link NameplateData} component.
     *
     * <p><b>Internal - do not call from external mods.</b></p>
     */
    public static void setComponentType(ComponentType<EntityStore, NameplateData> type) {
        NameplateAPI.componentType = type;
    }

    // ── Define (segment registration) ──

    /**
     * Define a nameplate segment with default target ({@link SegmentTarget#ALL})
     * and no example text.
     *
     * @param plugin      the plugin owning this segment
     * @param segmentId   a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @return a {@link SegmentBuilder} to configure resolver, requirements, and caching
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName) {
        return define(plugin, segmentId, displayName, SegmentTarget.ALL, null);
    }

    /**
     * Define a nameplate segment with an entity target hint.
     *
     * @param plugin      the plugin owning this segment
     * @param segmentId   a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @param target      the entity target hint shown as a tag in the UI
     * @return a {@link SegmentBuilder} to configure resolver, requirements, and caching
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     * @see SegmentTarget
     */
    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        return define(plugin, segmentId, displayName, target, null);
    }

    /**
     * Define a nameplate segment with an entity target hint and example value.
     *
     * <p>The {@link SegmentTarget} is shown as a tag in the UI (e.g. {@code [Players]},
     * {@code [NPCs]}) so players know which entities the segment is relevant to.</p>
     *
     * <p>The {@code example} is shown as a preview in the UI. Pass {@code null}
     * for no example.</p>
     *
     * <p>Chain {@link SegmentBuilder#resolver} to provide a value function:</p>
     * <pre>{@code
     * NameplateAPI.define(this, "tier", "Elite Tier", SegmentTarget.NPCS, "Legendary")
     *     .requires(TierComponent.getComponentType())
     *     .resolver((store, ref, variant) -> {
     *         TierComponent tier = store.getComponent(ref, tierType);
     *         return tier != null ? tier.getName() : null;
     *     });
     * }</pre>
     *
     * @param plugin      the plugin owning this segment
     * @param segmentId   a unique identifier for this segment within your plugin
     * @param displayName human-readable name shown in the Nameplate Builder UI
     * @param target      the entity target hint shown as a tag in the UI
     * @param example     example value shown in the UI (nullable)
     * @return a {@link SegmentBuilder} to configure resolver, requirements, and caching
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     * @see SegmentBuilder
     * @see SegmentTarget
     */
    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(displayName, "displayName");
        requireNonNull(target, "target");
        return getRegistry().define(plugin, segmentId, displayName, target, example);
    }

    /**
     * Remove a segment definition from the UI.
     *
     * <p>After this call the segment will no longer appear in the Nameplate Builder UI.
     * Existing {@link NameplateData} components on entities are <b>not</b> affected.</p>
     *
     * @param plugin    the plugin that defined the segment
     * @param segmentId the segment identifier
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void undefine(JavaPlugin plugin, String segmentId) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        getRegistry().undefine(plugin, segmentId);
    }

    // ── Format Variants ──

    /**
     * Register display format variants for a segment.
     *
     * <p>Variants let players choose how a segment's data is displayed. For example,
     * a health segment might offer {@code ["Current/Max", "Percentage"]} so the
     * player can toggle between {@code "42/67"} and {@code "63%"}.</p>
     *
     * <p>When using resolvers, the {@code variantIndex} parameter passed to
     * {@link SegmentResolver#resolve} corresponds to these variant names.</p>
     *
     * <p>When using manual text ({@link #setText}), push variant texts using
     * suffixed keys: {@code "health"} for variant 0, {@code "health.1"} for
     * variant 1, etc.</p>
     *
     * @param plugin       the plugin owning this segment
     * @param segmentId    the segment identifier (must match a previously defined segment)
     * @param variantNames human-readable variant names (index 0 = default)
     * @throws NameplateArgumentException       if any parameter is null/blank or list is empty
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void defineVariants(JavaPlugin plugin, String segmentId, List<String> variantNames) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        requireNonNull(variantNames, "variantNames");
        if (variantNames.isEmpty()) {
            throw new NameplateArgumentException("variantNames", "'variantNames' must not be empty");
        }
        getRegistry().defineVariants(plugin, segmentId, variantNames);
    }

    // ── setText / clearText (per-entity text) ──

    /**
     * Set (or update) nameplate text for a segment on a specific entity.
     *
     * <p>Use this for segments that need per-entity state tracking or event-driven
     * updates. For segments whose values can be computed from entity components,
     * prefer {@link SegmentBuilder#resolver} instead.</p>
     *
     * <p>If the entity does not yet have a {@link NameplateData} component,
     * one is created and attached automatically. If the segment already has
     * text on this entity, it is overwritten.</p>
     *
     * <p>Manual text set via this method takes precedence over resolver values
     * for the same segment on the same entity.</p>
     *
     * <p><b>Important:</b> this method calls {@code store.addComponent()} internally
     * when the entity doesn't already have a {@link NameplateData} component. This
     * means it <b>cannot</b> be called from inside an {@code EntityTickingSystem}.
     * For tick-system initialization, build a {@link NameplateData} manually and use
     * {@code commandBuffer.putComponent()} instead.</p>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     * @param segmentId the segment identifier (should match what was passed to {@link #define})
     * @param text      the text to display
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void setText(Store<EntityStore> store,
                               Ref<EntityStore> entityRef,
                               String segmentId,
                               String text) {
        requireNonNull(store, "store");
        requireNonNull(entityRef, "entityRef");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(text, "text", "must not be blank - use clearText() to clear");

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
     * Clear a single segment's text from an entity.
     *
     * <p>If the entity's {@link NameplateData} component becomes empty after
     * removal and no resolvers apply, the component is automatically removed.</p>
     *
     * @param store     the entity store
     * @param entityRef reference to the entity
     * @param segmentId the segment identifier to clear
     * @throws NameplateArgumentException       if any parameter is null or blank
     * @throws NameplateNotInitializedException if NameplateBuilder has not loaded
     */
    public static void clearText(Store<EntityStore> store,
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
     * <p>Most mods should use {@link #setText} and {@link #clearText} instead.
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
