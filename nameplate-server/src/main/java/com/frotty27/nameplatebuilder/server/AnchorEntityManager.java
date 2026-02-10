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
 * <p>Each anchor is a bare {@code ProjectileComponent} entity with no model,
 * no collision ({@code Intangible}), and a {@code NetworkId} so it is visible
 * to clients. The anchor's {@code TransformComponent} position is updated
 * every tick to follow the real entity at the configured Y offset.</p>
 *
 * <p>Anchor spawning is asynchronous — it is queued via {@code world.execute()}
 * and materializes on the next tick. During the one-frame delay, nameplate text
 * is shown on the real entity as a fallback.</p>
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

        AnchorState(double offset) {
            this.anchorRef = null;
            this.spawnPending = true;
            this.currentOffset = offset;
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
        return state.anchorRef;
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
     *       position plus the Y offset.</li>
     * </ul>
     *
     * @param realEntityRef the real entity's reference
     * @param realPosition  the real entity's current world position
     * @param offset        the Y offset (max across all viewers needing an anchor)
     * @param world         the World for spawning (via {@code world.execute()})
     * @param store         the entity store for reading/mutating components
     * @param transformType the TransformComponent type for position access
     */
    void ensureAnchor(Ref<EntityStore> realEntityRef,
                      Vector3d realPosition,
                      double offset,
                      World world,
                      Store<EntityStore> store,
                      ComponentType<EntityStore, TransformComponent> transformType) {

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

        // Anchor exists and is ready — reposition it
        state.currentOffset = offset;
        repositionAnchor(state.anchorRef, realPosition, offset, store, transformType);
    }

    /**
     * Remove the anchor for a real entity. If the anchor is ready, it is
     * removed via the command buffer. If spawn is still pending, the state
     * is added to {@link #pendingRemovals} for cleanup when the spawn
     * completes.
     *
     * @param realEntityRef the real entity whose anchor should be removed
     * @param commandBuffer the command buffer for deferred entity removal
     */
    void removeAnchor(Ref<EntityStore> realEntityRef,
                      CommandBuffer<EntityStore> commandBuffer) {
        AnchorState state = anchors.remove(realEntityRef);
        if (state == null) {
            return;
        }

        if (state.spawnPending || state.anchorRef == null) {
            // Spawn hasn't completed yet — defer removal
            pendingRemovals.add(state);
            return;
        }

        commandBuffer.removeEntity(state.anchorRef, RemoveReason.REMOVE);
    }

    /**
     * Clean up anchors that were scheduled for removal while their spawn
     * was still pending. Call once per tick from the aggregator.
     */
    void cleanupPendingRemovals(CommandBuffer<EntityStore> commandBuffer) {
        if (pendingRemovals.isEmpty()) {
            return;
        }
        Iterator<AnchorState> it = pendingRemovals.iterator();
        while (it.hasNext()) {
            AnchorState state = it.next();
            if (!state.spawnPending && state.anchorRef != null) {
                commandBuffer.removeEntity(state.anchorRef, RemoveReason.REMOVE);
                it.remove();
            }
            // If still pending, leave for the next tick
        }
    }

    /**
     * Clean up anchors whose real entity has been removed from the store
     * (e.g. by {@code /npc clean}). The real entity's {@code Ref} becomes
     * invalid when the entity is removed, so we check {@code ref.isValid()}
     * and despawn any orphaned anchors.
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
                AnchorState state = entry.getValue();
                it.remove();
                if (state.spawnPending || state.anchorRef == null) {
                    // Anchor spawn hasn't completed — defer removal
                    pendingRemovals.add(state);
                } else {
                    commandBuffer.removeEntity(state.anchorRef, RemoveReason.REMOVE);
                }
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
     * The anchor materializes on the next tick.
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

            Ref<EntityStore> anchorRef = store.addEntity(holder, AddReason.SPAWN);
            state.anchorRef = anchorRef;
            state.spawnPending = false;
        });
    }

    /**
     * Reposition an existing anchor to follow the real entity at the given
     * Y offset. Mutates the {@code TransformComponent} position in-place,
     * which is safe from within tick systems (no structural change).
     */
    private void repositionAnchor(Ref<EntityStore> anchorRef,
                                  Vector3d realPosition,
                                  double offset,
                                  Store<EntityStore> store,
                                  ComponentType<EntityStore, TransformComponent> transformType) {
        TransformComponent transform = store.getComponent(anchorRef, transformType);
        if (transform == null) {
            return;
        }
        Vector3d pos = transform.getPosition();
        pos.setX(realPosition.getX());
        pos.setY(realPosition.getY() + offset);
        pos.setZ(realPosition.getZ());
    }
}
