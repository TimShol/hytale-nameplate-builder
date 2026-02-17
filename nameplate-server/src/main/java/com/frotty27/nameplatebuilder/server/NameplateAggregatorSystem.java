package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.NameplateUpdate;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NameplateAggregatorSystem extends EntityTickingSystem<EntityStore> {

    private static final double VIEW_RANGE = 30.0;
    private static final double VIEW_CONE_THRESHOLD = 0.9;
    private static final String NO_DATA_HINT = "Type /npb to customize";
    private static final String ALL_HIDDEN_HINT = "";

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleComponentType;
    private final ComponentType<EntityStore, UUIDComponent> uuidComponentType;
    private final ComponentType<EntityStore, TransformComponent> transformComponentType;
    private final ComponentType<EntityStore, HeadRotation> headRotationType;
    private final ComponentType<EntityStore, BoundingBox> boundingBoxType;
    private final ComponentType<EntityStore, DeathComponent> deathComponentType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final ComponentType<EntityStore, NPCEntity> npcEntityType;
    private final ComponentType<EntityStore, Player> playerType;
    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;
    private final AnchorEntityManager anchorManager;

    NameplateAggregatorSystem(NameplateRegistry registry,
                              NameplatePreferenceStore preferences,
                              AdminConfigStore adminConfig,
                              ComponentType<EntityStore, NameplateData> nameplateDataType,
                              AnchorEntityManager anchorManager) {
        this.visibleComponentType = EntityTrackerSystems.Visible.getComponentType();
        this.uuidComponentType = UUIDComponent.getComponentType();
        this.transformComponentType = TransformComponent.getComponentType();
        this.headRotationType = HeadRotation.getComponentType();
        this.boundingBoxType = BoundingBox.getComponentType();
        this.deathComponentType = DeathComponent.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.npcEntityType = NPCEntity.getComponentType();
        this.playerType = Player.getComponentType();
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
        this.anchorManager = anchorManager;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(visibleComponentType, nameplateDataType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {



        List<Ref<EntityStore>> orphanedAnchors = anchorManager.cleanupOrphanedAnchors(commandBuffer);
        if (!orphanedAnchors.isEmpty()) {
            ComponentUpdate blankUpdate = nameplateUpdate("");
            for (Ref<EntityStore> orphanRef : orphanedAnchors) {
                EntityTrackerSystems.Visible anchorVisible = store.getComponent(orphanRef, visibleComponentType);
                if (anchorVisible != null && anchorVisible.visibleTo != null) {
                    for (EntityTrackerSystems.EntityViewer viewer : anchorVisible.visibleTo.values()) {
                        safeAnchorUpdate(viewer, orphanRef, blankUpdate);
                    }
                }
            }
        }

        EntityTrackerSystems.Visible visible = chunk.getComponent(index, visibleComponentType);
        if (visible == null || visible.visibleTo == null) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);






        DeathComponent deathComponent = store.getComponent(entityRef, deathComponentType);
        if (deathComponent != null) {
            Ref<EntityStore> deadAnchorRef = anchorManager.getAnchorRef(entityRef);
            ComponentUpdate emptyUpdate = nameplateUpdate("");
            for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
                viewerEntry.getValue().queueUpdate(entityRef, emptyUpdate);
                if (deadAnchorRef != null) {
                    safeAnchorUpdate(viewerEntry.getValue(), deadAnchorRef, emptyUpdate);
                }
            }


            commandBuffer.removeComponent(entityRef, nameplateDataType);
            return;
        }

        String entityTypeId = resolveEntityTypeId(chunk);




        NameplateData entityData = store.getComponent(entityRef, nameplateDataType);
        if (entityData == null) {

            if (anchorManager.hasAnchor(entityRef)) {
                anchorManager.removeAnchor(entityRef, commandBuffer);
            }
            return;
        }

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();



        if (!segments.isEmpty() && segments.keySet().stream().allMatch(adminConfig::isDisabled)) {
            Ref<EntityStore> disAnchorRef = anchorManager.getAnchorRef(entityRef);
            ComponentUpdate emptyUpdate = nameplateUpdate("");
            for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
                viewerEntry.getValue().queueUpdate(entityRef, emptyUpdate);
                if (disAnchorRef != null) {
                    safeAnchorUpdate(viewerEntry.getValue(), disAnchorRef, emptyUpdate);
                }
            }
            return;
        }

        List<SegmentKey> available = entityData.isEmpty()
                ? List.of()
                : resolveAvailableKeys(entityData, segments);

        Comparator<SegmentKey> defaultComparator = Comparator
                .comparing((SegmentKey k) -> {
                    NameplateRegistry.Segment s = segments.get(k);
                    return s != null ? s.pluginName() : k.pluginId();
                })
                .thenComparing(k -> {
                    NameplateRegistry.Segment s = segments.get(k);
                    return s != null ? s.displayName() : k.segmentId();
                });


        TransformComponent realTransform = store.getComponent(entityRef, transformComponentType);
        Vector3d realPosition = realTransform != null ? realTransform.getPosition() : null;



        double entityHeight = resolveEntityHeight(store, entityRef);



        String tabEntityType = resolveTabEntityType(store, entityRef);




        boolean isPlayer = store.getComponent(entityRef, playerType) != null;



        int viewerCount = visible.visibleTo.size();
        double[] viewerOffsets = new double[viewerCount];
        boolean[] viewerWantsAnchor = new boolean[viewerCount];
        String[] viewerPreferenceTypes = new String[viewerCount];
        UUID[] viewerUuids = new UUID[viewerCount];
        double maxOffset = 0.0;
        boolean anyViewerNeedsAnchor = false;

        int vi = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            UUID viewerUuid = getUuid(store, viewerRef);
            viewerUuids[vi] = viewerUuid;



            viewerPreferenceTypes[vi] = tabEntityType;

            double userOffset = preferences.getOffset(viewerUuid, "*");


            double totalOffset = entityHeight + userOffset;
            viewerOffsets[vi] = totalOffset;

            viewerWantsAnchor[vi] = !isPlayer && userOffset != 0.0;
            if (!isPlayer && userOffset != 0.0) {
                anyViewerNeedsAnchor = true;
                if (Math.abs(totalOffset) > Math.abs(maxOffset)) {
                    maxOffset = totalOffset;
                }
            }
            vi++;
        }





        if (anyViewerNeedsAnchor && !isPlayer && realPosition != null) {
            World world = resolveWorld(store, entityRef);
            if (world != null) {
                anchorManager.ensureAnchor(entityRef, realPosition, maxOffset,
                        world, store, transformComponentType, commandBuffer);
            }
        } else if (anchorManager.hasAnchor(entityRef)) {

            anchorManager.removeAnchor(entityRef, commandBuffer);
        }

        Ref<EntityStore> anchorRef = anchorManager.getAnchorRef(entityRef);



        vi = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = viewerUuids[vi];
            String preferenceEntityType = viewerPreferenceTypes[vi];
            double viewerOffset = viewerOffsets[vi];
            boolean wantsAnchor = viewerWantsAnchor[vi];
            vi++;


            if (!preferences.isNameplatesEnabled(viewerUuid)) {
                ComponentUpdate emptyUpdate = nameplateUpdate("");
                viewer.queueUpdate(entityRef, emptyUpdate);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, emptyUpdate);
                }
                continue;
            }



            if (preferences.isOnlyShowWhenLooking(viewerUuid, "*")
                    && !isLookingAt(store, viewerRef, entityRef)) {
                ComponentUpdate emptyUpdate = nameplateUpdate("");
                viewer.queueUpdate(entityRef, emptyUpdate);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, emptyUpdate);
                }
                continue;
            }

            String text;
            if (available.isEmpty()) {



                text = ALL_HIDDEN_HINT;
            } else {
                List<SegmentKey> chain = preferences.getChain(viewerUuid, preferenceEntityType, available, defaultComparator);
                text = buildText(chain, entityData, viewerUuid, preferenceEntityType);
                if (text.isEmpty()) {


                    text = NO_DATA_HINT;
                }
            }


            if (wantsAnchor && anchorRef != null) {


                viewer.queueUpdate(entityRef, nameplateUpdate(""));
                safeAnchorUpdate(viewer, anchorRef, nameplateUpdate(text));
            } else if (wantsAnchor) {


                viewer.queueUpdate(entityRef, nameplateUpdate(text));
            } else {

                viewer.queueUpdate(entityRef, nameplateUpdate(text));

                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, nameplateUpdate(""));
                }
            }
        }
    }


    private String resolveTabEntityType(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Player player = store.getComponent(entityRef, playerType);
        return player != null
                ? NameplateBuilderPage.ENTITY_TYPE_PLAYERS
                : NameplateBuilderPage.ENTITY_TYPE_NPCS;
    }




    private World resolveWorld(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NPCEntity npc = store.getComponent(entityRef, npcEntityType);
        if (npc != null) {
            return npc.getWorld();
        }
        Player player = store.getComponent(entityRef, playerType);
        if (player != null) {
            return player.getWorld();
        }
        return null;
    }




    private double resolveEntityHeight(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            BoundingBox bb = store.getComponent(entityRef, boundingBoxType);
            if (bb == null) return 0.0;
            com.hypixel.hytale.math.shape.Box box = bb.getBoundingBox();
            if (box == null) return 0.0;



            double h = box.max.getY();
            return Math.max(0.0, h);
        } catch (Throwable _) {

            return 0.0;
        }
    }




    private List<SegmentKey> resolveAvailableKeys(NameplateData entityData,
                                                   Map<SegmentKey, NameplateRegistry.Segment> segments) {
        List<SegmentKey> keys = new ArrayList<>();
        for (String entryKey : entityData.getEntries().keySet()) {

            if (entryKey.startsWith("_")) {
                continue;
            }


            if (entryKey.contains(".")) {
                continue;
            }
            SegmentKey matched = findSegmentKey(entryKey, segments);
            if (matched != null) {
                if (adminConfig.isDisabled(matched)) {
                    continue;
                }
                if (!keys.contains(matched)) {
                    keys.add(matched);
                }
            } else {

                SegmentKey synthetic = new SegmentKey("_unknown", entryKey);
                if (!keys.contains(synthetic)) {
                    keys.add(synthetic);
                }
            }
        }
        return keys;
    }


    private SegmentKey findSegmentKey(String entryKey, Map<SegmentKey, NameplateRegistry.Segment> segments) {
        SegmentKey best = null;
        boolean bestBuiltIn = false;
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            if (!entry.getKey().segmentId().equals(entryKey)) {
                continue;
            }
            if (best == null) {
                best = entry.getKey();
                bestBuiltIn = entry.getValue().builtIn();
                continue;
            }
            boolean candidateBuiltIn = entry.getValue().builtIn();

            if (candidateBuiltIn && !bestBuiltIn) {
                best = entry.getKey();
                bestBuiltIn = true;
            } else if (candidateBuiltIn == bestBuiltIn) {

                if (entry.getKey().pluginId().compareTo(best.pluginId()) < 0) {
                    best = entry.getKey();
                }
            }
        }
        return best;
    }




    private String buildText(List<SegmentKey> ordered,
                             NameplateData entityData,
                             UUID viewerUuid,
                             String entityTypeId) {
        StringBuilder builder = new StringBuilder();
        SegmentKey prevKey = null;
        for (SegmentKey key : ordered) {

            if (adminConfig.isDisabled(key)) {
                continue;
            }

            if (!adminConfig.isRequired(key) && !preferences.isEnabled(viewerUuid, entityTypeId, key)) {
                continue;
            }


            int variantIndex = preferences.getSelectedVariant(viewerUuid, entityTypeId, key);
            String text;
            if (variantIndex > 0) {

                String variantText = entityData.getText(key.segmentId() + "." + variantIndex);
                text = variantText != null && !variantText.isBlank() ? variantText : entityData.getText(key.segmentId());
            } else {
                text = entityData.getText(key.segmentId());
            }
            if (text == null || text.isBlank()) {
                continue;
            }



            NameplateRegistry.Segment segDef = registry.getSegments().get(key);
            if (segDef != null && segDef.supportsPrefixSuffix() && variantIndex > 0) {
                String barEmpty = preferences.getBarEmptyChar(viewerUuid, entityTypeId, key);
                if (barEmpty.length() == 1) {
                    text = text.replace('.', barEmpty.charAt(0));
                }
            }

            String pfx = preferences.getPrefix(viewerUuid, entityTypeId, key);
            String sfx = preferences.getSuffix(viewerUuid, entityTypeId, key);
            if (!pfx.isEmpty() || !sfx.isEmpty()) {
                text = pfx + text + sfx;
            }
            if (!builder.isEmpty() && prevKey != null) {
                builder.append(preferences.getSeparatorAfter(viewerUuid, entityTypeId, prevKey));
            }
            builder.append(text);
            prevKey = key;
        }
        return builder.toString();
    }




    private boolean isLookingAt(Store<EntityStore> store,
                                Ref<EntityStore> viewerRef,
                                Ref<EntityStore> entityRef) {
        TransformComponent viewerTransform = store.getComponent(viewerRef, transformComponentType);
        if (viewerTransform == null) {
            return true;
        }

        TransformComponent entityTransform = store.getComponent(entityRef, transformComponentType);
        if (entityTransform == null) {
            return true;
        }

        Vector3d viewerPos = viewerTransform.getPosition();
        Vector3d entityPos = entityTransform.getPosition();

        double distance = viewerPos.distanceTo(entityPos);
        if (distance > VIEW_RANGE || distance < 0.5) {
            return false;
        }


        Vector3d lookDir;
        HeadRotation viewerHead = store.getComponent(viewerRef, headRotationType);
        if (viewerHead != null) {
            lookDir = viewerHead.getDirection();
        } else {

            Vector3f rotation = viewerTransform.getRotation();
            lookDir = directionFromPitchYaw(rotation.getPitch(), rotation.getYaw());
        }

        Vector3d toEntity = new Vector3d(
                entityPos.getX() - viewerPos.getX(),
                entityPos.getY() - viewerPos.getY(),
                entityPos.getZ() - viewerPos.getZ()
        ).normalize();

        double dot = lookDir.dot(toEntity);
        return dot >= VIEW_CONE_THRESHOLD;
    }


    private static Vector3d directionFromPitchYaw(float pitchDeg, float yawDeg) {
        double pitch = Math.toRadians(pitchDeg);
        double yaw = Math.toRadians(yawDeg);
        double cosPitch = Math.cos(pitch);
        return new Vector3d(
                -Math.sin(yaw) * cosPitch,
                -Math.sin(pitch),
                Math.cos(yaw) * cosPitch
        ).normalize();
    }




    private static NameplateUpdate nameplateUpdate(String text) {
        return new NameplateUpdate(text == null || text.isEmpty() ? " " : text);
    }


    private static void safeAnchorUpdate(EntityTrackerSystems.EntityViewer viewer,
                                         Ref<EntityStore> anchorRef,
                                         ComponentUpdate update) {
        try {
            if (anchorRef == null || !anchorRef.isValid()) {
                return;
            }
            viewer.queueUpdate(anchorRef, update);
        } catch (IllegalArgumentException | IllegalStateException _) {

        }
    }

    private UUID getUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        UUIDComponent uuidComponent = store.getComponent(ref, uuidComponentType);
        if (uuidComponent != null) {
            return uuidComponent.getUuid();
        }
        return new UUID(0L, 0L);
    }

    private String resolveEntityTypeId(ArchetypeChunk<EntityStore> chunk) {
        Archetype<EntityStore> archetype = chunk.getArchetype();
        for (int i = 0; i < archetype.length(); i++) {
            ComponentType<EntityStore, ?> type = archetype.get(i);
            if (type == null) {
                continue;
            }
            Class<?> cls = type.getTypeClass();
            if (cls != null && Entity.class.isAssignableFrom(cls)) {
                @SuppressWarnings("unchecked")
                Class<? extends Entity> entityClass = (Class<? extends Entity>) cls;
                String id = EntityModule.get().getIdentifier(entityClass);
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        }
        return "*";
    }
}
