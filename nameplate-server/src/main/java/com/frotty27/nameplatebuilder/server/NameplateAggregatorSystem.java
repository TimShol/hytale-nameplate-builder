package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.NameplateUpdate;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
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

import java.util.*;

final class NameplateAggregatorSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    static final UUID ADMIN_CHAIN_UUID = new UUID(0L, 0L);

    private static final double VIEW_RANGE = 12.0;
    private static final double VIEW_CONE_THRESHOLD = 0.9;

    private static final ComponentUpdate EMPTY_UPDATE = new NameplateUpdate(" ");

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleComponentType;
    private final ComponentType<EntityStore, UUIDComponent> uuidComponentType;
    private final ComponentType<EntityStore, TransformComponent> transformComponentType;
    private final ComponentType<EntityStore, HeadRotation> headRotationType;
    private final ComponentType<EntityStore, BoundingBox> boundingBoxType;
    private final ComponentType<EntityStore, DeathComponent> deathComponentType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final ComponentType<EntityStore, NPCEntity> npcEntityType;
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, MovementStatesComponent> movementStatesType;
    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;
    private final AnchorEntityManager anchorManager;
    private final EntitySourceService entitySourceService;

    private final IdentityHashMap<Archetype<EntityStore>, String> entityTypeIdCache = new IdentityHashMap<>();
    private final IdentityHashMap<Archetype<EntityStore>, String> namespaceCache = new IdentityHashMap<>();

    private final IdentityHashMap<Archetype<EntityStore>, List<SegmentKey>> resolverKeysCache = new IdentityHashMap<>();
    private int resolverKeysCacheVersion = -1;
    private int resolverKeysAdminVersion = -1;

    private final Set<SegmentKey> trackedSegmentKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile long lastCleanupEntityIndex = -1;

    private volatile Comparator<SegmentKey> cachedComparator;
    private volatile int cachedComparatorVersion = -1;

    private static final ThreadLocal<StringBuilder> TEXT_BUILDER =
            ThreadLocal.withInitial(() -> new StringBuilder(128));

    NameplateAggregatorSystem(NameplateRegistry registry,
                              NameplatePreferenceStore preferences,
                              AdminConfigStore adminConfig,
                              ComponentType<EntityStore, NameplateData> nameplateDataType,
                              AnchorEntityManager anchorManager,
                              EntitySourceService entitySourceService) {
        this.visibleComponentType = EntityTrackerSystems.Visible.getComponentType();
        this.uuidComponentType = UUIDComponent.getComponentType();
        this.transformComponentType = TransformComponent.getComponentType();
        this.headRotationType = HeadRotation.getComponentType();
        this.boundingBoxType = BoundingBox.getComponentType();
        this.deathComponentType = DeathComponent.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.npcEntityType = NPCEntity.getComponentType();
        this.playerType = Player.getComponentType();
        this.movementStatesType = MovementStatesComponent.getComponentType();
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
        this.anchorManager = anchorManager;
        this.entitySourceService = entitySourceService;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(visibleComponentType, nameplateDataType);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> chunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        try {
        NPCEntity npcEntity = store.getComponent(entityRef, npcEntityType);
        Player playerEntity = store.getComponent(entityRef, playerType);
        World entityWorld = npcEntity != null ? npcEntity.getWorld()
                : playerEntity != null ? playerEntity.getWorld() : null;

        if (index == 0 && lastCleanupEntityIndex != index) {
            lastCleanupEntityIndex = index;
            if (entityWorld != null) {
                List<Ref<EntityStore>> orphanedAnchors = anchorManager.cleanupOrphanedAnchors(entityWorld);
                if (!orphanedAnchors.isEmpty()) {
                    for (Ref<EntityStore> orphanRef : orphanedAnchors) {
                        EntityTrackerSystems.Visible anchorVisible = store.getComponent(orphanRef, visibleComponentType);
                        if (anchorVisible != null && anchorVisible.visibleTo != null) {
                            for (EntityTrackerSystems.EntityViewer viewer : anchorVisible.visibleTo.values()) {
                                safeAnchorUpdate(viewer, orphanRef, EMPTY_UPDATE);
                            }
                        }
                    }
                }
            }
        }

        EntityTrackerSystems.Visible visible = chunk.getComponent(index, visibleComponentType);
        if (visible == null) {
            return;
        }

        boolean noViewers = visible.visibleTo == null || visible.visibleTo.isEmpty();

        DeathComponent deathComponent = store.getComponent(entityRef, deathComponentType);
        if (deathComponent != null) {
            if (!noViewers) {
                Ref<EntityStore> deadAnchorRef = anchorManager.getAnchorRef(entityRef);
                for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
                    viewerEntry.getValue().queueUpdate(entityRef, EMPTY_UPDATE);
                    if (deadAnchorRef != null) {
                        safeAnchorUpdate(viewerEntry.getValue(), deadAnchorRef, EMPTY_UPDATE);
                    }
                }
            }
            commandBuffer.removeComponent(entityRef, nameplateDataType);
            return;
        }

        if (noViewers) {
            if (anchorManager.hasAnchor(entityRef) && entityWorld != null) {
                anchorManager.removeAnchor(entityRef, entityWorld);
            }
            return;
        }

        if (!adminConfig.isMasterEnabled()) {
            sendEmptyToAll(visible, entityRef);
            return;
        }

        Archetype<EntityStore> archetype = chunk.getArchetype();
        String namespace = namespaceCache.get(archetype);
        if (namespace == null) {
            String entityTypeId = resolveEntityTypeId(chunk);
            namespace = AdminConfigStore.extractNamespace(entityTypeId);
            if ("*".equals(namespace) || namespace.isEmpty()) {
                namespace = "hytale";
            }
            namespaceCache.put(archetype, namespace);
        }
        boolean isPlayer = playerEntity != null;

        if (isPlayer && !adminConfig.isPlayerChainEnabled()) {
            sendEmptyToAll(visible, entityRef);
            return;
        }
        if (!isPlayer && !adminConfig.isNpcChainEnabled()) {
            sendEmptyToAll(visible, entityRef);
            return;
        }
        if (!isPlayer && !adminConfig.isNamespaceEnabled(namespace)) {
            sendEmptyToAll(visible, entityRef);
            return;
        }
        if (!isPlayer && npcEntity != null) {
            String roleName = npcEntity.getRoleName();
            if (roleName != null && (adminConfig.isNpcBlacklisted(roleName) || adminConfig.matchesBlacklistPattern(roleName))) {
                sendEmptyToAll(visible, entityRef);
                return;
            }
        }

        if (!isPlayer && npcEntity != null) {
            var source = entitySourceService.getSource(npcEntity);
            if (source.type() == EntitySourceService.SourceType.MOD
                    && !registry.isModIntegrated(source.modName())
                    && !adminConfig.isEntitySourceDefaultsEnabled(source.modName())) {
                sendEmptyToAll(visible, entityRef);
                return;
            }
        }

        if (entityWorld != null) {
            String worldName = entityWorld.getName();
            if (!adminConfig.isWorldEnabled(worldName)) {
                sendEmptyToAll(visible, entityRef);
                return;
            }
        }

        if (isPlayer) {
            MovementStatesComponent movementStates = store.getComponent(entityRef, movementStatesType);
            if (movementStates != null && movementStates.getMovementStates() != null
                    && movementStates.getMovementStates().crouching) {
                sendEmptyToAll(visible, entityRef);
                return;
            }
        }

        NameplateData entityData = store.getComponent(entityRef, nameplateDataType);
        if (entityData == null) {
            if (anchorManager.hasAnchor(entityRef)) {
                if (entityWorld != null) {
                    anchorManager.removeAnchor(entityRef, entityWorld);
                }
            }
            return;
        }

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();

        if (!segments.isEmpty() && adminConfig.areAllSegmentsDisabled(segments.keySet())) {
            sendEmptyToAll(visible, entityRef);
            return;
        }

        List<SegmentKey> available = resolveAvailableKeys(entityData, segments, store, entityRef, chunk);

        for (SegmentKey key : available) {
            if (trackedSegmentKeys.contains(key)) continue;
            trackedSegmentKeys.add(key);
            NameplateRegistry.Segment segment = segments.get(key);
            boolean builtIn = segment != null && segment.builtIn();
            if (builtIn) {
                adminConfig.trackNamespaceSegment("hytale", key.segmentId(), true);
            } else {
                String modId = segment != null ? segment.pluginId() : key.pluginId();
                String modNamespace = AdminConfigStore.extractModName(modId);
                if (modNamespace.isEmpty()) {
                    modNamespace = AdminConfigStore.extractNamespace(modId);
                }
                if (modNamespace.isEmpty()) continue;
                adminConfig.trackNamespaceSegment(modNamespace, key.segmentId(), false);
                if (segment != null && segment.pluginName() != null) {
                    adminConfig.trackNamespaceDisplayName(modNamespace, segment.pluginName());
                }
            }
        }

        Comparator<SegmentKey> defaultComparator = getOrBuildComparator(segments);

        TransformComponent realTransform = store.getComponent(entityRef, transformComponentType);
        Vector3d realPosition = realTransform != null ? realTransform.getPosition() : null;

        double entityHeight = resolveEntityHeight(store, entityRef);

        String preferenceEntityType = isPlayer
                ? NameplateBuilderPage.ENTITY_TYPE_PLAYERS
                : NameplateBuilderPage.ENTITY_TYPE_NPCS;

        int viewerCount = visible.visibleTo.size();
        boolean[] viewerWantsAnchor = new boolean[viewerCount];
        UUID[] viewerUuids = new UUID[viewerCount];
        double maxOffset = 0.0;
        boolean anyViewerNeedsAnchor = false;

        int viewerIndex = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            UUID viewerUuid = getUuid(store, viewerRef);
            if (viewerUuid == null) { viewerIndex++; continue; }
            viewerUuids[viewerIndex] = viewerUuid;

            double userOffset = preferences.getOffset(viewerUuid, "*");

            double totalOffset = entityHeight + userOffset;
            viewerWantsAnchor[viewerIndex] = !isPlayer && userOffset != 0.0;
            if (!isPlayer && userOffset != 0.0) {
                anyViewerNeedsAnchor = true;
                if (Math.abs(totalOffset) > Math.abs(maxOffset)) {
                    maxOffset = totalOffset;
                }
            }
            viewerIndex++;
        }

        if (anyViewerNeedsAnchor && !isPlayer && realPosition != null) {
            if (entityWorld != null) {
                anchorManager.ensureAnchor(entityRef, realPosition, maxOffset,
                        entityWorld, store, transformComponentType, commandBuffer);
            }
        } else if (anchorManager.hasAnchor(entityRef)) {
            if (entityWorld != null) {
                anchorManager.removeAnchor(entityRef, entityWorld);
            }
        }

        Ref<EntityStore> anchorRef = anchorManager.getAnchorRef(entityRef);

        boolean locked = isPlayer ? adminConfig.isPlayerChainLocked() : adminConfig.isNpcChainLocked();
        String lockedText;
        ComponentUpdate lockedTextUpdate = null;
        if (locked && !available.isEmpty()) {
            List<SegmentKey> lockedChain = preferences.getChain(ADMIN_CHAIN_UUID, preferenceEntityType, available, defaultComparator);
            lockedText = buildText(lockedChain, entityData, ADMIN_CHAIN_UUID, preferenceEntityType, store, entityRef, segments);
            if (lockedText.isEmpty()) {
                lockedTextUpdate = EMPTY_UPDATE;
            } else {
                lockedTextUpdate = nameplateUpdate(lockedText);
            }
        }

        String viewerChainType = isPlayer ? "_players" : "_npcs";

        viewerIndex = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = viewerUuids[viewerIndex];
            boolean wantsAnchor = viewerWantsAnchor[viewerIndex];
            viewerIndex++;

            if (viewerUuid == null) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                continue;
            }

            if (!preferences.isNameplatesEnabled(viewerUuid)) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                if (anchorRef != null) safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                continue;
            }

            if (!preferences.isChainEnabled(viewerUuid, viewerChainType)) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                }
                continue;
            }

            if (entityWorld != null && !preferences.isWorldEnabled(viewerUuid, entityWorld.getName())) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                }
                continue;
            }

            if (!isPlayer && preferences.isOnlyShowWhenLooking(viewerUuid, "*")
                    && !isLookingAt(store, viewerRef, entityRef)) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                }
                continue;
            }

            ComponentUpdate textUpdate;
            if (locked && lockedTextUpdate != null) {
                textUpdate = lockedTextUpdate;
            } else if (available.isEmpty()) {
                textUpdate = EMPTY_UPDATE;
            } else {
                UUID chainUuid = locked ? ADMIN_CHAIN_UUID : viewerUuid;
                List<SegmentKey> chain = preferences.getChain(chainUuid, preferenceEntityType, available, defaultComparator);
                String text = buildText(chain, entityData, chainUuid, preferenceEntityType, store, entityRef, segments);
                if (text.isEmpty()) {
                    textUpdate = EMPTY_UPDATE;
                } else {
                    textUpdate = nameplateUpdate(text);
                }
            }

            if (wantsAnchor && anchorRef != null) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                safeAnchorUpdate(viewer, anchorRef, textUpdate);
            } else {
                viewer.queueUpdate(entityRef, textUpdate);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                }
            }
        }
        } catch (IllegalStateException ignored) {
        }
    }

    private Comparator<SegmentKey> getOrBuildComparator(Map<SegmentKey, NameplateRegistry.Segment> segments) {
        int currentVersion = registry.getVersion();
        if (cachedComparator != null && cachedComparatorVersion == currentVersion) {
            return cachedComparator;
        }
        cachedComparator = Comparator
                .comparing((SegmentKey key) -> {
                    NameplateRegistry.Segment segment = segments.get(key);
                    return segment != null ? segment.pluginName() : key.pluginId();
                })
                .thenComparing(key -> {
                    NameplateRegistry.Segment segment = segments.get(key);
                    return segment != null ? segment.displayName() : key.segmentId();
                });
        cachedComparatorVersion = currentVersion;
        return cachedComparator;
    }

    private double resolveEntityHeight(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            BoundingBox boundingBox = store.getComponent(entityRef, boundingBoxType);
            if (boundingBox == null) return 0.0;
            Box box = boundingBox.getBoundingBox();
            if (box == null) return 0.0;
            double height = box.max.getY();
            return Math.max(0.0, height);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private List<SegmentKey> resolveAvailableKeys(NameplateData entityData,
                                                   Map<SegmentKey, NameplateRegistry.Segment> segments,
                                                   Store<EntityStore> store,
                                                   Ref<EntityStore> entityRef,
                                                   ArchetypeChunk<EntityStore> chunk) {
        List<SegmentKey> result = new ArrayList<>();

        for (String entryKey : entityData.getEntriesDirect().keySet()) {
            if (entryKey.charAt(0) == '_') continue;
            if (entryKey.indexOf('.') >= 0) continue;
            SegmentKey matched = registry.findBySegmentId(entryKey);
            if (matched != null) {
                if (!adminConfig.isDisabled(matched) && isNamespaceEnabledForSegment(matched, segments)) {
                    result.add(matched);
                }
            } else {
                result.add(new SegmentKey("_unknown", entryKey));
            }
        }

        List<SegmentKey> resolverKeys = getResolverKeysForArchetype(chunk.getArchetype(), segments);
        for (int i = 0, size = resolverKeys.size(); i < size; i++) {
            SegmentKey key = resolverKeys.get(i);
            if (!result.contains(key)) {
                result.add(key);
            }
        }

        return result;
    }

    private List<SegmentKey> getResolverKeysForArchetype(Archetype<EntityStore> archetype,
                                                          Map<SegmentKey, NameplateRegistry.Segment> segments) {
        int currentVersion = registry.getVersion();
        int currentAdminVersion = adminConfig.getConfigVersion();
        if (resolverKeysCacheVersion != currentVersion || resolverKeysAdminVersion != currentAdminVersion) {
            resolverKeysCache.clear();
            resolverKeysCacheVersion = currentVersion;
            resolverKeysAdminVersion = currentAdminVersion;
        }

        List<SegmentKey> cachedKeys = resolverKeysCache.get(archetype);
        if (cachedKeys != null) return cachedKeys;

        List<SegmentKey> keys = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            NameplateRegistry.Segment segment = entry.getValue();
            if (segment.resolver() == null) continue;
            SegmentKey key = entry.getKey();
            if (adminConfig.isDisabled(key)) continue;
            if (!isNamespaceEnabledForSegment(key, segments)) continue;

            if (segment.requiredComponent() != null) {
                boolean hasComponent = false;
                for (int i = 0; i < archetype.length(); i++) {
                    if (archetype.get(i) == segment.requiredComponent()) {
                        hasComponent = true;
                        break;
                    }
                }
                if (!hasComponent) continue;
            }

            keys.add(key);
        }

        resolverKeysCache.put(archetype, keys);
        return keys;
    }

    private boolean isNamespaceEnabledForSegment(SegmentKey key, Map<SegmentKey, NameplateRegistry.Segment> segments) {
        NameplateRegistry.Segment segment = segments.get(key);
        if (segment == null) return true;
        if (segment.builtIn()) {
            return adminConfig.isNamespaceEnabled("hytale");
        }
        String modId = segment.pluginId();
        String modNamespace = AdminConfigStore.extractModName(modId);
        if (modNamespace.isEmpty()) {
            modNamespace = AdminConfigStore.extractNamespace(modId);
        }
        return adminConfig.isNamespaceEnabled(modNamespace);
    }

    private String buildText(List<SegmentKey> ordered,
                             NameplateData entityData,
                             UUID viewerUuid,
                             String entityTypeId,
                             Store<EntityStore> store,
                             Ref<EntityStore> entityRef,
                             Map<SegmentKey, NameplateRegistry.Segment> segments) {
        StringBuilder builder = TEXT_BUILDER.get();
        builder.setLength(0);

        var prefs = preferences.getSetDirect(viewerUuid, entityTypeId);
        String defaultSeparator = prefs != null ? prefs.separator : " - ";

        SegmentKey previousSegmentKey = null;
        for (int i = 0, size = ordered.size(); i < size; i++) {
            SegmentKey key = ordered.get(i);
            if (adminConfig.isDisabled(key)) {
                continue;
            }
            if (!adminConfig.isRequired(key)) {
                int id = key.id();
                boolean disabled = prefs != null && id >= 0 && id < prefs.disabled.length && prefs.disabled[id];
                if (disabled) continue;
            }

            int id = key.id();
            int variantIndex = prefs != null && id >= 0 && id < prefs.selectedVariant.length ? prefs.selectedVariant[id] : 0;
            String segmentId = key.segmentId();
            String text;
            if (variantIndex > 0) {
                String variantText = entityData.getText(segmentId + "." + variantIndex);
                text = variantText != null && !variantText.isEmpty() ? variantText : entityData.getText(segmentId);
            } else {
                text = entityData.getText(segmentId);
            }

            if (text == null || text.isEmpty()) {
                NameplateRegistry.Segment segment = segments.get(key);
                if (segment != null && segment.resolver() != null) {
                    try {
                        text = segment.resolver().resolve(store, entityRef, variantIndex);
                    } catch (Exception ignored) {
                        text = null;
                    }
                }
            }

            if (text == null || text.isEmpty()) {
                continue;
            }

            NameplateRegistry.Segment segmentDefinition = segments.get(key);
            if (segmentDefinition != null && segmentDefinition.supportsPrefixSuffix() && variantIndex > 0) {
                String barEmpty = prefs != null && id >= 0 && id < prefs.barEmptyChar.length && prefs.barEmptyChar[id] != null ? prefs.barEmptyChar[id] : "-";
                if (barEmpty.length() == 1 && barEmpty.charAt(0) != '-') {
                    text = text.replace('.', barEmpty.charAt(0));
                }
            }

            if (!builder.isEmpty() && previousSegmentKey != null) {
                int prevId = previousSegmentKey.id();
                String sep = prefs != null && prevId >= 0 && prevId < prefs.separatorAfter.length && prefs.separatorAfter[prevId] != null
                        ? prefs.separatorAfter[prevId] : defaultSeparator;
                builder.append(sep);
            }

            String prefix = prefs != null && id >= 0 && id < prefs.prefix.length && prefs.prefix[id] != null ? prefs.prefix[id] : "";
            String suffix = prefs != null && id >= 0 && id < prefs.suffix.length && prefs.suffix[id] != null ? prefs.suffix[id] : "";
            if (!prefix.isEmpty()) {
                builder.append(prefix);
            }
            builder.append(text);
            if (!suffix.isEmpty()) {
                builder.append(suffix);
            }

            previousSegmentKey = key;
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

        double deltaX = entityPos.getX() - viewerPos.getX();
        double deltaY = entityPos.getY() - viewerPos.getY();
        double deltaZ = entityPos.getZ() - viewerPos.getZ();
        double distanceSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

        if (distanceSq > VIEW_RANGE * VIEW_RANGE || distanceSq < 0.25) {
            return false;
        }

        Vector3d lookDir;
        HeadRotation viewerHead = store.getComponent(viewerRef, headRotationType);
        if (viewerHead != null) {
            lookDir = viewerHead.getDirection();
            if (lookDir == null) return true;
        } else {
            Vector3f rotation = viewerTransform.getRotation();
            lookDir = directionFromPitchYaw(rotation.getPitch(), rotation.getYaw());
        }

        double dotProduct = lookDir.getX() * deltaX + lookDir.getY() * deltaY + lookDir.getZ() * deltaZ;
        return dotProduct >= VIEW_CONE_THRESHOLD * Math.sqrt(distanceSq);
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

    private void sendEmptyToAll(EntityTrackerSystems.Visible visible,
                               Ref<EntityStore> entityRef) {
        Ref<EntityStore> anchorRef = anchorManager.getAnchorRef(entityRef);
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            viewerEntry.getValue().queueUpdate(entityRef, EMPTY_UPDATE);
            if (anchorRef != null) {
                safeAnchorUpdate(viewerEntry.getValue(), anchorRef, EMPTY_UPDATE);
            }
        }
    }

    private static ComponentUpdate nameplateUpdate(String text) {
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
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
    }

    private UUID getUuid(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            UUIDComponent uuidComponent = store.getComponent(entityRef, uuidComponentType);
            if (uuidComponent != null) {
                return uuidComponent.getUuid();
            }
        } catch (IllegalStateException ignored) {
        }
        return null;
    }

    private String resolveEntityTypeId(ArchetypeChunk<EntityStore> chunk) {
        Archetype<EntityStore> archetype = chunk.getArchetype();
        String cachedEntityTypeId = entityTypeIdCache.get(archetype);
        if (cachedEntityTypeId != null) {
            return cachedEntityTypeId;
        }
        String result = computeEntityTypeId(archetype);
        entityTypeIdCache.put(archetype, result);
        return result;
    }

    private String computeEntityTypeId(Archetype<EntityStore> archetype) {
        for (int i = 0; i < archetype.length(); i++) {
            ComponentType<EntityStore, ?> type = archetype.get(i);
            if (type == null) {
                continue;
            }
            Class<?> typeClass = type.getTypeClass();
            if (typeClass != null && Entity.class.isAssignableFrom(typeClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Entity> entityClass = (Class<? extends Entity>) typeClass;
                String id = EntityModule.get().getIdentifier(entityClass);
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        }
        return "hytale";
    }
}
