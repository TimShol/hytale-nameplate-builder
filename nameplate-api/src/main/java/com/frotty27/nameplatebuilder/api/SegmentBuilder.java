package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Builder returned by {@link NameplateAPI#define} for configuring a segment
 * after registration.
 *
 * <p>Methods chain fluently and write through to the registry immediately.
 * No explicit {@code build()} call is needed - the segment is registered
 * as soon as {@code define()} is called, and builder methods update it
 * in place.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * NameplateAPI.define(this, "elite-tier", "Elite Tier", SegmentTarget.NPCS, "Legendary")
 *     .requires(TierComponent.getComponentType())
 *     .cacheTicks(10)
 *     .resolver((store, entityRef, variant) -> {
 *         TierComponent tier = store.getComponent(entityRef, tierType);
 *         if (tier == null) return null;
 *         return tier.getTierName();
 *     });
 * }</pre>
 *
 * @see NameplateAPI#define
 * @see SegmentResolver
 */
public interface SegmentBuilder {

    /**
     * Register a resolver function that computes this segment's text per entity.
     *
     * <p>When set, NameplateBuilder calls this resolver instead of reading from
     * {@link NameplateData}. If the entity also has manual text set via
     * {@link NameplateAPI#setText}, the manual text takes precedence.</p>
     *
     * @param resolver the resolver function
     * @return this builder for chaining
     * @see SegmentResolver
     */
    SegmentBuilder resolver(SegmentResolver resolver);

    /**
     * Optimization hint: only call the resolver for entities whose archetype
     * contains this component type.
     *
     * <p>Entities without the specified component in their archetype are skipped
     * entirely - the resolver is never invoked and the segment is treated as
     * not applicable. This avoids unnecessary component lookups for entities
     * that can never have this segment.</p>
     *
     * @param componentType the required component type
     * @return this builder for chaining
     */
    SegmentBuilder requires(ComponentType<EntityStore, ?> componentType);

    /**
     * How many ticks to cache the resolver result per entity.
     *
     * <p>Default is 1 (re-evaluate every tick). Set higher for data that changes
     * infrequently, such as level or faction. The cache is per-entity and is
     * evicted when the entity dies.</p>
     *
     * @param ticks number of ticks to cache (minimum 1)
     * @return this builder for chaining
     */
    SegmentBuilder cacheTicks(int ticks);
}
