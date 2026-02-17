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

final class AnchorEntityManager {

    private final Map<Ref<EntityStore>, AnchorState> anchors = new HashMap<>();
    private final List<AnchorState> pendingRemovals = new ArrayList<>();


    private static final class AnchorState {
        volatile Ref<EntityStore> anchorRef;
        volatile boolean spawnPending;
        double currentOffset;

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


    boolean isSpawnPending(Ref<EntityStore> realEntityRef) {
        AnchorState state = anchors.get(realEntityRef);
        return state != null && state.spawnPending;
    }


    boolean hasAnchor(Ref<EntityStore> realEntityRef) {
        return anchors.containsKey(realEntityRef);
    }


    void ensureAnchor(Ref<EntityStore> realEntityRef,
                      Vector3d realPosition,
                      double offset,
                      World world,
                      Store<EntityStore> store,
                      ComponentType<EntityStore, TransformComponent> transformType,
                      CommandBuffer<EntityStore> commandBuffer) {

        AnchorState state = anchors.get(realEntityRef);

        if (state == null) {

            state = new AnchorState(offset);
            anchors.put(realEntityRef, state);
            queueSpawn(realPosition, offset, world, state);
            return;
        }

        if (state.spawnPending) {

            state.currentOffset = offset;
            return;
        }


        if (state.anchorRef == null || !state.anchorRef.isValid()) {
            state.anchorRef = null;
            state.spawnPending = true;
            state.currentOffset = offset;
            state.lastPosition = null;
            state.velocity = null;
            queueSpawn(realPosition, offset, world, state);
            return;
        }


        state.currentOffset = offset;
        repositionAnchor(state.anchorRef, realPosition, offset, state, store, transformType, commandBuffer);
    }


    void removeAnchor(Ref<EntityStore> realEntityRef,
                      CommandBuffer<EntityStore> commandBuffer) {


        anchors.remove(realEntityRef);
    }


    List<Ref<EntityStore>> cleanupOrphanedAnchors(CommandBuffer<EntityStore> commandBuffer) {
        if (anchors.isEmpty()) {
            return List.of();
        }
        List<Ref<EntityStore>> orphanedAnchors = null;
        Iterator<Map.Entry<Ref<EntityStore>, AnchorState>> it = anchors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Ref<EntityStore>, AnchorState> entry = it.next();
            Ref<EntityStore> realRef = entry.getKey();
            if (!realRef.isValid()) {
                AnchorState state = entry.getValue();
                it.remove();

                if (!state.spawnPending && state.anchorRef != null && state.anchorRef.isValid()) {
                    if (orphanedAnchors == null) {
                        orphanedAnchors = new ArrayList<>();
                    }
                    orphanedAnchors.add(state.anchorRef);
                }
            }
        }
        return orphanedAnchors != null ? orphanedAnchors : List.of();
    }


    void clear() {
        anchors.clear();
        pendingRemovals.clear();
    }




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



            Ref<EntityStore> anchorRef = store.addEntity(holder, AddReason.LOAD);
            state.anchorRef = anchorRef;
            state.spawnPending = false;
        });
    }


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


        Vector3d targetPosition;

        if (state.lastPosition != null) {

            double dx = realPosition.getX() - state.lastPosition.getX();
            double dy = realPosition.getY() - state.lastPosition.getY();
            double dz = realPosition.getZ() - state.lastPosition.getZ();


            if (state.velocity != null) {

                dx = state.velocity.getX() * 0.7 + dx * 0.3;
                dy = state.velocity.getY() * 0.7 + dy * 0.3;
                dz = state.velocity.getZ() * 0.7 + dz * 0.3;
            }

            state.velocity = new Vector3d(dx, dy, dz);



            targetPosition = new Vector3d(
                    realPosition.getX() + dx,
                    realPosition.getY() + dy + offset,
                    realPosition.getZ() + dz
            );
        } else {

            targetPosition = new Vector3d(
                    realPosition.getX(),
                    realPosition.getY() + offset,
                    realPosition.getZ()
            );
        }


        state.lastPosition = new Vector3d(realPosition.getX(), realPosition.getY(), realPosition.getZ());


        TransformComponent newTransform = new TransformComponent(targetPosition, transform.getRotation());


        commandBuffer.replaceComponent(anchorRef, transformType, newTransform);
    }
}
