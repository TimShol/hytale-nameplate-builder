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
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NameplateAggregatorSystem extends EntityTickingSystem<EntityStore> {

    private static final double VIEW_RANGE = 30.0;
    private static final double VIEW_CONE_THRESHOLD = 0.9; // ~25° half-angle
    private static final String EMPTY_HINT = "Type /npb to customize";

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleComponentType;
    private final ComponentType<EntityStore, UUIDComponent> uuidComponentType;
    private final ComponentType<EntityStore, TransformComponent> transformComponentType;
    private final ComponentType<EntityStore, HeadRotation> headRotationType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;

    NameplateAggregatorSystem(NameplateRegistry registry,
                              NameplatePreferenceStore preferences,
                              ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.visibleComponentType = EntityTrackerSystems.Visible.getComponentType();
        this.uuidComponentType = UUIDComponent.getComponentType();
        this.transformComponentType = TransformComponent.getComponentType();
        this.headRotationType = HeadRotation.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.registry = registry;
        this.preferences = preferences;
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
        EntityTrackerSystems.Visible visible = chunk.getComponent(index, visibleComponentType);
        if (visible == null || visible.visibleTo == null) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        String entityTypeId = resolveEntityTypeId(chunk);

        // Read the NameplateData component — this is the sole source of text.
        // If the entity has no NameplateData at all, it was never registered
        // with our system — don't interfere with its default nameplate.
        NameplateData entityData = store.getComponent(entityRef, nameplateDataType);
        if (entityData == null) {
            return;
        }

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
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

        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = getUuid(store, viewerRef);

            String preferenceEntityType = entityTypeId;
            if (preferences.isUsingGlobal(viewerUuid, entityTypeId)
                    || !preferences.hasPreferences(viewerUuid, entityTypeId)) {
                preferenceEntityType = "*";
            }

            // View-cone filter: hide nameplate for entities the viewer isn't looking at.
            // We still send an update (empty string) so the default value doesn't bleed through.
            if (preferences.isOnlyShowWhenLooking(viewerUuid, preferenceEntityType)
                    && !isLookingAt(store, viewerRef, entityRef)) {
                viewer.queueUpdate(entityRef, nameplateUpdate(""));
                continue;
            }

            String text;
            if (available.isEmpty()) {
                text = EMPTY_HINT;
            } else {
                List<SegmentKey> chain = preferences.getChain(viewerUuid, preferenceEntityType, available, defaultComparator);
                text = buildText(chain, entityData, viewerUuid, preferenceEntityType);
                if (text.isEmpty()) {
                    text = EMPTY_HINT;
                }
            }

            viewer.queueUpdate(entityRef, nameplateUpdate(text));
        }
    }

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
            SegmentKey matched = findSegmentKey(entryKey, segments);
            if (matched != null) {
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
     * Tries exact segmentId match first.
     */
    private SegmentKey findSegmentKey(String entryKey, Map<SegmentKey, NameplateRegistry.Segment> segments) {
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            if (entry.getKey().segmentId().equals(entryKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Build the final nameplate text for one entity as seen by one viewer.
     * Text comes solely from the entity's {@link NameplateData} component.
     */
    private String buildText(List<SegmentKey> ordered,
                             NameplateData entityData,
                             UUID viewerUuid,
                             String entityTypeId) {
        String separator = preferences.getSeparator(viewerUuid, entityTypeId);
        StringBuilder builder = new StringBuilder();
        for (SegmentKey key : ordered) {
            if (!preferences.isEnabled(viewerUuid, entityTypeId, key)) {
                continue;
            }

            String text = entityData.getText(key.segmentId());
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(separator);
            }
            builder.append(text);
        }
        return builder.toString();
    }

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

    private static ComponentUpdate nameplateUpdate(String text) {
        ComponentUpdate update = new ComponentUpdate();
        update.type = ComponentUpdateType.Nameplate;
        update.nameplate = new com.hypixel.hytale.protocol.Nameplate(text);
        return update;
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
