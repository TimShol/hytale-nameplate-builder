package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

/**
 * Server-side entry point for the NameplateBuilder mod.
 *
 * <p>Wires together the core subsystems on startup:</p>
 * <ul>
 *   <li>{@link NameplateRegistry} — segment UI metadata store</li>
 *   <li>{@link NameplatePreferenceStore} — per-player preference persistence</li>
 *   <li>{@link AdminConfigStore} — server-wide required-segment configuration</li>
 *   <li>{@link AnchorEntityManager} — invisible anchor entities for vertical offset</li>
 *   <li>{@link NameplateAggregatorSystem} — per-tick nameplate compositor</li>
 *   <li>{@link NameplateBuilderCommand} — {@code /npb} player command</li>
 * </ul>
 *
 * <p>On shutdown, all persistent stores are saved to disk.</p>
 */
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

        // Register the NameplateData ECS component so mods can attach it to entities
        ComponentType<EntityStore, NameplateData> nameplateDataType =
                getEntityStoreRegistry().registerComponent(NameplateData.class, "nameplate_data", NameplateData.CODEC);
        NameplateAPI.setComponentType(nameplateDataType);

        // Describe built-in segments provided by NameplateBuilder itself
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

        // Register tick systems
        getEntityStoreRegistry().registerSystem(new DefaultSegmentSystem(nameplateDataType));
        getEntityStoreRegistry().registerSystem(new NameplateAggregatorSystem(registry, preferences, adminConfig, nameplateDataType, anchorManager));
        getCommandRegistry().registerCommand(new NameplateBuilderCommand(registry, preferences, adminConfig));

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
