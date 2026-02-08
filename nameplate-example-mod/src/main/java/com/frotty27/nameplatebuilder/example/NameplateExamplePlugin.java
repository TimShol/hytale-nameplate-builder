package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.INameplateSegmentHandle;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Example mod demonstrating the NameplateBuilder API.
 *
 * <p>Segments are automatically cleaned up when the server shuts down,
 * so there is no need to call {@code unregister} in {@code shutdown()}.
 * If you do need to remove a segment at runtime (e.g. a temporary buff),
 * use the {@link INameplateSegmentHandle} returned by {@code register}.</p>
 */
public final class NameplateExamplePlugin extends JavaPlugin {

    private INameplateSegmentHandle buffHandle;

    public NameplateExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Simple static text — no provider or context needed
        NameplateAPI.register(this, "tier", "Elite Tier", "[Elite]");
        NameplateAPI.register(this, "health", "Health Bar", "67/67");

        // Dynamic text — use a provider when the text depends on the entity
        NameplateAPI.register(this, "level", "Level",
                ctx -> "Lvl. " + Math.abs(ctx.entityUuid().hashCode() % 99));

        // Temporary segment — hold the handle so it can be removed later
        buffHandle = NameplateAPI.register(this, "buff", "Active Buff", "[+10% XP]");
    }

    /**
     * Example: call this from a command or event to remove the buff segment at runtime.
     */
    public void removeBuff() {
        if (buffHandle != null) {
            buffHandle.unregister();
            buffHandle = null;
        }
    }
}
