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

final class NameplatePreferenceStore {

    private final Path filePath;
    private final Map<UUID, Map<String, PreferenceSet>> data = new HashMap<>();

    NameplatePreferenceStore(Path filePath) {
        this.filePath = filePath;
    }

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
                    if (set.offset != 0.0) {
                        writer.write("O|" + viewer + "|" + entityType + "|" + set.offset);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException _) {
            // Non-critical — preferences will be re-saved next shutdown
        }
    }

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

    boolean isOnlyShowWhenLooking(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        return set != null && set.onlyShowWhenLooking;
    }

    void setOnlyShowWhenLooking(UUID viewer, String entityType, boolean value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.onlyShowWhenLooking = value;
    }

    String getSeparator(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return " - ";
        }
        return set.separator;
    }

    void setSeparator(UUID viewer, String entityType, String separator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.separator = (separator == null || separator.isEmpty()) ? " - " : separator;
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

    void setSeparatorAfter(UUID viewer, String entityType, SegmentKey key, String separator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (separator == null) {
            set.separatorAfter.remove(key);
        } else {
            set.separatorAfter.put(key, separator);
        }
    }

    double getOffset(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return 0.0;
        }
        return set.offset;
    }

    void setOffset(UUID viewer, String entityType, double offset) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.offset = Math.max(-5.0, Math.min(5.0, offset));
    }

    boolean isEnabled(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return true;
        }
        return set.enabled.getOrDefault(key, true);
    }

    void enable(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, true);
        if (set.enabled.getOrDefault(key, false)) {
            return;
        }
        int nextOrder = set.order.values().stream().mapToInt(v -> v).max().orElse(-1) + 1;
        set.enabled.put(key, true);
        set.order.put(key, nextOrder);
    }

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
        private boolean useGlobal = false;
        private boolean onlyShowWhenLooking = false;
        private String separator = " - ";
        private double offset = 0.0;
    }
}
