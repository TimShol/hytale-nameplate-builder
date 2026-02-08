package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.INameplateSegmentHandle;
import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example mod demonstrating the NameplateBuilder API.
 *
 * <p>Shows three patterns:</p>
 * <ul>
 *   <li><b>Global static text</b> — applies to every entity (simple)</li>
 *   <li><b>Per-entity filtering</b> — only applies to a subset of entities</li>
 *   <li><b>Runtime removal</b> — using a handle to remove a segment later</li>
 * </ul>
 *
 * <p>Segments are automatically cleaned up on server shutdown.
 * If you need to remove one at runtime, use the {@link INameplateSegmentHandle}.</p>
 */
public final class NameplateExamplePlugin extends JavaPlugin {

    // Track which entities are "elite" — in a real mod this would come from your own component/system
    private final Set<UUID> eliteEntities = ConcurrentHashMap.newKeySet();

    private INameplateSegmentHandle buffHandle;

    public NameplateExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        // ── Pattern 1: Global text ──
        // Applies to ALL entities. Every entity with a nameplate will show this.
        NameplateAPI.register(this, "health", "Health Bar", "67/67");

        // ── Pattern 2: Per-entity filtering ──
        // Only shows "[Elite]" on entities you've marked as elite.
        // Return null for entities that shouldn't have this segment.
        NameplateAPI.register(this, "tier", "Elite Tier", ctx -> {
            if (eliteEntities.contains(ctx.entityUuid())) {
                return "[Elite]";
            }
            return null; // Not elite — skip this segment for this entity
        });

        // ── Pattern 3: Dynamic text per entity ──
        // Shows a level for every entity, but the value depends on the entity.
        NameplateAPI.register(this, "level", "Level",
                ctx -> "Lvl. " + Math.abs(ctx.entityUuid().hashCode() % 99));

        // ── Pattern 4: Temporary segment ──
        // Hold the handle to remove it at runtime (e.g. when a buff expires).
        buffHandle = NameplateAPI.register(this, "buff", "Active Buff", "[+10% XP]");
    }

    /** Mark an entity as elite — its nameplate will start showing the "[Elite]" tier. */
    public void markElite(UUID entityUuid) {
        eliteEntities.add(entityUuid);
    }

    /** Remove an entity's elite status. */
    public void removeElite(UUID entityUuid) {
        eliteEntities.remove(entityUuid);
    }

    /** Remove the buff segment entirely at runtime. */
    public void removeBuff() {
        if (buffHandle != null) {
            buffHandle.unregister();
            buffHandle = null;
        }
    }
}
