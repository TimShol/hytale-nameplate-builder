package com.frotty27.nameplatebuilder.api;

/**
 * Indicates which entity types a nameplate segment is intended for.
 *
 * <p>Pass a {@code SegmentTarget} to
 * {@link NameplateAPI#describe(com.hypixel.hytale.server.core.plugin.JavaPlugin, String, String, SegmentTarget)}
 * so that the Nameplate Builder UI can display a tag (e.g. {@code [All]},
 * {@code [Players]}, {@code [NPCs]}) next to each segment, helping players
 * understand which segments are relevant to which entities.</p>
 *
 * <p>This is purely a UI hint — it does <b>not</b> enforce any restrictions at
 * runtime. A mod can still call {@link NameplateAPI#register} with any segment
 * on any entity regardless of the declared target.</p>
 *
 * @see NameplateAPI#describe(com.hypixel.hytale.server.core.plugin.JavaPlugin, String, String, SegmentTarget)
 */
public enum SegmentTarget {

    /** Segment applies to all entity types (players, NPCs, etc.). */
    ALL("All"),

    /** Segment is intended for player entities only. */
    PLAYERS("Players"),

    /** Segment is intended for NPC entities only. */
    NPCS("NPCs");

    private final String label;

    SegmentTarget(String label) {
        this.label = label;
    }

    /**
     * Short human-readable label for the UI (e.g. {@code "All"}, {@code "Players"}).
     *
     * @return the display label
     */
    public String getLabel() {
        return label;
    }
}
