package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Built-in tick system that provides default nameplate segments for all entities.
 *
 * <ul>
 *   <li><b>Player Name</b> — auto-reads the player's display name, with an
 *       anonymize variant ({@code "Player"}) for privacy</li>
 *   <li><b>Health</b> — reads {@link EntityStatMap} health with three format variants:
 *       current/max ({@code "42/67"}, variant 0), percentage ({@code "63%"}, variant 1),
 *       and visual bar ({@code "||||||||||||........"}, variant 2). The bar uses
 *       {@code '.'} as an internal placeholder for empty slots; the aggregator replaces
 *       it with the viewer's configured empty fill character (default {@code '-'}).</li>
 * </ul>
 *
 * <p>For Player entities that don't yet have a {@link NameplateData} component,
 * this system auto-creates one via the {@link CommandBuffer}. NPC entities are
 * left alone — mods must explicitly call {@code NameplateAPI.register()} for NPCs.</p>
 *
 * @see NameplateAggregatorSystem#buildText for variant resolution and bar character replacement
 */
final class DefaultSegmentSystem extends EntityTickingSystem<EntityStore> {

    static final String SEGMENT_PLAYER_NAME = "player-name";
    static final String SEGMENT_PLAYER_NAME_ANON = "player-name.1";
    static final String SEGMENT_HEALTH = "health";
    static final String SEGMENT_HEALTH_PCT = "health.1";
    static final String SEGMENT_HEALTH_BAR = "health.2";

    private static final int BAR_LENGTH = 20;

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleType;
    private final ComponentType<EntityStore, NameplateData> nameplateDataType;
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    DefaultSegmentSystem(ComponentType<EntityStore, NameplateData> nameplateDataType) {
        this.visibleType = EntityTrackerSystems.Visible.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.playerType = Player.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(visibleType);
    }

    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        Player player = store.getComponent(entityRef, playerType);
        NameplateData existing = store.getComponent(entityRef, nameplateDataType);

        // Auto-attach NameplateData to Player entities that don't have it yet
        if (player != null && existing == null) {
            NameplateData data = new NameplateData();
            seedPlayerDefaults(data, player, store, entityRef);
            commandBuffer.putComponent(entityRef, nameplateDataType, data);
            return;
        }

        // Update built-in segments on entities that already have NameplateData
        if (existing != null) {
            updateBuiltInSegments(existing, player, store, entityRef);
        }
    }

    private void seedPlayerDefaults(NameplateData data, Player player,
                                    Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Player Name (variant 0: real name, variant 1: anonymized)
        String displayName = player.getLegacyDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            data.setText(SEGMENT_PLAYER_NAME, displayName);
        }
        data.setText(SEGMENT_PLAYER_NAME_ANON, "Player");

        // Health (variant 0: current/max, variant 1: percentage, variant 2: bar)
        setHealthText(data, store, entityRef);
    }

    private void updateBuiltInSegments(NameplateData data, Player player,
                                       Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Player Name — only on Player entities (variant 0: real, variant 1: anonymized)
        if (player != null) {
            String displayName = player.getLegacyDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                data.setText(SEGMENT_PLAYER_NAME, displayName);
            }
            data.setText(SEGMENT_PLAYER_NAME_ANON, "Player");
        }

        // Health — on any entity that has the health segment already set,
        // or on any entity with an EntityStatMap
        if (data.getText(SEGMENT_HEALTH) != null) {
            setHealthText(data, store, entityRef);
        } else {
            // Check if entity has health stats — if so, initialize the segment
            EntityStatMap statMap = store.getComponent(entityRef, statMapType);
            if (statMap != null) {
                EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                if (health != null) {
                    setHealthText(data, store, entityRef);
                }
            }
        }
    }

    private void setHealthText(NameplateData data, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap == null) {
            return;
        }
        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }

        int current = Math.round(health.get());
        int max = Math.round(health.getMax());

        // Variant 0: current/max (e.g. "42/67")
        data.setText(SEGMENT_HEALTH, current + "/" + max);

        // Variant 1: percentage (e.g. "63%")
        int pct = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(SEGMENT_HEALTH_PCT, pct + "%");

        // Variant 2: visual bar (e.g. "||||||||||||........")
        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        StringBuilder bar = new StringBuilder(BAR_LENGTH);
        for (int i = 0; i < filled; i++) bar.append('|');
        for (int i = filled; i < BAR_LENGTH; i++) bar.append('.');
        data.setText(SEGMENT_HEALTH_BAR, bar.toString());
    }
}
