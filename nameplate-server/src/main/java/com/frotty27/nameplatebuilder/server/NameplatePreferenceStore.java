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
                if (parts[0].equals("U")) {
                    if (parts.length < 4) {
                        continue;
                    }
                    UUID viewer = UUID.fromString(parts[1]);
                    String entityType = parts[2];
                    boolean useGlobal = Boolean.parseBoolean(parts[3]);
                    PreferenceSet set = data
                            .computeIfAbsent(viewer, ignored -> new HashMap<>())
                            .computeIfAbsent(entityType, ignored -> new PreferenceSet());
                    set.useGlobal = useGlobal;
                    continue;
                }
                if (parts[0].equals("S")) {
                    if (parts.length < 7) {
                        continue;
                    }
                    UUID viewer = UUID.fromString(parts[1]);
                    String entityType = parts[2];
                    String pluginId = parts[3];
                    String segmentId = parts[4];
                    boolean enabled = Boolean.parseBoolean(parts[5]);
                    int order = Integer.parseInt(parts[6]);

                    SegmentKey key = new SegmentKey(pluginId, segmentId);
                    PreferenceSet set = data
                            .computeIfAbsent(viewer, ignored -> new HashMap<>())
                            .computeIfAbsent(entityType, ignored -> new PreferenceSet());

                    set.enabled.put(key, enabled);
                    set.order.put(key, order);
                    continue;
                }
                // Legacy format: viewer|entityType|pluginId|segmentId|enabled|order
                if (parts.length >= 6) {
                    UUID viewer = UUID.fromString(parts[0]);
                    String entityType = parts[1];
                    String pluginId = parts[2];
                    String segmentId = parts[3];
                    boolean enabled = Boolean.parseBoolean(parts[4]);
                    int order = Integer.parseInt(parts[5]);

                    SegmentKey key = new SegmentKey(pluginId, segmentId);
                    PreferenceSet set = data
                            .computeIfAbsent(viewer, ignored -> new HashMap<>())
                            .computeIfAbsent(entityType, ignored -> new PreferenceSet());

                    set.enabled.put(key, enabled);
                    set.order.put(key, order);
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
            writer.write("# U|viewerUuid|entityType|useGlobal");
            writer.newLine();
            for (Map.Entry<UUID, Map<String, PreferenceSet>> viewerEntry : data.entrySet()) {
                UUID viewer = viewerEntry.getKey();
                for (Map.Entry<String, PreferenceSet> entityEntry : viewerEntry.getValue().entrySet()) {
                    String entityType = entityEntry.getKey();
                    PreferenceSet set = entityEntry.getValue();
                    writer.write("U|" + viewer + "|" + entityType + "|" + set.useGlobal);
                    writer.newLine();
                    for (Map.Entry<SegmentKey, Boolean> enabledEntry : set.enabled.entrySet()) {
                        SegmentKey key = enabledEntry.getKey();
                        boolean enabled = enabledEntry.getValue();
                        int order = set.order.getOrDefault(key, -1);
                        writer.write("S|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + enabled + "|" + order);
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

    void toggleUseGlobal(UUID viewer, String entityType) {
        if ("*".equals(entityType)) {
            return;
        }
        PreferenceSet set = getSet(viewer, entityType, true);
        set.useGlobal = !set.useGlobal;
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
        private boolean useGlobal = false;
    }
}
