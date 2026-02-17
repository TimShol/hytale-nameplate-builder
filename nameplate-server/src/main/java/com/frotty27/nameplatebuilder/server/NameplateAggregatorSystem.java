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

/**
 * Per-tick nameplate compositor that produces the final nameplate text for each entity.
 *
 * <p>Queries all entities with both a {@code Visible} and {@link NameplateData}
 * component. For each visible entity, it resolves segment keys from the component,
 * applies the viewer's preferences (ordering, enabled/disabled, separators), enforces
 * admin-required segments, and queues a per-viewer nameplate update via the entity
 * tracker.</p>
 *
 * <p>Also handles:</p>
 * <ul>
 *   <li><b>Death cleanup</b> — blanks nameplates and removes the component on death</li>
 *   <li><b>View-cone filtering</b> — hides nameplates for entities outside the view cone</li>
 *   <li><b>Anchor entity routing</b> — routes text to invisible offset anchors when configured</li>
 *   <li><b>Required segment enforcement</b> — segments marked as required always display</li>
 * </ul>
 *
 * @see NameplateRegistry
 * @see NameplatePreferenceStore
 * @see AdminConfigStore
 * @see AnchorEntityManager
 */
final class NameplateAggregatorSystem extends EntityTickingSystem<EntityStore> {

    private static final double VIEW_RANGE = 30.0;
    private static final double VIEW_CONE_THRESHOLD = 0.9; // ~25° half-angle
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
        // Clean up anchors for entities that were removed from the store
        // (e.g. /npc clean --confirm) — their Ref is no longer valid.
        // Blank their nameplates so clients don't see floating text.
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

        // If the entity is dead, send an empty nameplate to all viewers so the
        // text disappears immediately rather than lingering through the death animation.
        // We also remove the NameplateData component.
        // NOTE: We don't remove anchors during death because it can cause race conditions
        // with chunk serialization. Anchors are cleaned up by cleanupOrphanedAnchors().
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
            // Don't remove anchor here — let cleanupOrphanedAnchors() handle it
            // to avoid chunk serialization race conditions
            commandBuffer.removeComponent(entityRef, nameplateDataType);
            return;
        }

        String entityTypeId = resolveEntityTypeId(chunk);

        // Read the NameplateData component — this is the sole source of text.
        // If the entity has no NameplateData at all, it was never registered
        // with our system — don't interfere with its default nameplate.
        NameplateData entityData = store.getComponent(entityRef, nameplateDataType);
        if (entityData == null) {
            // Clean up any orphaned anchor for an entity that lost its NameplateData
            if (anchorManager.hasAnchor(entityRef)) {
                anchorManager.removeAnchor(entityRef, commandBuffer);
            }
            return;
        }

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();

        // If every segment is admin-disabled, nameplates are effectively off globally —
        // blank all viewers and skip the rest of the processing.
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

        // Get the real entity's position for anchor positioning
        TransformComponent realTransform = store.getComponent(entityRef, transformComponentType);
        Vector3d realPosition = realTransform != null ? realTransform.getPosition() : null;

        // Resolve entity height from bounding box so the anchor is positioned
        // above the model's head rather than at its feet.
        double entityHeight = resolveEntityHeight(store, entityRef);

        // Determine the tab entity type for chain/separator preferences.
        // Player entities use "_players", NPC entities use "_npcs".
        String tabEntityType = resolveTabEntityType(store, entityRef);

        // Check if this is a player entity — players never use anchors because
        // vanilla client-side nameplate rendering is smoother (no server-side lag).
        // NPCs can have varying sizes, so they benefit from offset anchors.
        boolean isPlayer = store.getComponent(entityRef, playerType) != null;

        // ── Pass 1: Compute per-viewer offsets and determine max offset ──

        int viewerCount = visible.visibleTo.size();
        double[] viewerOffsets = new double[viewerCount];   // total offset (entityHeight + userOffset)
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

            // Chain/separator preferences use the tab entity type (_npcs / _players).
            // Global settings (offset, look-toggle) use "*".
            viewerPreferenceTypes[vi] = tabEntityType;

            double userOffset = preferences.getOffset(viewerUuid, "*");
            // Total anchor offset = entity model height + user-configured offset.
            // This positions the nameplate above the entity's head, not its feet.
            double totalOffset = entityHeight + userOffset;
            viewerOffsets[vi] = totalOffset;
            // Players never use anchors — skip even if offset is configured
            viewerWantsAnchor[vi] = !isPlayer && userOffset != 0.0;
            if (!isPlayer && userOffset != 0.0) {
                anyViewerNeedsAnchor = true;
                if (Math.abs(totalOffset) > Math.abs(maxOffset)) {
                    maxOffset = totalOffset;
                }
            }
            vi++;
        }

        // ── Anchor lifecycle management ──
        // Players never spawn anchors — vanilla nameplate rendering is client-side
        // and doesn't lag. Only NPCs use server-side anchor entities for offset.

        if (anyViewerNeedsAnchor && !isPlayer && realPosition != null) {
            World world = resolveWorld(store, entityRef);
            if (world != null) {
                anchorManager.ensureAnchor(entityRef, realPosition, maxOffset,
                        world, store, transformComponentType, commandBuffer);
            }
        } else if (anchorManager.hasAnchor(entityRef)) {
            // Entity became a player, or all viewers switched to offset=0 — despawn anchor
            anchorManager.removeAnchor(entityRef, commandBuffer);
        }

        Ref<EntityStore> anchorRef = anchorManager.getAnchorRef(entityRef);

        // ── Pass 2: Route nameplate text per viewer ──

        vi = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = viewerUuids[vi];
            String preferenceEntityType = viewerPreferenceTypes[vi];
            double viewerOffset = viewerOffsets[vi];
            boolean wantsAnchor = viewerWantsAnchor[vi];
            vi++;

            // Global disable: if this viewer turned off nameplates entirely, blank everything
            if (!preferences.isNameplatesEnabled(viewerUuid)) {
                ComponentUpdate emptyUpdate = nameplateUpdate("");
                viewer.queueUpdate(entityRef, emptyUpdate);
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, emptyUpdate);
                }
                continue;
            }

            // View-cone filter: hide nameplate for entities the viewer isn't looking at.
            // We still send an update (empty string) so the default value doesn't bleed through.
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
                // No mod has set any segment data on this entity, or every
                // registered segment is admin-disabled — show blank so the
                // default entity ID doesn't bleed through.
                text = ALL_HIDDEN_HINT;
            } else {
                List<SegmentKey> chain = preferences.getChain(viewerUuid, preferenceEntityType, available, defaultComparator);
                text = buildText(chain, entityData, viewerUuid, preferenceEntityType);
                if (text.isEmpty()) {
                    // Segments exist but the player's chain is empty (all blocks
                    // removed) — show hint so they know how to add them back.
                    text = NO_DATA_HINT;
                }
            }

            // Per-viewer offset routing
            if (wantsAnchor && anchorRef != null) {
                // Viewer wants offset and anchor is ready:
                // blank the real entity's nameplate, send text to anchor
                viewer.queueUpdate(entityRef, nameplateUpdate(""));
                safeAnchorUpdate(viewer, anchorRef, nameplateUpdate(text));
            } else if (wantsAnchor) {
                // Viewer wants offset but anchor not ready yet (spawn pending or no World):
                // fallback — show text on real entity for one frame
                viewer.queueUpdate(entityRef, nameplateUpdate(text));
            } else {
                // Viewer wants offset=0: text on real entity
                viewer.queueUpdate(entityRef, nameplateUpdate(text));
                // Ensure this viewer sees blank on the anchor (if it exists for other viewers)
                if (anchorRef != null) {
                    safeAnchorUpdate(viewer, anchorRef, nameplateUpdate(""));
                }
            }
        }
    }

    /**
     * Determine the tab entity type for chain/separator preferences.
     * Player entities → {@code "_players"}, everything else → {@code "_npcs"}.
     */
    private String resolveTabEntityType(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Player player = store.getComponent(entityRef, playerType);
        return player != null
                ? NameplateBuilderPage.ENTITY_TYPE_PLAYERS
                : NameplateBuilderPage.ENTITY_TYPE_NPCS;
    }

    // ── World resolution ──

    /**
     * Attempt to resolve the {@link World} for a given entity.
     * Tries {@code NPCEntity.getWorld()} first, then {@code Player.getWorld()}.
     *
     * @return the World, or {@code null} if resolution fails
     */
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

    // ── Entity height resolution ──

    /**
     * Attempt to resolve the entity's model height from its {@link BoundingBox}
     * component. This is used to position the anchor entity at the top of the
     * model (head height) rather than at the entity's feet, so that the offset
     * the user configures is relative to the head position.
     *
     * <p>Returns {@code 0.0} if the bounding box is unavailable — the anchor
     * will fall back to the entity's feet position.</p>
     */
    private double resolveEntityHeight(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            BoundingBox bb = store.getComponent(entityRef, boundingBoxType);
            if (bb == null) return 0.0;
            com.hypixel.hytale.math.shape.Box box = bb.getBoundingBox();
            if (box == null) return 0.0;
            // box.max.getY() gives the upper Y of the bounding box.
            // For entity bounding boxes this is the model height above feet.
            // We also try box.height() as a fallback — it returns maxY - minY.
            double h = box.max.getY();
            return Math.max(0.0, h);
        } catch (Throwable _) {
            // BoundingBox/Box API might differ — graceful fallback
            return 0.0;
        }
    }

    // ── Segment resolution ──

    /**
     * Resolve the available segment keys from the entity's NameplateData entries.
     *
     * <p>For each entry in the component, we try to match it to a described segment
     * in the registry. If the entry key matches a segmentId of a described segment,
     * we use that SegmentKey. Otherwise, we create a synthetic SegmentKey so the
     * segment still shows up (with the raw ID as fallback in the UI).</p>
     */
    private List<SegmentKey> resolveAvailableKeys(NameplateData entityData,
                                                   Map<SegmentKey, NameplateRegistry.Segment> segments) {
        List<SegmentKey> keys = new ArrayList<>();
        for (String entryKey : entityData.getEntries().keySet()) {
            // Skip hidden metadata keys (prefixed with "_")
            if (entryKey.startsWith("_")) {
                continue;
            }
            // Skip variant-suffixed keys (e.g. "health.1", "level.2")
            // These are alternate format texts, not standalone segments
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
                // Undescribed segment — create a synthetic key
                SegmentKey synthetic = new SegmentKey("_unknown", entryKey);
                if (!keys.contains(synthetic)) {
                    keys.add(synthetic);
                }
            }
        }
        return keys;
    }

    /**
     * Find the SegmentKey in the registry that matches a component entry key.
     *
     * <p>When multiple plugins register the same segmentId, this method uses a
     * deterministic tie-breaking strategy: built-in segments win first, then
     * alphabetically earliest pluginId. This prevents non-deterministic behaviour
     * from {@link java.util.concurrent.ConcurrentHashMap} iteration order.</p>
     */
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
            // Built-in segments always win over non-built-in
            if (candidateBuiltIn && !bestBuiltIn) {
                best = entry.getKey();
                bestBuiltIn = true;
            } else if (candidateBuiltIn == bestBuiltIn) {
                // Same tier — alphabetically earliest pluginId wins for determinism
                if (entry.getKey().pluginId().compareTo(best.pluginId()) < 0) {
                    best = entry.getKey();
                }
            }
        }
        return best;
    }

    // ── Text building ──

    /**
     * Build the final nameplate text for one entity as seen by one viewer.
     *
     * <p>For each segment in the viewer's ordered chain, this method:</p>
     * <ol>
     *   <li>Resolves the format variant (suffixed key lookup with fallback to base)</li>
     *   <li>Replaces bar placeholder characters ({@code '.'}) with the viewer's
     *       configured empty fill character for segments that support it</li>
     *   <li>Wraps the text with the viewer's configured prefix and suffix</li>
     *   <li>Joins segments using per-block separators</li>
     * </ol>
     */
    private String buildText(List<SegmentKey> ordered,
                             NameplateData entityData,
                             UUID viewerUuid,
                             String entityTypeId) {
        StringBuilder builder = new StringBuilder();
        SegmentKey prevKey = null;
        for (SegmentKey key : ordered) {
            // Disabled segments are never shown regardless of other settings
            if (adminConfig.isDisabled(key)) {
                continue;
            }
            // Required segments always display, even if the viewer disabled them
            if (!adminConfig.isRequired(key) && !preferences.isEnabled(viewerUuid, entityTypeId, key)) {
                continue;
            }

            // Resolve format variant: check if the viewer selected a non-default variant
            int variantIndex = preferences.getSelectedVariant(viewerUuid, entityTypeId, key);
            String text;
            if (variantIndex > 0) {
                // Try the suffixed variant key first, fall back to base
                String variantText = entityData.getText(key.segmentId() + "." + variantIndex);
                text = variantText != null && !variantText.isBlank() ? variantText : entityData.getText(key.segmentId());
            } else {
                text = entityData.getText(key.segmentId());
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            // Replace bar placeholder '.' with the player's custom empty fill character.
            // Only applies to segments that support bar customization (supportsPrefixSuffix)
            // and only when the bar variant is selected (variantIndex > 0 with suffixed key).
            NameplateRegistry.Segment segDef = registry.getSegments().get(key);
            if (segDef != null && segDef.supportsPrefixSuffix() && variantIndex > 0) {
                String barEmpty = preferences.getBarEmptyChar(viewerUuid, entityTypeId, key);
                if (barEmpty.length() == 1) {
                    text = text.replace('.', barEmpty.charAt(0));
                }
            }
            // Apply prefix/suffix wrapping (e.g. "[" + "42/67" + "]" → "[42/67]")
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

    // ── View-cone filter ──

    /**
     * Check if the viewer is looking at the target entity within a view cone.
     *
     * <p>Uses {@link HeadRotation} for precise head direction when available,
     * otherwise falls back to the body rotation from {@link TransformComponent}.
     * The direction is derived from pitch (x) and yaw (y) Euler angles.</p>
     */
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

        // Prefer HeadRotation (precise head aim), fall back to body rotation
        Vector3d lookDir;
        HeadRotation viewerHead = store.getComponent(viewerRef, headRotationType);
        if (viewerHead != null) {
            lookDir = viewerHead.getDirection();
        } else {
            // Derive direction from TransformComponent rotation (pitch=x, yaw=y)
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

    /**
     * Convert pitch and yaw (in degrees) to a normalized direction vector.
     */
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

    // ── Protocol helpers ──

    /**
     * Build a nameplate {@link ComponentUpdate} for the given text.
     * A single space is used instead of null or empty strings — the Hytale
     * client may not handle those gracefully (C# NullReferenceException).
     */
    private static NameplateUpdate nameplateUpdate(String text) {
        return new NameplateUpdate(text == null || text.isEmpty() ? " " : text);
    }

    /**
     * Safely queue a nameplate update for an anchor entity.
     * The anchor may not be in the viewer's entity tracker (e.g. the viewer
     * noclipped out of range, or the anchor just spawned). In that case
     * {@code queueUpdate} throws {@link IllegalArgumentException} — we
     * silently ignore it because the anchor will either enter the tracker
     * on the next tick or be cleaned up.
     */
    private static void safeAnchorUpdate(EntityTrackerSystems.EntityViewer viewer,
                                         Ref<EntityStore> anchorRef,
                                         ComponentUpdate update) {
        try {
            if (anchorRef == null || !anchorRef.isValid()) {
                return;
            }
            viewer.queueUpdate(anchorRef, update);
        } catch (IllegalArgumentException | IllegalStateException _) {
            // Anchor not visible to this viewer or ref invalidated mid-tick — safe to skip
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
