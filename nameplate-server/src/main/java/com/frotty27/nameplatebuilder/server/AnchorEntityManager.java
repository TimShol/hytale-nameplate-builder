package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages invisible "anchor" entities used to display nameplates at a
 * configurable vertical offset above real entities.
 *
 * <p>Each anchor is an invisible entity with {@code ProjectileComponent} (model "Projectile"),
 * {@code Intangible} (no collision), {@code TransformComponent} (position), and
 * {@code NetworkId} (visible to clients). The anchor's position is updated every tick
 * to follow the real entity at the configured Y offset using velocity-based prediction.</p>
 *
 * <p>Anchor spawning is asynchronous — it is queued via {@code world.execute()}
 * and materializes on the next tick. During the one-frame delay, nameplate text
 * is shown on the real entity as a fallback.</p>
 *
 * <p><b>Lifecycle:</b> Anchors are spawned with {@code AddReason.LOAD} so they won't
 * persist to disk. When entities die or are removed, anchors are simply untracked
 * (removed from {@code anchors} map) and left in the world until chunk unload. They
 * are never explicitly removed to avoid chunk serialization race conditions.</p>
 */
final class AnchorEntityManager {

    private final Map<Ref<EntityStore>, AnchorState> anchors = new HashMap<>();
    private final List<AnchorState> pendingRemovals = new ArrayList<>();

    /**
     * Tracks the state of an anchor entity for one real entity.
     * Fields are volatile because the spawn callback runs on the world thread
     * via {@code world.execute()}, while the tick system reads them on the
     * same thread but potentially after the callback drains.
     */
    private static final class AnchorState {
        volatile Ref<EntityStore> anchorRef;
        volatile boolean spawnPending;
        double currentOffset;
        // Velocity tracking for predictive positioning
        Vector3d lastPosition;
        Vector3d velocity;

        AnchorState(double offset) {
            this.anchorRef = null;
            this.spawnPending = true;
            this.currentOffset = offset;
            this.lastPosition = null;
            this.velocity = null;
        }
    }

    /**
     * Returns the anchor entity ref for a real entity, or {@code null} if
     * no anchor exists or the spawn has not yet materialized.
     */
    Ref<EntityStore> getAnchorRef(Ref<EntityStore> realEntityRef) {
        AnchorState state = anchors.get(realEntityRef);
        if (state == null || state.spawnPending) {
            return null;
        }
        Ref<EntityStore> ref = state.anchorRef;
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref;
    }

    /**
     * Returns {@code true} if an anchor spawn has been queued but has not
     * yet materialized for the given real entity.
     */
    boolean isSpawnPending(Ref<EntityStore> realEntityRef) {
        AnchorState state = anchors.get(realEntityRef);
        return state != null && state.spawnPending;
    }

    /**
     * Returns {@code true} if any anchor state exists for the given real
     * entity (either ready or spawn pending).
     */
    boolean hasAnchor(Ref<EntityStore> realEntityRef) {
        return anchors.containsKey(realEntityRef);
    }

    /**
     * Ensure an anchor exists for the given real entity at the specified offset.
     *
     * <ul>
     *   <li>If no anchor exists: queues a spawn via {@code world.execute()}.</li>
     *   <li>If spawn is pending: updates the desired offset for when it lands.</li>
     *   <li>If anchor is ready: repositions it to match the real entity's
     *       position plus the Y offset, with velocity-based prediction for
     *       fast-moving entities.</li>
     * </ul>
     *
     * @param realEntityRef the real entity's reference
     * @param realPosition  the real entity's current world position
     * @param offset        the Y offset (max across all viewers needing an anchor)
     * @param world         the World for spawning (via {@code world.execute()})
     * @param store         the entity store for reading components
     * @param transformType the TransformComponent type for position access
     * @param commandBuffer the command buffer for component replacement
     */
    void ensureAnchor(Ref<EntityStore> realEntityRef,
                      Vector3d realPosition,
                      double offset,
                      World world,
                      Store<EntityStore> store,
                      ComponentType<EntityStore, TransformComponent> transformType,
                      CommandBuffer<EntityStore> commandBuffer) {

        AnchorState state = anchors.get(realEntityRef);

        if (state == null) {
            // No anchor at all — queue spawn
            state = new AnchorState(offset);
            anchors.put(realEntityRef, state);
            queueSpawn(realPosition, offset, world, state);
            return;
        }

        if (state.spawnPending) {
            // Spawn queued but not yet materialized — update desired offset
            state.currentOffset = offset;
            return;
        }

        // Anchor ref went stale (engine removed the entity) — re-spawn
        if (state.anchorRef == null || !state.anchorRef.isValid()) {
            state.anchorRef = null;
            state.spawnPending = true;
            state.currentOffset = offset;
            state.lastPosition = null;
            state.velocity = null;
            queueSpawn(realPosition, offset, world, state);
            return;
        }

        // Anchor exists and is ready — reposition it with prediction
        state.currentOffset = offset;
        repositionAnchor(state.anchorRef, realPosition, offset, state, store, transformType, commandBuffer);
    }

    /**
     * Remove the anchor for a real entity. Simply removes tracking state.
     * The anchor entity itself is left in the world (with blank nameplate)
     * to avoid chunk serialization race conditions. Since anchors use
     * AddReason.LOAD, they won't persist to disk and will disappear on
     * chunk unload/reload.
     *
     * @param realEntityRef the real entity whose anchor should be removed
     * @param commandBuffer the command buffer (unused, kept for API compatibility)
     */
    void removeAnchor(Ref<EntityStore> realEntityRef,
                      CommandBuffer<EntityStore> commandBuffer) {
        // Just remove from tracking - leave the anchor entity in the world
        // It has AddReason.LOAD so it won't persist to disk
        anchors.remove(realEntityRef);
    }

    /**
     * No-op - we no longer remove anchor entities to avoid chunk serialization
     * race conditions. Anchors use AddReason.LOAD so they won't persist to disk.
     *
     * @deprecated No longer used - kept for API compatibility
     */
    @Deprecated
    void cleanupPendingRemovals(CommandBuffer<EntityStore> commandBuffer) {
        // No-op - we don't remove anchors anymore
        pendingRemovals.clear();
    }

    /**
     * Clean up anchors whose real entity has been removed from the store
     * (e.g. by {@code /npc clean}). Simply removes tracking state - the
     * anchor entity is left in the world to avoid serialization races.
     *
     * <p>Call once per tick from the aggregator, before processing entities.</p>
     */
    void cleanupOrphanedAnchors(CommandBuffer<EntityStore> commandBuffer) {
        if (anchors.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Ref<EntityStore>, AnchorState>> it = anchors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Ref<EntityStore>, AnchorState> entry = it.next();
            Ref<EntityStore> realRef = entry.getKey();
            if (!realRef.isValid()) {
                // Just remove from tracking - leave anchor in world
                it.remove();
            }
        }
    }

    /** Clears all anchor state. Called during plugin shutdown. */
    void clear() {
        anchors.clear();
        pendingRemovals.clear();
    }

    // ── Private helpers ──

    /**
     * Queue an anchor entity spawn via {@code world.execute()}.
     * The anchor materializes on the next tick. Anchors are invisible entities
     * (ProjectileComponent + Intangible + Transform + NetworkId) that exist
     * solely to display nameplate text at an offset. They use {@code AddReason.LOAD}
     * so they won't persist to disk.
     */
    private void queueSpawn(Vector3d realPosition,
                            double offset,
                            World world,
                            AnchorState state) {

        double anchorX = realPosition.getX();
        double anchorY = realPosition.getY() + offset;
        double anchorZ = realPosition.getZ();

        world.execute(() -> {
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) {
                state.spawnPending = false;
                return;
            }
            Store<EntityStore> store = entityStore.getStore();

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            // Use ProjectileComponent with "Projectile" to satisfy legacy system
            // but keep entity intangible and invisible
            holder.putComponent(
                    ProjectileComponent.getComponentType(),
                    new ProjectileComponent("Projectile"));
            holder.putComponent(
                    Intangible.getComponentType(),
                    Intangible.INSTANCE);
            holder.putComponent(
                    TransformComponent.getComponentType(),
                    new TransformComponent(
                            new Vector3d(anchorX, anchorY, anchorZ),
                            new Vector3f(0, 0, 0)));
            holder.putComponent(
                    NetworkId.getComponentType(),
                    new NetworkId(entityStore.takeNextNetworkId()));

            // Use AddReason.LOAD to indicate this is a runtime-only entity
            // that shouldn't be persisted to disk during chunk saves
            Ref<EntityStore> anchorRef = store.addEntity(holder, AddReason.LOAD);
            state.anchorRef = anchorRef;
            state.spawnPending = false;
        });
    }

    /**
     * Reposition an existing anchor to follow the real entity at the given
     * Y offset. Uses {@code CommandBuffer.replaceComponent()} for safe,
     * deferred updates. Implements velocity-based prediction to compensate
     * for the 1-tick delay and reduce visual lag on fast-moving entities.
     *
     * <p><b>Predictive positioning:</b> Tracks entity velocity and positions
     * the anchor where the entity <i>will be</i> next tick, compensating for
     * the CommandBuffer delay.</p>
     */
    private void repositionAnchor(Ref<EntityStore> anchorRef,
                                  Vector3d realPosition,
                                  double offset,
                                  AnchorState state,
                                  Store<EntityStore> store,
                                  ComponentType<EntityStore, TransformComponent> transformType,
                                  CommandBuffer<EntityStore> commandBuffer) {
        TransformComponent transform = store.getComponent(anchorRef, transformType);
        if (transform == null) {
            return;
        }

        // ── Velocity calculation for predictive positioning ──
        Vector3d targetPosition;

        if (state.lastPosition != null) {
            // Calculate velocity from position delta
            double dx = realPosition.getX() - state.lastPosition.getX();
            double dy = realPosition.getY() - state.lastPosition.getY();
            double dz = realPosition.getZ() - state.lastPosition.getZ();

            // Apply simple smoothing to avoid jitter from single-tick anomalies
            if (state.velocity != null) {
                // Exponential moving average: 70% current velocity + 30% new velocity
                dx = state.velocity.getX() * 0.7 + dx * 0.3;
                dy = state.velocity.getY() * 0.7 + dy * 0.3;
                dz = state.velocity.getZ() * 0.7 + dz * 0.3;
            }

            state.velocity = new Vector3d(dx, dy, dz);

            // Predict next position: current + velocity
            // This compensates for the 1-tick CommandBuffer delay
            targetPosition = new Vector3d(
                    realPosition.getX() + dx,
                    realPosition.getY() + dy + offset,
                    realPosition.getZ() + dz
            );
        } else {
            // First tick — no velocity data yet, use current position
            targetPosition = new Vector3d(
                    realPosition.getX(),
                    realPosition.getY() + offset,
                    realPosition.getZ()
            );
        }

        // Store current position for next tick's velocity calculation
        state.lastPosition = new Vector3d(realPosition.getX(), realPosition.getY(), realPosition.getZ());

        // Create new TransformComponent with predicted position
        TransformComponent newTransform = new TransformComponent(targetPosition, transform.getRotation());

        // Queue component replacement via CommandBuffer (safe during tick processing)
        commandBuffer.replaceComponent(anchorRef, transformType, newTransform);
    }
}
