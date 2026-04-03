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

    private static final double VIEW_RANGE = 30.0;
    private static final double VIEW_CONE_THRESHOLD = 0.9;
    private static final String NO_DATA_HINT = "Type /npb to customize";
    private static final String ALL_HIDDEN_HINT = "";

    // OPT-1: Cached NameplateUpdate objects to avoid millions of identical allocations per second
    private static final ComponentUpdate EMPTY_UPDATE = new NameplateUpdate(" ");
    private static final ComponentUpdate NO_DATA_UPDATE = new NameplateUpdate(NO_DATA_HINT);

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

    // OPT-3: Cache entity type ID per archetype to avoid reflection every tick
    private final IdentityHashMap<Archetype<EntityStore>, String> entityTypeIdCache = new IdentityHashMap<>();

    // OPT-10: Guard orphan cleanup to run once per tick cycle
    private volatile long lastCleanupEntityIndex = -1;

    // OPT-13: Cached comparator - rebuilt only when segments change
    private volatile Comparator<SegmentKey> cachedComparator;
    private volatile int cachedComparatorVersion = -1;

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
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> chunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        // OPT-4: Resolve world once per entity instead of up to 5 times
        World entityWorld = resolveWorld(store, entityRef);

        // OPT-10: Run orphan cleanup once per tick cycle, not per entity
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

        if (!adminConfig.isMasterEnabled()) {
            sendEmptyToAll(visible, entityRef);
            return;
        }

        DeathComponent deathComponent = store.getComponent(entityRef, deathComponentType);
        if (deathComponent != null) {
            Ref<EntityStore> deadAnchorRef = anchorManager.getAnchorRef(entityRef);
            for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
                viewerEntry.getValue().queueUpdate(entityRef, EMPTY_UPDATE);
                if (deadAnchorRef != null) {
                    safeAnchorUpdate(viewerEntry.getValue(), deadAnchorRef, EMPTY_UPDATE);
                }
            }
            commandBuffer.removeComponent(entityRef, nameplateDataType);
            return;
        }

        // OPT-3: Cached archetype-based entity type resolution (no reflection after first call)
        String entityTypeId = resolveEntityTypeId(chunk);
        String namespace = AdminConfigStore.extractNamespace(entityTypeId);
        if ("*".equals(namespace) || namespace.isEmpty()) {
            namespace = "hytale";
        }
        boolean isPlayer = store.getComponent(entityRef, playerType) != null;

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
        if (!isPlayer) {
            NPCEntity blacklistNpc = store.getComponent(entityRef, npcEntityType);
            if (blacklistNpc != null) {
                String roleName = blacklistNpc.getRoleName();
                if (roleName != null && adminConfig.isNpcBlacklisted(roleName)) {
                    sendEmptyToAll(visible, entityRef);
                    return;
                }
            }
        }

        // OPT-4: Reuse entityWorld resolved at the top
        if (entityWorld != null) {
            String worldName = entityWorld.getName();
            if (!adminConfig.isWorldEnabled(worldName)) {
                sendEmptyToAll(visible, entityRef);
                return;
            }
        }

        NameplateData entityData = store.getComponent(entityRef, nameplateDataType);
        if (entityData == null) {
            if (anchorManager.hasAnchor(entityRef)) {
                // OPT-4: Reuse entityWorld
                if (entityWorld != null) {
                    anchorManager.removeAnchor(entityRef, entityWorld);
                }
            }
            return;
        }

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();

        if (!segments.isEmpty() && segments.keySet().stream().allMatch(adminConfig::isDisabled)) {
            Ref<EntityStore> disAnchorRef = anchorManager.getAnchorRef(entityRef);
            for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
                viewerEntry.getValue().queueUpdate(entityRef, EMPTY_UPDATE);
                if (disAnchorRef != null) {
                    safeAnchorUpdate(viewerEntry.getValue(), disAnchorRef, EMPTY_UPDATE);
                }
            }
            return;
        }

        // OPT-5: Namespace filtering folded into resolveAvailableKeys - no copy + second pass needed
        List<SegmentKey> available = resolveAvailableKeys(entityData, segments, store, entityRef, chunk);

        for (SegmentKey key : available) {
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

        // OPT-13: Cached comparator - only rebuild when segments change
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
            // OPT-4: Reuse entityWorld
            if (entityWorld != null) {
                anchorManager.ensureAnchor(entityRef, realPosition, maxOffset,
                        entityWorld, store, transformComponentType, commandBuffer);
            }
        } else if (anchorManager.hasAnchor(entityRef)) {
            // OPT-4: Reuse entityWorld
            if (entityWorld != null) {
                anchorManager.removeAnchor(entityRef, entityWorld);
            }
        }

        Ref<EntityStore> anchorRef = anchorManager.getAnchorRef(entityRef);

        // OPT-2: Pre-compute locked chain text once per entity instead of per viewer
        boolean locked = isPlayer ? adminConfig.isPlayerChainLocked() : adminConfig.isNpcChainLocked();
        String lockedText = null;
        ComponentUpdate lockedTextUpdate = null;
        ComponentUpdate lockedEmptyUpdate = null;
        if (locked && !available.isEmpty()) {
            List<SegmentKey> lockedChain = preferences.getChain(ADMIN_CHAIN_UUID, preferenceEntityType, available, defaultComparator);
            lockedText = buildText(lockedChain, entityData, ADMIN_CHAIN_UUID, preferenceEntityType, store, entityRef, segments);
            if (lockedText.isEmpty()) {
                lockedText = NO_DATA_HINT;
                lockedTextUpdate = NO_DATA_UPDATE;
            } else {
                lockedTextUpdate = nameplateUpdate(lockedText);
            }
            lockedEmptyUpdate = EMPTY_UPDATE;
        }

        String viewerChainType = isPlayer ? "_players" : "_npcs";

        viewerIndex = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = viewerUuids[viewerIndex];
            boolean wantsAnchor = viewerWantsAnchor[viewerIndex];
            viewerIndex++;

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

            // OPT-11: Inline look-at math without Vector3d allocation
            if (preferences.isOnlyShowWhenLooking(viewerUuid, "*")
                    && !isLookingAt(store, viewerRef, entityRef)) {
                viewer.queueUpdate(entityRef, EMPTY_UPDATE);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, EMPTY_UPDATE);
                }
                continue;
            }

            // OPT-2: Reuse pre-computed locked text
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
                    textUpdate = NO_DATA_UPDATE;
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
    }

    // OPT-13: Build comparator once, cache until segments change
    private Comparator<SegmentKey> getOrBuildComparator(Map<SegmentKey, NameplateRegistry.Segment> segments) {
        int currentVersion = segments.hashCode();
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
            BoundingBox boundingBox = store.getComponent(entityRef, boundingBoxType);
            if (boundingBox == null) return 0.0;
            Box box = boundingBox.getBoundingBox();
            if (box == null) return 0.0;
            double height = box.max.getY();
            return Math.max(0.0, height);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    // OPT-5: Namespace filtering folded in - no separate copy + removeIf pass needed
    private List<SegmentKey> resolveAvailableKeys(NameplateData entityData,
                                                   Map<SegmentKey, NameplateRegistry.Segment> segments,
                                                   Store<EntityStore> store,
                                                   Ref<EntityStore> entityRef,
                                                   ArchetypeChunk<EntityStore> chunk) {
        Set<SegmentKey> keySet = new LinkedHashSet<>();

        for (String entryKey : entityData.getEntries().keySet()) {
            if (entryKey.startsWith("_")) continue;
            if (entryKey.contains(".")) continue;
            SegmentKey matched = findSegmentKey(entryKey, segments);
            if (matched != null) {
                if (!adminConfig.isDisabled(matched) && isNamespaceEnabledForSegment(matched, segments)) {
                    keySet.add(matched);
                }
            } else {
                keySet.add(new SegmentKey("_unknown", entryKey));
            }
        }

        Archetype<EntityStore> archetype = chunk.getArchetype();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            NameplateRegistry.Segment segment = entry.getValue();
            if (segment.resolver() == null) continue;
            if (adminConfig.isDisabled(entry.getKey())) continue;
            if (keySet.contains(entry.getKey())) continue;
            if (!isNamespaceEnabledForSegment(entry.getKey(), segments)) continue;

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

            keySet.add(entry.getKey());
        }

        return new ArrayList<>(keySet);
    }

    // OPT-5: Extracted namespace check to avoid duplicating logic
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

    // OPT-12: Prefix/suffix appended directly to StringBuilder instead of intermediate string
    private String buildText(List<SegmentKey> ordered,
                             NameplateData entityData,
                             UUID viewerUuid,
                             String entityTypeId,
                             Store<EntityStore> store,
                             Ref<EntityStore> entityRef,
                             Map<SegmentKey, NameplateRegistry.Segment> segments) {
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
                NameplateRegistry.Segment segment = segments.get(key);
                if (segment != null && segment.resolver() != null) {
                    try {
                        text = segment.resolver().resolve(store, entityRef, variantIndex);
                    } catch (Throwable ignored) {
                        text = null;
                    }
                }
            }

            if (text == null || text.isBlank()) {
                continue;
            }

            NameplateRegistry.Segment segmentDefinition = segments.get(key);
            if (segmentDefinition != null && segmentDefinition.supportsPrefixSuffix() && variantIndex > 0) {
                String barEmpty = preferences.getBarEmptyChar(viewerUuid, entityTypeId, key);
                if (barEmpty.length() == 1) {
                    text = text.replace('.', barEmpty.charAt(0));
                }
            }

            if (!builder.isEmpty() && prevKey != null) {
                builder.append(preferences.getSeparatorAfter(viewerUuid, entityTypeId, prevKey));
            }

            // OPT-12: Append prefix/suffix directly to builder
            String prefix = preferences.getPrefix(viewerUuid, entityTypeId, key);
            String suffix = preferences.getSuffix(viewerUuid, entityTypeId, key);
            if (!prefix.isEmpty()) {
                builder.append(prefix);
            }
            builder.append(text);
            if (!suffix.isEmpty()) {
                builder.append(suffix);
            }

            prevKey = key;
        }
        return builder.toString();
    }

    // OPT-11: Inline dot product math without Vector3d allocation
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

        double toX = entityPos.getX() - viewerPos.getX();
        double toY = entityPos.getY() - viewerPos.getY();
        double toZ = entityPos.getZ() - viewerPos.getZ();
        double distanceSq = toX * toX + toY * toY + toZ * toZ;

        if (distanceSq > VIEW_RANGE * VIEW_RANGE || distanceSq < 0.25) {
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

        // dot/distance >= threshold  is equivalent to  dot >= threshold * distance
        // Avoids division; multiply threshold by distance instead
        double dot = lookDir.getX() * toX + lookDir.getY() * toY + lookDir.getZ() * toZ;
        return dot >= VIEW_CONE_THRESHOLD * Math.sqrt(distanceSq);
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

    // OPT-1: Uses cached EMPTY_UPDATE instead of allocating
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

    // OPT-6: Return cached UUID instead of allocating new one
    private UUID getUuid(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        UUIDComponent uuidComponent = store.getComponent(entityRef, uuidComponentType);
        if (uuidComponent != null) {
            return uuidComponent.getUuid();
        }
        return ADMIN_CHAIN_UUID;
    }

    // OPT-3: Cache entity type ID per archetype - reflection only happens once per type
    private String resolveEntityTypeId(ArchetypeChunk<EntityStore> chunk) {
        Archetype<EntityStore> archetype = chunk.getArchetype();
        String cached = entityTypeIdCache.get(archetype);
        if (cached != null) {
            return cached;
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
        return "hytale";
    }
}
