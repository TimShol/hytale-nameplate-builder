package com.frotty27.nameplatebuilder.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player nameplate preferences to disk.
 *
 * <p>Stores each player's segment chain (enabled/disabled, ordering), per-block
 * separators, global default separator, vertical offset, look-at toggle,
 * master enable/disable state, format variant selections, prefix/suffix text
 * wrapping, and bar empty-fill characters. Data is keyed by viewer UUID and
 * entity type ({@code "_npcs"}, {@code "_players"}, or {@code "*"} for global settings).</p>
 *
 * <p>File format uses pipe-separated lines with a single-letter prefix indicating
 * the record type: {@code S} = segment, {@code U} = user settings, {@code D} = default
 * separator, {@code B} = per-block separator, {@code O} = offset, {@code E} = master toggle,
 * {@code V} = variant selection, {@code P} = prefix, {@code X} = suffix,
 * {@code F} = bar empty fill character, {@code W} = welcome message preference.</p>
 *
 * @see NameplateBuilderPage
 * @see NameplateAggregatorSystem
 */
final class NameplatePreferenceStore {

    private final Path filePath;
    private final Map<UUID, Map<String, PreferenceSet>> data = new HashMap<>();

    /**
     * Creates a new preference store backed by the given file.
     *
     * @param filePath path to the {@code preferences.txt} file
     */
    NameplatePreferenceStore(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Loads all player preferences from disk, replacing any in-memory state.
     *
     * <p>If the file does not exist or is corrupt, the store starts empty
     * and all players receive default settings.</p>
     */
    void load() {
        data.clear();
        if (!Files.exists(filePath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 2) {
                    continue;
                }
                switch (parts[0]) {
                    case "U" -> {
                        if (parts.length < 4) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        set.useGlobal = Boolean.parseBoolean(parts[3]);
                        set.onlyShowWhenLooking = parts.length >= 5 && Boolean.parseBoolean(parts[4]);
                    }
                    case "S" -> {
                        if (parts.length < 7) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.enabled.put(key, Boolean.parseBoolean(parts[5]));
                        set.order.put(key, Integer.parseInt(parts[6]));
                    }
                    case "D" -> {
                        if (parts.length < 4) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        set.separator = parts[3].replace("\\p", "|").replace("\\\\", "\\");
                    }
                    case "B" -> {
                        if (parts.length < 6) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.separatorAfter.put(key, parts[5].replace("\\p", "|").replace("\\\\", "\\"));
                    }
                    case "O" -> {
                        if (parts.length < 4) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        set.offset = Double.parseDouble(parts[3]);
                    }
                    case "E" -> {
                        if (parts.length < 3) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), "*", true);
                        set.nameplatesEnabled = Boolean.parseBoolean(parts[2]);
                    }
                    case "W" -> {
                        if (parts.length < 3) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), "*", true);
                        set.showWelcomeMessage = Boolean.parseBoolean(parts[2]);
                    }
                    case "V" -> {
                        if (parts.length < 6) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.selectedVariant.put(key, Integer.parseInt(parts[5]));
                    }
                    case "P" -> {
                        if (parts.length < 6) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.prefix.put(key, parts[5].replace("\\p", "|").replace("\\\\", "\\"));
                    }
                    case "X" -> {
                        if (parts.length < 6) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.suffix.put(key, parts[5].replace("\\p", "|").replace("\\\\", "\\"));
                    }
                    case "F" -> {
                        if (parts.length < 6) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        SegmentKey key = new SegmentKey(parts[3], parts[4]);
                        set.barEmptyChar.put(key, parts[5].replace("\\p", "|").replace("\\\\", "\\"));
                    }
                    default -> {
                        // Legacy format: viewer|entityType|pluginId|segmentId|enabled|order
                        if (parts.length >= 6) {
                            PreferenceSet set = getSet(UUID.fromString(parts[0]), parts[1], true);
                            SegmentKey key = new SegmentKey(parts[2], parts[3]);
                            set.enabled.put(key, Boolean.parseBoolean(parts[4]));
                            set.order.put(key, Integer.parseInt(parts[5]));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException _) {
            // Corrupt or missing preference file — safe to ignore, defaults will apply
        }
    }

    /**
     * Persists all player preferences to disk.
     *
     * <p>Called during server shutdown and after admin configuration changes.
     * The file includes comment headers documenting each line format.</p>
     */
    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException _) {
            // Best-effort directory creation — save will fail below if this didn't work
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("# S|viewerUuid|entityType|pluginId|segmentId|enabled|order");
            writer.newLine();
            writer.write("# U|viewerUuid|entityType|useGlobal|onlyShowWhenLooking");
            writer.newLine();
            writer.write("# D|viewerUuid|entityType|separator");
            writer.newLine();
            writer.write("# B|viewerUuid|entityType|pluginId|segmentId|separatorAfter");
            writer.newLine();
            writer.write("# O|viewerUuid|entityType|offset");
            writer.newLine();
            writer.write("# E|viewerUuid|nameplatesEnabled");
            writer.newLine();
            writer.write("# V|viewerUuid|entityType|pluginId|segmentId|variantIndex");
            writer.newLine();
            writer.write("# P|viewerUuid|entityType|pluginId|segmentId|prefix");
            writer.newLine();
            writer.write("# X|viewerUuid|entityType|pluginId|segmentId|suffix");
            writer.newLine();
            writer.write("# F|viewerUuid|entityType|pluginId|segmentId|barEmptyChar");
            writer.newLine();
            writer.write("# W|viewerUuid|showWelcomeMessage");
            writer.newLine();
            for (Map.Entry<UUID, Map<String, PreferenceSet>> viewerEntry : data.entrySet()) {
                UUID viewer = viewerEntry.getKey();
                for (Map.Entry<String, PreferenceSet> entityEntry : viewerEntry.getValue().entrySet()) {
                    String entityType = entityEntry.getKey();
                    PreferenceSet set = entityEntry.getValue();
                    writer.write("U|" + viewer + "|" + entityType + "|" + set.useGlobal + "|" + set.onlyShowWhenLooking);
                    writer.newLine();
                    // Escape pipe chars in separator: \ → \\, | → \p
                    String escapedSep = set.separator.replace("\\", "\\\\").replace("|", "\\p");
                    writer.write("D|" + viewer + "|" + entityType + "|" + escapedSep);
                    writer.newLine();
                    for (Map.Entry<SegmentKey, Boolean> enabledEntry : set.enabled.entrySet()) {
                        SegmentKey key = enabledEntry.getKey();
                        boolean enabled = enabledEntry.getValue();
                        int order = set.order.getOrDefault(key, -1);
                        writer.write("S|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + enabled + "|" + order);
                        writer.newLine();
                    }
                    for (Map.Entry<SegmentKey, String> sepEntry : set.separatorAfter.entrySet()) {
                        SegmentKey key = sepEntry.getKey();
                        String escapedBlockSep = sepEntry.getValue().replace("\\", "\\\\").replace("|", "\\p");
                        writer.write("B|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedBlockSep);
                        writer.newLine();
                    }
                    for (Map.Entry<SegmentKey, Integer> variantEntry : set.selectedVariant.entrySet()) {
                        SegmentKey key = variantEntry.getKey();
                        int vi = variantEntry.getValue();
                        if (vi != 0) {
                            writer.write("V|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + vi);
                            writer.newLine();
                        }
                    }
                    for (Map.Entry<SegmentKey, String> prefixEntry : set.prefix.entrySet()) {
                        SegmentKey key = prefixEntry.getKey();
                        String escapedPrefix = prefixEntry.getValue().replace("\\", "\\\\").replace("|", "\\p");
                        writer.write("P|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedPrefix);
                        writer.newLine();
                    }
                    for (Map.Entry<SegmentKey, String> suffixEntry : set.suffix.entrySet()) {
                        SegmentKey key = suffixEntry.getKey();
                        String escapedSuffix = suffixEntry.getValue().replace("\\", "\\\\").replace("|", "\\p");
                        writer.write("X|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedSuffix);
                        writer.newLine();
                    }
                    for (Map.Entry<SegmentKey, String> barEntry : set.barEmptyChar.entrySet()) {
                        SegmentKey key = barEntry.getKey();
                        String escapedBar = barEntry.getValue().replace("\\", "\\\\").replace("|", "\\p");
                        writer.write("F|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedBar);
                        writer.newLine();
                    }
                    if (set.offset != 0.0) {
                        writer.write("O|" + viewer + "|" + entityType + "|" + set.offset);
                        writer.newLine();
                    }
                    if (!set.nameplatesEnabled) {
                        writer.write("E|" + viewer + "|" + set.nameplatesEnabled);
                        writer.newLine();
                    }
                    if (!set.showWelcomeMessage) {
                        writer.write("W|" + viewer + "|" + set.showWelcomeMessage);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException _) {
            // Non-critical — preferences will be re-saved next shutdown
        }
    }

    /** Returns {@code true} if the viewer uses global preferences for the given entity type. */
    boolean isUsingGlobal(UUID viewer, String entityType) {
        if ("*".equals(entityType)) {
            return false;
        }
        PreferenceSet set = getSet(viewer, entityType, false);
        return set != null && set.useGlobal;
    }

    /**
     * Check if a viewer has any stored preferences for a specific entity type.
     * Returns {@code false} for the global wildcard {@code "*"} or if no
     * preferences have been saved for this entity type.
     */
    boolean hasPreferences(UUID viewer, String entityType) {
        if ("*".equals(entityType)) {
            return false;
        }
        return getSet(viewer, entityType, false) != null;
    }

    /** Returns {@code true} if nameplates are enabled for the viewer (defaults to {@code true}). */
    boolean isNameplatesEnabled(UUID viewer) {
        PreferenceSet set = getSet(viewer, "*", false);
        return set == null || set.nameplatesEnabled;
    }

    /** Sets the master nameplate enable/disable toggle for the viewer. */
    void setNameplatesEnabled(UUID viewer, boolean enabled) {
        PreferenceSet set = getSet(viewer, "*", true);
        set.nameplatesEnabled = enabled;
    }

    /** Returns {@code true} if the viewer wants to see the join welcome message (defaults to {@code true}). */
    boolean isShowWelcomeMessage(UUID viewer) {
        PreferenceSet set = getSet(viewer, "*", false);
        return set == null || set.showWelcomeMessage;
    }

    /** Sets whether the viewer sees the coloured join welcome message. */
    void setShowWelcomeMessage(UUID viewer, boolean show) {
        PreferenceSet set = getSet(viewer, "*", true);
        set.showWelcomeMessage = show;
    }

    /** Returns {@code true} if the viewer has the "only show when looking at" filter enabled. */
    boolean isOnlyShowWhenLooking(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        return set != null && set.onlyShowWhenLooking;
    }

    /** Sets the "only show when looking at" view-cone filter for the viewer. */
    void setOnlyShowWhenLooking(UUID viewer, String entityType, boolean value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.onlyShowWhenLooking = value;
    }

    /** Returns the default separator string between segments (defaults to {@code " - "}). */
    String getSeparator(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return " - ";
        }
        return set.separator;
    }

    /** Sets the default separator string between segments for the viewer. */
    void setSeparator(UUID viewer, String entityType, String separator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.separator = separator == null ? "" : separator;
    }

    /**
     * Get the separator shown after the given segment in the chain.
     * Falls back to the global default separator if no per-block override is set.
     */
    String getSeparatorAfter(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return " - ";
        }
        String sep = set.separatorAfter.get(key);
        return sep != null ? sep : set.separator;
    }

    /**
     * Sets or removes the per-block separator shown after a specific segment.
     *
     * @param separator the separator text, or {@code null} to revert to the default
     */
    void setSeparatorAfter(UUID viewer, String entityType, SegmentKey key, String separator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (separator == null) {
            set.separatorAfter.remove(key);
        } else {
            set.separatorAfter.put(key, separator);
        }
    }

    /** Returns the vertical nameplate offset for the viewer (defaults to {@code 0.0}). */
    double getOffset(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return 0.0;
        }
        return set.offset;
    }

    /** Sets the vertical nameplate offset, clamped to {@code [-5.0, 5.0]}. */
    void setOffset(UUID viewer, String entityType, double offset) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.offset = Math.max(-5.0, Math.min(5.0, offset));
    }

    /** Returns {@code true} if the segment is enabled in the viewer's chain (defaults to {@code true}). */
    boolean isEnabled(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return true;
        }
        return set.enabled.getOrDefault(key, true);
    }

    /** Enables a segment in the viewer's chain, appending it at the end of the ordering. */
    void enable(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (set.enabled.getOrDefault(key, false)) {
            return;
        }
        int nextOrder = set.order.values().stream().mapToInt(v -> v).max().orElse(-1) + 1;
        set.enabled.put(key, true);
        set.order.put(key, nextOrder);
    }

    /** Disables a segment in the viewer's chain (removes it from the visible output). */
    void disable(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.enabled.put(key, false);
    }

    /**
     * Disable all segments for a viewer on the given entity type.
     * This clears the entire chain without removing ordering information,
     * so segments can be re-added individually later.
     */
    void disableAll(UUID viewer, String entityType, List<SegmentKey> available) {
        PreferenceSet set = getSet(viewer, entityType, true);
        for (SegmentKey key : available) {
            set.enabled.put(key, false);
        }
    }

    /**
     * Returns the viewer's enabled segment chain, ordered by stored preference.
     *
     * <p>Segments without stored preferences default to enabled. If no
     * preferences exist at all, all available segments are returned in the
     * default comparator order.</p>
     *
     * @param available         all available segment keys for this entity
     * @param defaultComparator fallback ordering for segments without stored order
     * @return ordered list of enabled segments
     */
    List<SegmentKey> getChain(UUID viewer,
                              String entityType,
                              List<SegmentKey> available,
                              Comparator<SegmentKey> defaultComparator) {
        if (available.isEmpty()) {
            return List.of();
        }
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            List<SegmentKey> copy = new ArrayList<>(available);
            copy.sort(defaultComparator);
            return copy;
        }
        List<SegmentKey> copy = new ArrayList<>();
        for (SegmentKey key : available) {
            if (set.enabled.getOrDefault(key, true)) {
                copy.add(key);
            }
        }
        copy.sort((a, b) -> {
            int oa = set.order.getOrDefault(a, Integer.MAX_VALUE);
            int ob = set.order.getOrDefault(b, Integer.MAX_VALUE);
            if (oa != ob) return Integer.compare(oa, ob);
            return defaultComparator.compare(a, b);
        });
        return copy;
    }

    /**
     * Returns segments that the viewer has explicitly disabled (removed from their chain).
     *
     * <p>Only returns segments that have an explicit {@code false} entry in the
     * enabled map — segments with no stored preference are considered enabled.</p>
     *
     * @param available         all available segment keys for this entity
     * @param defaultComparator ordering for the returned list
     * @return sorted list of disabled segments
     */
    List<SegmentKey> getAvailable(UUID viewer,
                                  String entityType,
                                  List<SegmentKey> available,
                                  Comparator<SegmentKey> defaultComparator) {
        if (available.isEmpty()) {
            return List.of();
        }
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return List.of();
        }
        List<SegmentKey> copy = new ArrayList<>();
        for (SegmentKey key : available) {
            if (!set.enabled.getOrDefault(key, true)) {
                copy.add(key);
            }
        }
        copy.sort(defaultComparator);
        return copy;
    }

    /**
     * Snapshot the current chain into the enabled/order maps so that it persists
     * across save/load cycles. Without this, segments that are "enabled by default"
     * (never explicitly added or removed) would have no S-lines in the file and
     * their ordering would revert to the default after a reload.
     */
    void snapshotChain(UUID viewer, String entityType, List<SegmentKey> available, Comparator<SegmentKey> defaultComparator) {
        List<SegmentKey> chain = getChain(viewer, entityType, available, defaultComparator);
        PreferenceSet set = getSet(viewer, entityType, true);
        for (int i = 0; i < chain.size(); i++) {
            set.enabled.put(chain.get(i), true);
            set.order.put(chain.get(i), i);
        }
        // Also ensure explicitly disabled segments are recorded
        for (SegmentKey key : available) {
            if (!set.enabled.containsKey(key)) {
                set.enabled.put(key, false);
            }
        }
    }

    /** Returns the viewer's selected format variant index for a segment (defaults to {@code 0}). */
    int getSelectedVariant(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return 0;
        }
        return set.selectedVariant.getOrDefault(key, 0);
    }

    /** Sets the format variant index for a segment. Index {@code 0} clears the override. */
    void setSelectedVariant(UUID viewer, String entityType, SegmentKey key, int variantIndex) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (variantIndex == 0) {
            set.selectedVariant.remove(key);
        } else {
            set.selectedVariant.put(key, variantIndex);
        }
    }

    /** Returns the prefix text wrapping a segment's output (defaults to empty). */
    String getPrefix(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "";
        return set.prefix.getOrDefault(key, "");
    }

    /** Sets or clears the prefix text for a segment. Empty or {@code null} clears it. */
    void setPrefix(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (value == null || value.isEmpty()) {
            set.prefix.remove(key);
        } else {
            set.prefix.put(key, value);
        }
    }

    /** Returns the suffix text wrapping a segment's output (defaults to empty). */
    String getSuffix(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "";
        return set.suffix.getOrDefault(key, "");
    }

    /** Sets or clears the suffix text for a segment. Empty or {@code null} clears it. */
    void setSuffix(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (value == null || value.isEmpty()) {
            set.suffix.remove(key);
        } else {
            set.suffix.put(key, value);
        }
    }

    /** Returns the custom bar empty character for this segment, defaulting to {@code "-"}. */
    String getBarEmptyChar(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "-";
        String val = set.barEmptyChar.get(key);
        return val != null && !val.isEmpty() ? val : "-";
    }

    /** Sets or clears the custom bar empty character. {@code null}, empty, or {@code "-"} resets to default. */
    void setBarEmptyChar(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (value == null || value.isEmpty() || "-".equals(value)) {
            set.barEmptyChar.remove(key);
        } else {
            set.barEmptyChar.put(key, value);
        }
    }

    /**
     * Moves a segment within the chain by the given delta ({@code -1} = up, {@code +1} = down).
     *
     * <p>Reorders the chain and reassigns order indices for all segments
     * in the chain. No-op if the segment is already at the boundary.</p>
     */
    void move(UUID viewer, String entityType, SegmentKey key, int delta, List<SegmentKey> available, Comparator<SegmentKey> defaultComparator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        List<SegmentKey> ordered = getChain(viewer, entityType, available, defaultComparator);
        int index = ordered.indexOf(key);
        if (index < 0) return;
        int newIndex = Math.max(0, Math.min(ordered.size() - 1, index + delta));
        if (newIndex == index) return;
        ordered.remove(index);
        ordered.add(newIndex, key);
        for (int i = 0; i < ordered.size(); i++) {
            set.order.put(ordered.get(i), i);
        }
    }

    private PreferenceSet getSet(UUID viewer, String entityType, boolean create) {
        Map<String, PreferenceSet> byEntity = data.get(viewer);
        if (byEntity == null && create) {
            byEntity = new HashMap<>();
            data.put(viewer, byEntity);
        }
        if (byEntity == null) {
            return null;
        }
        PreferenceSet set = byEntity.get(entityType);
        if (set == null && create) {
            set = new PreferenceSet();
            byEntity.put(entityType, set);
        }
        return set;
    }

    private static final class PreferenceSet {
        private final Map<SegmentKey, Boolean> enabled = new HashMap<>();
        private final Map<SegmentKey, Integer> order = new HashMap<>();
        private final Map<SegmentKey, String> separatorAfter = new HashMap<>();
        private final Map<SegmentKey, Integer> selectedVariant = new HashMap<>();
        private final Map<SegmentKey, String> prefix = new HashMap<>();
        private final Map<SegmentKey, String> suffix = new HashMap<>();
        private final Map<SegmentKey, String> barEmptyChar = new HashMap<>();
        private boolean useGlobal = false;
        private boolean onlyShowWhenLooking = false;
        private boolean nameplatesEnabled = true;
        private boolean showWelcomeMessage = true;
        private String separator = " - ";
        private double offset = 0.0;
    }
}
