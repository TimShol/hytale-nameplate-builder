package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class NameplateBuilderPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private NameplateRegistry registry;
    private NameplatePreferenceStore preferences;

    public NameplateBuilderPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        registry = new NameplateRegistry();
        preferences = new NameplatePreferenceStore(getDataDirectory().resolve("preferences.txt"));
        preferences.load();

        NameplateAPI.setRegistry(registry);

        getEntityStoreRegistry().registerSystem(new NameplateAggregatorSystem(registry, preferences));
        getCommandRegistry().registerCommand(new NameplateBuilderCommand(registry, preferences));

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
    }
}
