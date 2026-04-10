package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.*;

public final class NameplateBuilderPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private NameplateRegistry registry;
    private NameplatePreferenceStore preferences;
    private AdminConfigStore adminConfig;
    private AnchorEntityManager anchorManager;

    private final List<String> discoveredWorldNames = new ArrayList<>();
    private final List<String> discoveredInstanceNames = new ArrayList<>();

    public NameplateBuilderPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        String version = getManifest().getVersion().toString();
        AdminConfigStore.setPluginVersion(version);
        NameplatePreferenceStore.setPluginVersion(version);

        registry = new NameplateRegistry();
        preferences = new NameplatePreferenceStore(getDataDirectory());
        preferences.setRegistry(registry);
        preferences.load();
        adminConfig = new AdminConfigStore(getDataDirectory().resolve("config.json"));
        adminConfig.load();

        NameplateAPI.setRegistry(registry);
        anchorManager = new AnchorEntityManager();


        ComponentType<EntityStore, NameplateData> nameplateDataType =
                getEntityStoreRegistry().registerComponent(NameplateData.class, "nameplate_data", NameplateData.CODEC);
        NameplateAPI.setComponentType(nameplateDataType);


        String pluginId = NameplateRegistry.toPluginId(this);
        ComponentType<EntityStore, NPCEntity> npcEntityType = NPCEntity.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        ComponentType<EntityStore, EntityStatMap> statMapType = EntityStatMap.getComponentType();

        registry.defineBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_ENTITY_NAME,
                "Entity Name", SegmentTarget.NPCS, "Archaeopteryx")
                .overridable()
                .requires(npcEntityType)
                .resolver((store, entityRef, _) -> {
                    NPCEntity npcEntity = store.getComponent(entityRef, npcEntityType);
                    if (npcEntity == null) return null;
                    String roleName = npcEntity.getRoleName();
                    if (roleName == null || roleName.isBlank()) return null;
                    return cleanRoleName(roleName);
                });

        registry.defineBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_PLAYER_NAME,
                "Player Name", SegmentTarget.PLAYERS, "Frotty27")
                .overridable()
                .requires(playerType)
                .resolver((store, entityRef, variantIndex) -> {
                    Player player = store.getComponent(entityRef, playerType);
                    if (player == null) return null;
                    return variantIndex == 1 ? "Player" : player.getDisplayName();
                });
        registry.defineVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_PLAYER_NAME,
                List.of("Real Name", "Anonymized (Player)"));

        registry.defineBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH,
                "Health", SegmentTarget.ALL, "67/69")
                .requires(statMapType)
                .resolver((store, entityRef, variantIndex) -> {
                    EntityStatMap statMap = store.getComponent(entityRef, statMapType);
                    return statMap != null ? formatStat(statMap.get(DefaultEntityStatTypes.getHealth()), variantIndex) : null;
                });
        registry.defineVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH,
                List.of("Current/Max (67/69)", "Percentage (69%)", "Bar (||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH);

        registry.defineBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA,
                "Stamina", SegmentTarget.ALL, "42/100")
                .requires(statMapType)
                .resolver((store, entityRef, variantIndex) -> {
                    EntityStatMap statMap = store.getComponent(entityRef, statMapType);
                    return statMap != null ? formatStat(statMap.get(DefaultEntityStatTypes.getStamina()), variantIndex) : null;
                });
        registry.defineVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA,
                List.of("Current/Max (42/100)", "Percentage (42%)", "Bar (||||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA);

        registry.defineBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_MANA,
                "Mana", SegmentTarget.ALL, "30/50")
                .requires(statMapType)
                .resolver((store, entityRef, variantIndex) -> {
                    EntityStatMap statMap = store.getComponent(entityRef, statMapType);
                    return statMap != null ? formatStat(statMap.get(DefaultEntityStatTypes.getMana()), variantIndex) : null;
                });
        registry.defineVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_MANA,
                List.of("Current/Max (30/50)", "Percentage (60%)", "Bar (||||||||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_MANA);


        adminConfig.prePopulateProfiles(registry.getSegments());
        preferences.setRegistry(registry);

        var entitySourceService = new EntitySourceService();

        getEntityStoreRegistry().registerSystem(new DefaultSegmentSystem(nameplateDataType, adminConfig, entitySourceService, registry));
        getEntityStoreRegistry().registerSystem(new NameplateAggregatorSystem(registry, preferences, adminConfig, nameplateDataType, anchorManager, entitySourceService));
        getCommandRegistry().registerCommand(new NameplateBuilderCommand(registry, preferences, adminConfig, this));
        getCommandRegistry().registerCommand(new NameplateDebugCommand());
        getCommandRegistry().registerCommand(new NameplateBenchmarkCommand(registry, preferences, adminConfig));
        getCommandRegistry().registerCommand(new NameplateIdentifyCommand(registry));


        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                var player = event.getPlayer();
                @SuppressWarnings("removal")
                UUID playerUuid = player.getPlayerRef().getUuid();

                if (!adminConfig.isWelcomeMessagesEnabled()) return;
                if (!preferences.isShowWelcomeMessage(playerUuid)) return;

                boolean allDisabled = !registry.getSegments().isEmpty()
                        && registry.getSegments().keySet().stream().allMatch(adminConfig::isDisabled);

                String serverName = adminConfig.getDisplayServerName();
                if (allDisabled) {
                    player.sendMessage(Message.raw("[" + serverName + "] - Nameplates are disabled on this server.")
                            .color("#FF5555"));
                } else {
                    player.sendMessage(Message.raw("[" + serverName + "] - Use /npb to customize your nameplates.")
                            .color("#55FF55"));
                }
            } catch (RuntimeException ignored) {
            }
        });

        discoverWorlds();

        LOGGER.atInfo().log("NameplateBuilder loaded. Registry ready for mod integrations.");
    }

    @Override
    protected void shutdown() {
        if (registry != null) {
            registry.clear();
        }
        if (anchorManager != null) {
            anchorManager.clear();
        }
    }

    void discoverWorlds() {
        try {
            var worlds = Universe.get().getWorlds();
            discoveredWorldNames.clear();
            discoveredWorldNames.addAll(worlds.keySet());
            discoveredWorldNames.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Throwable _) {
        }
        try {
            var instanceAssets = InstancesPlugin.get().getInstanceAssets();
            discoveredInstanceNames.clear();
            discoveredInstanceNames.addAll(instanceAssets);
            discoveredInstanceNames.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Throwable _) {
        }
    }

    List<String> getDiscoveredWorldNames() {
        return Collections.unmodifiableList(discoveredWorldNames);
    }

    List<String> getDiscoveredInstanceNames() {
        return Collections.unmodifiableList(discoveredInstanceNames);
    }

    private static final int BAR_LENGTH = 20;

    private static final Set<String> ROLE_NOISE = Set.of("patrol", "wander", "guard", "idle", "big", "small");

    private static String cleanRoleName(String roleName) {
        String[] parts = roleName.split("_");
        int end = parts.length;
        while (end > 1 && ROLE_NOISE.contains(parts[end - 1].toLowerCase(java.util.Locale.ROOT))) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (!sb.isEmpty()) sb.append(' ');
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? roleName.replace('_', ' ') : sb.toString();
    }

    private static String formatStat(EntityStatValue stat, int variantIndex) {
        if (stat == null) return null;
        int current = Math.round(stat.get());
        int max = Math.round(stat.getMax());
        return switch (variantIndex) {
            case 1 -> (max > 0 ? Math.round(100f * current / max) : 0) + "%";
            case 2 -> {
                int filled = max > 0 ? Math.round((float) current / max * BAR_LENGTH) : 0;
                yield "|".repeat(Math.max(0, filled)) + ".".repeat(Math.max(0, BAR_LENGTH - filled));
            }
            default -> current + "/" + max;
        };
    }
}
