package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.jspecify.annotations.NonNull;

final class DefaultSegmentSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static boolean debugEnabled = false;

    static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    static boolean isDebugEnabled() {
        return debugEnabled;
    }

    static final String SEGMENT_ENTITY_NAME = "entity-name";
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
    private final ComponentType<EntityStore, NPCEntity> npcEntityType;
    private final AdminConfigStore adminConfig;

    DefaultSegmentSystem(ComponentType<EntityStore, NameplateData> nameplateDataType,
                         AdminConfigStore adminConfig) {
        this.visibleType = EntityTrackerSystems.Visible.getComponentType();
        this.nameplateDataType = nameplateDataType;
        this.playerType = Player.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
        this.npcEntityType = NPCEntity.getComponentType();
        this.adminConfig = adminConfig;
    }

    @Override
    public Archetype<EntityStore> getQuery() {
        return Archetype.of(visibleType);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Override
    public void tick(float dt, int index, @NonNull ArchetypeChunk<EntityStore> chunk,
                     @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {

        if (!adminConfig.isMasterEnabled()) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        Player player = store.getComponent(entityRef, playerType);
        NameplateData existing = store.getComponent(entityRef, nameplateDataType);


        if (player != null && existing == null) {
            if (!adminConfig.isPlayerChainEnabled()) {
                return;
            }
            NameplateData data = new NameplateData();
            seedPlayerDefaults(data, player, store, entityRef);
            commandBuffer.putComponent(entityRef, nameplateDataType, data);
            return;
        }


        if (player == null && existing == null) {
            if (!adminConfig.isNpcChainEnabled()) {
                if (debugEnabled) LOGGER.atInfo().log("[Seed] Skipped NPC - npcChainEnabled=false");
                return;
            }

            NPCEntity npcEntity = store.getComponent(entityRef, npcEntityType);

            if (npcEntity != null) {
                String roleName = npcEntity.getRoleName();
                if (roleName != null && adminConfig.isNpcBlacklisted(roleName)) {
                    if (debugEnabled) LOGGER.atInfo().log("[Seed] Skipped NPC - blacklisted: %s", roleName);
                    return;
                }
            }

            EntityStatMap statMap = store.getComponent(entityRef, statMapType);

            if (debugEnabled) {
                String roleName = npcEntity != null ? npcEntity.getRoleName() : "null";
                boolean hasStatMap = statMap != null;
                boolean hasHealth = hasStatMap && statMap.get(DefaultEntityStatTypes.getHealth()) != null;
                LOGGER.atInfo().log("[Seed] NPC: roleName=%s hasStatMap=%s health=%s hasNPCEntity=%s",
                        roleName, hasStatMap, hasHealth, npcEntity != null);
            }

            if (statMap != null) {
                boolean hasStats = statMap.get(DefaultEntityStatTypes.getHealth()) != null
                        || statMap.get(DefaultEntityStatTypes.getStamina()) != null
                        || statMap.get(DefaultEntityStatTypes.getMana()) != null;
                if (hasStats) {
                    NameplateData data = new NameplateData();
                    String entityName = null;
                    if (npcEntity != null) {
                        String roleName = npcEntity.getRoleName();
                        if (roleName != null && !roleName.isBlank()) {
                            entityName = roleName.replace('_', ' ');
                        }
                    }
                    if (entityName != null && !entityName.isBlank()) {
                        data.setText(SEGMENT_ENTITY_NAME, entityName);
                        adminConfig.trackNamespaceSegment("hytale", SEGMENT_ENTITY_NAME, true);
                    }
                    setHealthText(data, store, entityRef);
                    setStaminaText(data, store, entityRef);
                    setManaText(data, store, entityRef);
                    adminConfig.trackNamespaceSegment("hytale", SEGMENT_HEALTH, true);
                    adminConfig.trackNamespaceSegment("hytale", SEGMENT_STAMINA, true);
                    adminConfig.trackNamespaceSegment("hytale", SEGMENT_MANA, true);
                    commandBuffer.putComponent(entityRef, nameplateDataType, data);
                    if (debugEnabled) LOGGER.atInfo().log("[Seed] SEEDED NPC with stats: name=%s", entityName);
                    return;
                }
            }
        }


        if (existing != null) {
            updateBuiltInSegments(existing, player, store, entityRef);
        }
    }

    private void seedPlayerDefaults(NameplateData data, Player player,
                                    Store<EntityStore> store, Ref<EntityStore> entityRef) {

        String displayName = player.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            data.setText(SEGMENT_PLAYER_NAME, displayName);
        }
        data.setText(SEGMENT_PLAYER_NAME_ANON, "Player");


        setHealthText(data, store, entityRef);


        setStaminaText(data, store, entityRef);


        setManaText(data, store, entityRef);
    }

    private void updateBuiltInSegments(NameplateData data, Player player,
                                       Store<EntityStore> store, Ref<EntityStore> entityRef) {

        if (player == null) {
            String existingName = data.getText(SEGMENT_ENTITY_NAME);
            if (existingName == null || existingName.isBlank()) {
                NPCEntity npcEntity = store.getComponent(entityRef, npcEntityType);
                if (npcEntity != null) {
                    String roleName = npcEntity.getRoleName();
                    if (roleName != null && !roleName.isBlank()) {
                        data.setText(SEGMENT_ENTITY_NAME, roleName.replace('_', ' '));
                    }
                }
            }
        }

        if (player != null) {
            String displayName = player.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                data.setText(SEGMENT_PLAYER_NAME, displayName);
            }
            data.setText(SEGMENT_PLAYER_NAME_ANON, "Player");
        }


        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap != null) {

            if (data.getText(SEGMENT_HEALTH) != null) {
                setHealthText(data, store, entityRef);
            } else if (statMap.get(DefaultEntityStatTypes.getHealth()) != null) {
                setHealthText(data, store, entityRef);
            }

            if (data.getText(SEGMENT_STAMINA) != null) {
                setStaminaText(data, store, entityRef);
            } else if (statMap.get(DefaultEntityStatTypes.getStamina()) != null) {
                setStaminaText(data, store, entityRef);
            }

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


        data.setText(SEGMENT_HEALTH, current + "/" + max);


        int pct = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(SEGMENT_HEALTH_PCT, pct + "%");


        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        String bar = "|".repeat(filled) + ".".repeat(BAR_LENGTH - filled);
        data.setText(SEGMENT_HEALTH_BAR, bar);
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
        String bar = "|".repeat(filled) + ".".repeat(BAR_LENGTH - filled);
        data.setText(SEGMENT_STAMINA_BAR, bar);
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
        String bar = "|".repeat(filled) + ".".repeat(BAR_LENGTH - filled);
        data.setText(SEGMENT_MANA_BAR, bar);
    }
}
