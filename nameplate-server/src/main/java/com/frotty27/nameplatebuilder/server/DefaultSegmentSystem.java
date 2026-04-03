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

    // OPT-7: Pre-computed bar strings - zero allocation at runtime
    private static final String[] BAR_STRINGS = new String[BAR_LENGTH + 1];
    static {
        for (int i = 0; i <= BAR_LENGTH; i++) {
            BAR_STRINGS[i] = "|".repeat(i) + ".".repeat(BAR_LENGTH - i);
        }
    }

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
    public void tick(float deltaTime, int index, @NonNull ArchetypeChunk<EntityStore> chunk,
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
            // OPT-9: Fetch statMap once, pass to all stat methods
            EntityStatMap statMap = store.getComponent(entityRef, statMapType);
            seedPlayerDefaults(data, player, statMap);
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

            if (debugEnabled && npcEntity != null) {
                String roleName = npcEntity.getRoleName();
                String npcTypeId = npcEntity.getNPCTypeId();
                int roleIndex = npcEntity.getRoleIndex();
                boolean hasStatMap = statMap != null;
                boolean hasHealth = hasStatMap && statMap.get(DefaultEntityStatTypes.getHealth()) != null;

                String assetOwner = "unknown";
                try {
                    var builderInfo = com.hypixel.hytale.server.npc.NPCPlugin.get().getRoleBuilderInfo(roleIndex);
                    if (builderInfo != null) {
                        assetOwner = "path=" + builderInfo.getPath() + " key=" + builderInfo.getKeyName();
                    }
                } catch (Throwable t) {
                    assetOwner = "error: " + t.getMessage();
                }

                String pluginGroups;
                try {
                    var plugins = com.hypixel.hytale.server.core.plugin.PluginManager.get().getPlugins();
                    StringBuilder groups = new StringBuilder();
                    for (var plugin : plugins) {
                        if (!groups.isEmpty()) groups.append(", ");
                        groups.append(plugin.getIdentifier().getGroup()).append(":").append(plugin.getIdentifier().getName());
                    }
                    pluginGroups = groups.toString();
                } catch (Throwable t) {
                    pluginGroups = "error: " + t.getMessage();
                }

                LOGGER.atInfo().log("[Seed] NPC: roleName=%s npcTypeId=%s roleIndex=%d hasStatMap=%s health=%s assetOwner=[%s] loadedPlugins=[%s]",
                        roleName, npcTypeId, roleIndex, hasStatMap, hasHealth, assetOwner, pluginGroups);
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
                    // OPT-9: Pass statMap directly instead of re-fetching 3x
                    setStatText(data, statMap, DefaultEntityStatTypes.getHealth(),
                            SEGMENT_HEALTH, SEGMENT_HEALTH_PCT, SEGMENT_HEALTH_BAR);
                    setStatText(data, statMap, DefaultEntityStatTypes.getStamina(),
                            SEGMENT_STAMINA, SEGMENT_STAMINA_PCT, SEGMENT_STAMINA_BAR);
                    setStatText(data, statMap, DefaultEntityStatTypes.getMana(),
                            SEGMENT_MANA, SEGMENT_MANA_PCT, SEGMENT_MANA_BAR);
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
            // OPT-9: Fetch statMap once, pass to updateBuiltInSegments
            EntityStatMap statMap = store.getComponent(entityRef, statMapType);
            updateBuiltInSegments(existing, player, store, entityRef, statMap);
        }
    }

    // OPT-9: statMap passed in instead of re-fetching
    private void seedPlayerDefaults(NameplateData data, Player player, EntityStatMap statMap) {
        String displayName = player.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            data.setText(SEGMENT_PLAYER_NAME, displayName);
        }
        data.setText(SEGMENT_PLAYER_NAME_ANON, "Player");

        if (statMap != null) {
            setStatText(data, statMap, DefaultEntityStatTypes.getHealth(),
                    SEGMENT_HEALTH, SEGMENT_HEALTH_PCT, SEGMENT_HEALTH_BAR);
            setStatText(data, statMap, DefaultEntityStatTypes.getStamina(),
                    SEGMENT_STAMINA, SEGMENT_STAMINA_PCT, SEGMENT_STAMINA_BAR);
            setStatText(data, statMap, DefaultEntityStatTypes.getMana(),
                    SEGMENT_MANA, SEGMENT_MANA_PCT, SEGMENT_MANA_BAR);
        }
    }

    // OPT-9: statMap passed in instead of re-fetching
    private void updateBuiltInSegments(NameplateData data, Player player,
                                       Store<EntityStore> store, Ref<EntityStore> entityRef,
                                       EntityStatMap statMap) {
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

        // OPT-8 + OPT-9: Use passed statMap, skip unchanged stats
        if (statMap != null) {
            if (data.getText(SEGMENT_HEALTH) != null || statMap.get(DefaultEntityStatTypes.getHealth()) != null) {
                setStatTextIfChanged(data, statMap, DefaultEntityStatTypes.getHealth(),
                        SEGMENT_HEALTH, SEGMENT_HEALTH_PCT, SEGMENT_HEALTH_BAR);
            }
            if (data.getText(SEGMENT_STAMINA) != null || statMap.get(DefaultEntityStatTypes.getStamina()) != null) {
                setStatTextIfChanged(data, statMap, DefaultEntityStatTypes.getStamina(),
                        SEGMENT_STAMINA, SEGMENT_STAMINA_PCT, SEGMENT_STAMINA_BAR);
            }
            if (data.getText(SEGMENT_MANA) != null || statMap.get(DefaultEntityStatTypes.getMana()) != null) {
                setStatTextIfChanged(data, statMap, DefaultEntityStatTypes.getMana(),
                        SEGMENT_MANA, SEGMENT_MANA_PCT, SEGMENT_MANA_BAR);
            }
        }
    }

    // OPT-7 + OPT-9: Pre-computed bars, statMap passed in
    private void setStatText(NameplateData data, EntityStatMap statMap, int statId,
                             String baseKey, String percentKey, String barKey) {
        EntityStatValue stat = statMap.get(statId);
        if (stat == null) return;

        int current = Math.round(stat.get());
        int max = Math.round(stat.getMax());

        data.setText(baseKey, current + "/" + max);

        int percent = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(percentKey, percent + "%");

        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        data.setText(barKey, BAR_STRINGS[filled]);
    }

    // OPT-8: Skip unchanged stats - compare current/max against last written values
    private void setStatTextIfChanged(NameplateData data, EntityStatMap statMap, int statId,
                                      String baseKey, String percentKey, String barKey) {
        EntityStatValue stat = statMap.get(statId);
        if (stat == null) return;

        int current = Math.round(stat.get());
        int max = Math.round(stat.getMax());

        // Check if values changed since last write using hidden metadata key
        String lastKey = "_last_" + baseKey;
        String newHash = current + ":" + max;
        String lastHash = data.getText(lastKey);
        if (newHash.equals(lastHash)) {
            return; // unchanged, skip all text updates
        }

        // Values changed - update everything
        data.setText(lastKey, newHash);
        data.setText(baseKey, current + "/" + max);

        int percent = max > 0 ? Math.round(100f * current / max) : 0;
        data.setText(percentKey, percent + "%");

        int filled = max > 0 ? Math.round((float) BAR_LENGTH * current / max) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        data.setText(barKey, BAR_STRINGS[filled]);
    }
}
