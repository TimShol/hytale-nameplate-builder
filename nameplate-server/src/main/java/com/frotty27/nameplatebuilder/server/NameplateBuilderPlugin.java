package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

public final class NameplateBuilderPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private NameplateRegistry registry;
    private NameplatePreferenceStore preferences;
    private AdminConfigStore adminConfig;
    private AnchorEntityManager anchorManager;

    public NameplateBuilderPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        registry = new NameplateRegistry();
        preferences = new NameplatePreferenceStore(getDataDirectory().resolve("preferences.txt"));
        preferences.load();
        adminConfig = new AdminConfigStore(getDataDirectory().resolve("admin_config.txt"));
        adminConfig.load();

        NameplateAPI.setRegistry(registry);
        anchorManager = new AnchorEntityManager();


        ComponentType<EntityStore, NameplateData> nameplateDataType =
                getEntityStoreRegistry().registerComponent(NameplateData.class, "nameplate_data", NameplateData.CODEC);
        NameplateAPI.setComponentType(nameplateDataType);


        String pluginId = NameplateRegistry.toPluginId(this);
        registry.describeBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_PLAYER_NAME,
                "Player Name", SegmentTarget.PLAYERS, "Frotty27");
        registry.describeVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_PLAYER_NAME,
                List.of("Real Name", "Anonymized (Player)"));
        registry.describeBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH,
                "Health", SegmentTarget.ALL, "67/69");
        registry.describeVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH,
                List.of("Current/Max (67/69)", "Percentage (69%)", "Bar (||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_HEALTH);
        registry.describeBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA,
                "Stamina", SegmentTarget.ALL, "42/100");
        registry.describeVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA,
                List.of("Current/Max (42/100)", "Percentage (42%)", "Bar (||||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_STAMINA);
        registry.describeBuiltIn(pluginId, DefaultSegmentSystem.SEGMENT_MANA,
                "Mana", SegmentTarget.ALL, "30/50");
        registry.describeVariantsInternal(pluginId, DefaultSegmentSystem.SEGMENT_MANA,
                List.of("Current/Max (30/50)", "Percentage (60%)", "Bar (||||||||||||-----)"));
        registry.setSupportsPrefixSuffix(pluginId, DefaultSegmentSystem.SEGMENT_MANA);


        getEntityStoreRegistry().registerSystem(new DefaultSegmentSystem(nameplateDataType));
        getEntityStoreRegistry().registerSystem(new NameplateAggregatorSystem(registry, preferences, adminConfig, nameplateDataType, anchorManager));
        getCommandRegistry().registerCommand(new NameplateBuilderCommand(registry, preferences, adminConfig));


        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                var player = event.getPlayer();
                UUID playerUuid = player.getPlayerRef().getUuid();
                if (playerUuid == null) return;

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
            } catch (RuntimeException _) {

            }
        });

        LOGGER.atInfo().log("NameplateBuilder loaded. Registry ready for mod integrations.");
    }

    @Override
    protected void shutdown() {
        if (registry != null) {
            registry.clear();
        }
        if (preferences != null) {
            preferences.save();
        }
        if (adminConfig != null) {
            adminConfig.save();
        }
        if (anchorManager != null) {
            anchorManager.clear();
        }
    }
}
