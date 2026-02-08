package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateContext;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NameplateAggregatorSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleComponentType;
    private final ComponentType<EntityStore, Nameplate> nameplateComponentType;
    private final ComponentType<EntityStore, UUIDComponent> uuidComponentType;
    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;

    NameplateAggregatorSystem(NameplateRegistry registry, NameplatePreferenceStore preferences) {
        this.visibleComponentType = EntityTrackerSystems.Visible.getComponentType();
        this.nameplateComponentType = Nameplate.getComponentType();
        this.uuidComponentType = UUIDComponent.getComponentType();
        this.registry = registry;
        this.preferences = preferences;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(visibleComponentType, nameplateComponentType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        EntityTrackerSystems.Visible visible = chunk.getComponent(index, visibleComponentType);
        Nameplate nameplate = chunk.getComponent(index, nameplateComponentType);
        if (visible == null || nameplate == null || visible.visibleTo == null) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        UUID entityUuid = getUuid(store, entityRef);
        String entityTypeId = resolveEntityTypeId(chunk);

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return;
        }

        List<SegmentKey> available = new ArrayList<>(segments.keySet());
        Comparator<SegmentKey> defaultComparator = Comparator
                .comparing((SegmentKey k) -> segments.get(k).getPluginName())
                .thenComparing(k -> segments.get(k).getDisplayName());

        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> viewerEntry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewerRef = viewerEntry.getKey();
            EntityTrackerSystems.EntityViewer viewer = viewerEntry.getValue();
            UUID viewerUuid = getUuid(store, viewerRef);

            String preferenceEntityType = entityTypeId;
            if (preferences.isUsingGlobal(viewerUuid, entityTypeId)) {
                preferenceEntityType = "*";
            }
            List<SegmentKey> chain = preferences.getChain(viewerUuid, preferenceEntityType, available, defaultComparator);
            String text = buildText(chain, segments, entityUuid, preferenceEntityType, viewerUuid);

            // No segments produced text for this entity — leave its nameplate untouched.
            // This prevents overwriting nameplates managed by other mods (holograms, signs, etc.).
            if (text.isEmpty()) {
                continue;
            }

            ComponentUpdate update = new ComponentUpdate();
            update.type = ComponentUpdateType.Nameplate;
            update.nameplate = new com.hypixel.hytale.protocol.Nameplate(text);
            viewer.queueUpdate(entityRef, update);
        }
    }

    private String buildText(List<SegmentKey> ordered,
                             Map<SegmentKey, NameplateRegistry.Segment> segments,
                             UUID entityUuid,
                             String entityTypeId,
                             UUID viewerUuid) {
        StringBuilder builder = new StringBuilder();
        for (SegmentKey key : ordered) {
            if (!preferences.isEnabled(viewerUuid, entityTypeId, key)) {
                continue;
            }
            NameplateRegistry.Segment segment = segments.get(key);
            if (segment == null) {
                continue;
            }
            String text = null;
            if (segment.getProvider() != null) {
                text = segment.getProvider().getText(new NameplateContext(entityUuid, entityTypeId, viewerUuid));
            }
            if (text == null || text.isBlank()) {
                text = segment.getEntityText().get(entityUuid);
            }
            if (text == null || text.isBlank()) {
                text = segment.getGlobalText();
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text);
        }
        return builder.toString();
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
