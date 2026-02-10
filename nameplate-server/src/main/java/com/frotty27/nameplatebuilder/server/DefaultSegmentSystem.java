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
 *       current/max, percentage, and visual bar.</li>
 *   <li><b>Stamina</b> — same three format variants as health, using the stamina stat.</li>
 *   <li><b>Mana</b> — same three format variants as health, using the mana stat.</li>
 * </ul>
 *
 * <p>Bar variants use {@code '.'} as an internal placeholder for empty slots; the
 * aggregator replaces it with the viewer's configured empty fill character
 * (default {@code '-'}).</p>
 *
 * <p>For Player entities that don't yet have a {@link NameplateData} component,
 * this system auto-creates one via the {@link CommandBuffer}. Non-Player entities
 * (NPCs, mobs, etc.) that have an {@link EntityStatMap} with health, stamina, or
 * mana stats are also auto-attached so their stats display on nameplates.</p>
 *
 * @see NameplateAggregatorSystem#buildText for variant resolution and bar character replacement
 */
final class DefaultSegmentSystem extends EntityTickingSystem<EntityStore> {

    static final String SEGMENT_PLAYER_NAME = "player-name";
    static final String SEGMENT_PLAYER_NAME_ANON = "player-name.1";
    static final String SEGMENT_HEALTH = "health";
    static final String SEGMENT_HEALTH_PCT = "health.1";
    static final String SEGMENT_HEALTH_BAR = "health.2";
    static final String SEGMENT_STAMINA = "stamina";
    static final String SEGMENT_STAMINA_PCT = "stamina.1";
    static final String SEGMENT_STAMINA_BAR = "stamina.2";
    static final String SEGMENT_MANA = "mana";
    static final String SEGMENT_MANA_PCT = "mana.1";
    static final String SEGMENT_MANA_BAR = "mana.2";

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

        // Auto-attach NameplateData to non-Player entities (NPCs, mobs) that have
        // stat data but no NameplateData yet — fixes health/stamina/mana not showing
        if (player == null && existing == null) {
            EntityStatMap statMap = store.getComponent(entityRef, statMapType);
            if (statMap != null) {
                boolean hasStats = statMap.get(DefaultEntityStatTypes.getHealth()) != null
                        || statMap.get(DefaultEntityStatTypes.getStamina()) != null
                        || statMap.get(DefaultEntityStatTypes.getMana()) != null;
                if (hasStats) {
                    NameplateData data = new NameplateData();
                    setHealthText(data, store, entityRef);
                    setStaminaText(data, store, entityRef);
                    setManaText(data, store, entityRef);
                    commandBuffer.putComponent(entityRef, nameplateDataType, data);
                    return;
                }
            }
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

        // Stamina (variant 0: current/max, variant 1: percentage, variant 2: bar)
        setStaminaText(data, store, entityRef);

        // Mana (variant 0: current/max, variant 1: percentage, variant 2: bar)
        setManaText(data, store, entityRef);
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

        // Stat segments — update if already initialized, or lazy-init if stat is available
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap != null) {
            // Health
            if (data.getText(SEGMENT_HEALTH) != null) {
                setHealthText(data, store, entityRef);
            } else if (statMap.get(DefaultEntityStatTypes.getHealth()) != null) {
                setHealthText(data, store, entityRef);
            }
            // Stamina
            if (data.getText(SEGMENT_STAMINA) != null) {
                setStaminaText(data, store, entityRef);
            } else if (statMap.get(DefaultEntityStatTypes.getStamina()) != null) {
                setStaminaText(data, store, entityRef);
            }
            // Mana
            if (data.getText(SEGMENT_MANA) != null) {
                setManaText(data, store, entityRef);
            } else if (statMap.get(DefaultEntityStatTypes.getMana()) != null) {
                setManaText(data, store, entityRef);
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

    private void setStaminaText(NameplateData data, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap == null) {
            return;
        }
        EntityStatValue stamina = statMap.get(DefaultEntityStatTypes.getStamina());
        if (stamina == null) {
            return;
        }

        int current = Math.round(stamina.get());
        int max = Math.round(stamina.getMax());

        data.setText(SEGMENT_STAMINA, current + "/" + max);

        int pct = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(SEGMENT_STAMINA_PCT, pct + "%");

        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        StringBuilder bar = new StringBuilder(BAR_LENGTH);
        for (int i = 0; i < filled; i++) bar.append('|');
        for (int i = filled; i < BAR_LENGTH; i++) bar.append('.');
        data.setText(SEGMENT_STAMINA_BAR, bar.toString());
    }

    private void setManaText(NameplateData data, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap == null) {
            return;
        }
        EntityStatValue mana = statMap.get(DefaultEntityStatTypes.getMana());
        if (mana == null) {
            return;
        }

        int current = Math.round(mana.get());
        int max = Math.round(mana.getMax());

        data.setText(SEGMENT_MANA, current + "/" + max);

        int pct = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(SEGMENT_MANA_PCT, pct + "%");

        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        StringBuilder bar = new StringBuilder(BAR_LENGTH);
        for (int i = 0; i < filled; i++) bar.append('|');
        for (int i = filled; i < BAR_LENGTH; i++) bar.append('.');
        data.setText(SEGMENT_MANA_BAR, bar.toString());
    }

}
