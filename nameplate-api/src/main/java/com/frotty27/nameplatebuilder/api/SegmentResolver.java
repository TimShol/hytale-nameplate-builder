package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Resolves nameplate segment text for a given entity.
 *
 * <p>Implementations are called by NameplateBuilder's aggregator every tick for
 * each visible entity. Return {@code null} if this segment does not apply to
 * the entity (e.g. the entity lacks the required component). Return a non-null
 * string to include the segment in the nameplate.</p>
 *
 * <p>The {@code variantIndex} indicates which display format the viewer has
 * selected (0 = default). Implementations should return the formatted text
 * for the requested variant, or fall back to the default if the variant is
 * not supported.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * NameplateAPI.define(this, "health", "Health", SegmentTarget.ALL, "67/69")
 *     .resolver((store, entityRef, variant) -> {
 *         EntityStatMap stats = store.getComponent(entityRef, statMapType);
 *         if (stats == null) return null;
 *         int current = Math.round(stats.get(health).get());
 *         int max = Math.round(stats.get(health).getMax());
 *         return switch (variant) {
 *             case 1 -> Math.round(100f * current / max) + "%";
 *             default -> current + "/" + max;
 *         };
 *     });
 * }</pre>
 *
 * @see SegmentBuilder#resolver(SegmentResolver)
 * @see NameplateAPI#define
 */
@FunctionalInterface
public interface SegmentResolver {

    /**
     * Compute the display text for this segment on the given entity.
     *
     * @param store        the entity store
     * @param entityRef    reference to the entity being processed
     * @param variantIndex the viewer's selected variant (0 = default)
     * @return the segment text to display, or {@code null} if this segment
     *         does not apply to this entity
     */
    String resolve(Store<EntityStore> store, Ref<EntityStore> entityRef, int variantIndex);
}
