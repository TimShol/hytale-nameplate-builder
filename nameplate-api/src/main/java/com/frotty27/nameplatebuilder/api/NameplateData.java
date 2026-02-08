package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ECS component that holds per-entity nameplate segment text.
 *
 * <p>This component is managed automatically by {@link NameplateAPI#register} and
 * {@link NameplateAPI#remove} — most mods should use those static methods
 * rather than interacting with this class directly.</p>
 *
 * <p>Each entry in the internal map is keyed by a segment ID (e.g.
 * {@code "health"}) and maps to the text that should be displayed for
 * that segment on this entity. The NameplateBuilder aggregator reads
 * this component every tick for each visible entity.</p>
 *
 * @see NameplateAPI#register
 * @see NameplateAPI#remove
 * @see NameplateAPI#getComponentType()
 */
public final class NameplateData implements Component<EntityStore> {

    /**
     * Codec for serialization. The map is stored as a single comma-separated
     * string of {@code key=value} pairs (matching the pattern used by other
     * Hytale mods for map-in-component storage).
     */
    public static final BuilderCodec<NameplateData> CODEC = BuilderCodec
            .builder(NameplateData.class, NameplateData::new)
            .append(
                    new com.hypixel.hytale.codec.KeyedCodec<>("NpbEntries", Codec.STRING),
                    NameplateData::deserializeEntries, NameplateData::serializeEntries
            )
            .add()
            .build();

    private final Map<String, String> entries;

    /** Creates a new empty NameplateData component. */
    public NameplateData() {
        this.entries = new HashMap<>();
    }

    private NameplateData(Map<String, String> entries) {
        this.entries = new HashMap<>(entries);
    }

    /**
     * Set the text for a segment key on this entity.
     *
     * @param segmentKey the segment key (e.g. {@code "health"} or fully qualified {@code "Frotty27:MyMod:health"})
     * @param text       the text to display, or {@code null} to remove the entry
     */
    public void setText(String segmentKey, String text) {
        if (text == null || text.isBlank()) {
            entries.remove(segmentKey);
        } else {
            entries.put(segmentKey, text);
        }
    }

    /**
     * Get the text for a segment key.
     *
     * @param segmentKey the segment key
     * @return the text, or {@code null} if not set
     */
    public String getText(String segmentKey) {
        return entries.get(segmentKey);
    }

    /**
     * Remove the text for a segment key.
     *
     * @param segmentKey the segment key to remove
     */
    public void removeText(String segmentKey) {
        entries.remove(segmentKey);
    }

    /** Returns {@code true} if this component has no entries. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all entries in this component.
     *
     * @return map of segmentKey → text
     */
    public Map<String, String> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    @Override
    public NameplateData clone() {
        return new NameplateData(this.entries);
    }

    // ── Serialization ──

    private String serializeEntries() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(escapeEntry(entry.getKey()))
              .append('=')
              .append(escapeEntry(entry.getValue()));
        }
        return sb.toString();
    }

    private void deserializeEntries(String value) {
        entries.clear();
        if (value == null || value.isBlank()) {
            return;
        }
        // Split on commas that are not escaped
        String[] pairs = value.split(",");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx < 0) {
                continue;
            }
            String key = unescapeEntry(pair.substring(0, eqIdx));
            String val = unescapeEntry(pair.substring(eqIdx + 1));
            if (!key.isBlank() && !val.isBlank()) {
                entries.put(key, val);
            }
        }
    }

    private static String escapeEntry(String s) {
        return s.replace("\\", "\\\\")
                .replace(",", "\\c")
                .replace("=", "\\e");
    }

    private static String unescapeEntry(String s) {
        return s.replace("\\e", "=")
                .replace("\\c", ",")
                .replace("\\\\", "\\");
    }
}
