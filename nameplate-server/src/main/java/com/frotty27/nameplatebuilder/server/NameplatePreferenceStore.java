package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class NameplatePreferenceStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final UUID ADMIN_CHAIN_UUID = new UUID(0L, 0L);

    private final Path filePath;
    private final Map<UUID, Map<String, PreferenceSet>> data = new HashMap<>();

    private NameplateRegistry registry;

    // Temporary storage for entries loaded before segment IDs are resolved
    private final List<PendingEntry> pendingEntries = new ArrayList<>();

    private record PendingEntry(UUID viewer, String entityType, String pluginId, String segmentId, char type, String value, int intValue) {}


    NameplatePreferenceStore(Path filePath) {
        this.filePath = filePath;
    }

    void setRegistry(NameplateRegistry registry) {
        this.registry = registry;
    }


    void load() {
        data.clear();
        pendingEntries.clear();
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
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        // Ensure the set exists for this viewer/entityType
                        getSet(uuid, entityType, true);
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'S', parts[5], Integer.parseInt(parts[6])));
                    }
                    case "D" -> {
                        if (parts.length < 4) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), parts[2], true);
                        set.separator = parts[3].replace("\\p", "|").replace("\\\\", "\\");
                    }
                    case "B" -> {
                        if (parts.length < 6) continue;
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        getSet(uuid, entityType, true);
                        String unescaped = parts[5].replace("\\p", "|").replace("\\\\", "\\");
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'B', unescaped, 0));
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
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        getSet(uuid, entityType, true);
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'V', null, Integer.parseInt(parts[5])));
                    }
                    case "P" -> {
                        if (parts.length < 6) continue;
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        getSet(uuid, entityType, true);
                        String unescaped = parts[5].replace("\\p", "|").replace("\\\\", "\\");
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'P', unescaped, 0));
                    }
                    case "X" -> {
                        if (parts.length < 6) continue;
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        getSet(uuid, entityType, true);
                        String unescaped = parts[5].replace("\\p", "|").replace("\\\\", "\\");
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'X', unescaped, 0));
                    }
                    case "F" -> {
                        if (parts.length < 6) continue;
                        UUID uuid = UUID.fromString(parts[1]);
                        String entityType = parts[2];
                        getSet(uuid, entityType, true);
                        String unescaped = parts[5].replace("\\p", "|").replace("\\\\", "\\");
                        pendingEntries.add(new PendingEntry(uuid, entityType, parts[3], parts[4], 'F', unescaped, 0));
                    }
                    case "WP" -> {
                        if (parts.length < 4) continue;
                        PreferenceSet set = getSet(UUID.fromString(parts[1]), "*", true);
                        set.worldEnabled.put(parts[2], Boolean.parseBoolean(parts[3]));
                    }
                    default -> {

                        if (parts.length >= 6) {
                            UUID uuid = UUID.fromString(parts[0]);
                            String entityType = parts[1];
                            getSet(uuid, entityType, true);
                            pendingEntries.add(new PendingEntry(uuid, entityType, parts[2], parts[3], 'S', parts[4], Integer.parseInt(parts[5])));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to load preferences from %s", filePath);
        }
        migratePerNamespaceKeys();
    }

    void resolvePendingEntries(NameplateRegistry registry) {
        for (PendingEntry entry : pendingEntries) {
            SegmentKey key = registry.findBySegmentId(entry.segmentId());
            if (key == null || key.id() == SegmentKey.UNASSIGNED) continue;
            PreferenceSet set = getSet(entry.viewer(), entry.entityType(), true);
            set.ensureCapacity(key.id());
            switch (entry.type()) {
                case 'S' -> {
                    set.disabled[key.id()] = !Boolean.parseBoolean(entry.value());
                    set.order[key.id()] = entry.intValue();
                }
                case 'B' -> set.separatorAfter[key.id()] = entry.value();
                case 'V' -> set.selectedVariant[key.id()] = entry.intValue();
                case 'P' -> set.prefix[key.id()] = entry.value();
                case 'X' -> set.suffix[key.id()] = entry.value();
                case 'F' -> set.barEmptyChar[key.id()] = entry.value();
            }
        }
        pendingEntries.clear();
    }

    private void migratePerNamespaceKeys() {
        for (Map.Entry<UUID, Map<String, PreferenceSet>> viewerEntry : data.entrySet()) {
            Map<String, PreferenceSet> byEntity = viewerEntry.getValue();
            PreferenceSet existing = byEntity.get("_npcs");
            if (existing != null) {
                continue;
            }
            PreferenceSet best = null;
            int bestCount = 0;
            for (Map.Entry<String, PreferenceSet> entry : byEntity.entrySet()) {
                if (entry.getKey().startsWith("_npcs:")) {
                    // Count non-default order entries to find the richest set
                    int count = 0;
                    for (int o : entry.getValue().order) {
                        if (o != Integer.MAX_VALUE) count++;
                    }
                    if (best == null || count > bestCount) {
                        best = entry.getValue();
                        bestCount = count;
                    }
                }
            }
            if (best != null) {
                byEntity.put("_npcs", best);
            }
            byEntity.keySet().removeIf(k -> k.startsWith("_npcs:"));
        }
    }


    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to create preferences directory");
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

                    String escapedSep = set.separator.replace("\\", "\\\\").replace("|", "\\p");
                    writer.write("D|" + viewer + "|" + entityType + "|" + escapedSep);
                    writer.newLine();

                    if (registry != null) {
                        Map<SegmentKey, NameplateRegistry.Segment> allSegments = registry.getSegments();
                        for (SegmentKey key : allSegments.keySet()) {
                            int id = key.id();
                            if (id < 0 || id >= set.disabled.length) continue;
                            // Write S line if explicitly set (disabled != default false OR order != MAX_VALUE)
                            if (set.disabled[id] || set.order[id] != Integer.MAX_VALUE) {
                                writer.write("S|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + !set.disabled[id] + "|" + set.order[id]);
                                writer.newLine();
                            }
                            // Write other entries only if non-default
                            if (set.separatorAfter[id] != null) {
                                String escapedBlockSep = set.separatorAfter[id].replace("\\", "\\\\").replace("|", "\\p");
                                writer.write("B|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedBlockSep);
                                writer.newLine();
                            }
                            if (set.selectedVariant[id] != 0) {
                                writer.write("V|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + set.selectedVariant[id]);
                                writer.newLine();
                            }
                            if (set.prefix[id] != null) {
                                String escapedPrefix = set.prefix[id].replace("\\", "\\\\").replace("|", "\\p");
                                writer.write("P|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedPrefix);
                                writer.newLine();
                            }
                            if (set.suffix[id] != null) {
                                String escapedSuffix = set.suffix[id].replace("\\", "\\\\").replace("|", "\\p");
                                writer.write("X|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedSuffix);
                                writer.newLine();
                            }
                            if (set.barEmptyChar[id] != null) {
                                String escapedBar = set.barEmptyChar[id].replace("\\", "\\\\").replace("|", "\\p");
                                writer.write("F|" + viewer + "|" + entityType + "|" + key.pluginId() + "|" + key.segmentId() + "|" + escapedBar);
                                writer.newLine();
                            }
                        }
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
                    for (Map.Entry<String, Boolean> worldEntry : set.worldEnabled.entrySet()) {
                        writer.write("WP|" + viewer + "|" + worldEntry.getKey() + "|" + worldEntry.getValue());
                        writer.newLine();
                    }
                }
            }
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to save preferences to %s", filePath);

        }
    }

    boolean isNameplatesEnabled(UUID viewer) {
        PreferenceSet set = getSet(viewer, "*", false);
        return set == null || set.nameplatesEnabled;
    }

    boolean isChainEnabled(UUID viewer, String entityType) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return true;
        return set.nameplatesEnabled;
    }

    void setChainEnabled(UUID viewer, String entityType, boolean enabled) {
        PreferenceSet set = getSet(viewer, entityType, true);
        set.nameplatesEnabled = enabled;
    }


    void setNameplatesEnabled(UUID viewer, boolean enabled) {
        PreferenceSet set = getSet(viewer, "*", true);
        set.nameplatesEnabled = enabled;
    }


    boolean isShowWelcomeMessage(UUID viewer) {
        PreferenceSet set = getSet(viewer, "*", false);
        return set == null || set.showWelcomeMessage;
    }


    void setShowWelcomeMessage(UUID viewer, boolean show) {
        PreferenceSet set = getSet(viewer, "*", true);
        set.showWelcomeMessage = show;
    }


    boolean isWorldEnabled(UUID viewer, String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return true;
        }
        PreferenceSet set = getSet(viewer, "*", false);
        if (set == null) {
            return true;
        }
        return set.worldEnabled.getOrDefault(worldName, true);
    }

    void setWorldEnabled(UUID viewer, String worldName, boolean enabled) {
        PreferenceSet set = getSet(viewer, "*", true);
        if (enabled) {
            set.worldEnabled.remove(worldName);
        } else {
            set.worldEnabled.put(worldName, false);
        }
    }

    Map<String, Boolean> getPlayerWorldEnabled(UUID viewer) {
        PreferenceSet set = getSet(viewer, "*", false);
        if (set == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(set.worldEnabled);
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
        set.separator = separator == null ? "" : separator;
    }


    String getSeparatorAfter(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return " - ";
        }
        int id = key.id();
        if (id >= 0 && id < set.separatorAfter.length) {
            String sep = set.separatorAfter[id];
            if (sep != null) return sep;
        }
        return set.separator;
    }


    void setSeparatorAfter(UUID viewer, String entityType, SegmentKey key, String separator) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.separatorAfter[id] = separator;
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
        int id = key.id();
        if (id < 0 || id >= set.disabled.length) return true;
        return !set.disabled[id];
    }


    void enable(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        if (!set.disabled[id]) return; // already enabled
        set.disabled[id] = false;
        // Find next order value
        int nextOrder = 0;
        for (int o : set.order) {
            if (o != Integer.MAX_VALUE && o >= nextOrder) nextOrder = o + 1;
        }
        set.order[id] = nextOrder;
        invalidateChainCache();
    }


    void disable(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.disabled[id] = true;
        invalidateChainCache();
    }


    void disableAll(UUID viewer, String entityType, List<SegmentKey> available) {
        PreferenceSet set = getSet(viewer, entityType, true);
        for (SegmentKey key : available) {
            int id = key.id();
            set.ensureCapacity(id);
            set.disabled[id] = true;
        }
        invalidateChainCache();
    }


    // OPT-20: Cached chain per viewer+entityType+available, invalidated on preference changes
    private record CachedChain(int availableHash, List<SegmentKey> available, List<SegmentKey> chain) {}
    private final Map<UUID, Map<String, CachedChain>> chainCache = new HashMap<>();
    private volatile int chainCacheGeneration = 0;
    private int lastSeenGeneration = -1;

    void invalidateChainCache() {
        chainCacheGeneration++;
    }

    List<SegmentKey> getChain(UUID viewer,
                              String entityType,
                              List<SegmentKey> available,
                              Comparator<SegmentKey> defaultComparator) {
        if (available.isEmpty()) {
            return List.of();
        }

        // Clear entire cache when any preference changes
        int currentGen = chainCacheGeneration;
        if (currentGen != lastSeenGeneration) {
            chainCache.clear();
            lastSeenGeneration = currentGen;
        }

        // Check cache - keyed by viewer+entityType, validated against available list
        Map<String, CachedChain> viewerCache = chainCache.get(viewer);
        if (viewerCache != null) {
            CachedChain cached = viewerCache.get(entityType);
            if (cached != null
                    && cached.availableHash == available.hashCode()
                    && cached.available.equals(available)) {
                return cached.chain;
            }
        }

        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null && !ADMIN_CHAIN_UUID.equals(viewer)) {
            PreferenceSet adminSet = getSet(ADMIN_CHAIN_UUID, entityType, false);
            if (adminSet != null) {
                set = clonePreferenceSet(adminSet, viewer, entityType);
            }
        }
        if (set == null) {
            if (ADMIN_CHAIN_UUID.equals(viewer)) {
                PreferenceSet adminSet = getSet(viewer, entityType, true);
                for (SegmentKey key : available) {
                    int id = key.id();
                    adminSet.ensureCapacity(id);
                    adminSet.disabled[id] = true;
                }
                set = adminSet;
            } else {
                seedDefaultChain(viewer, entityType, available);
                set = getSet(viewer, entityType, false);
            }
        }
        if (set == null) {
            List<SegmentKey> copy = new ArrayList<>(available);
            copy.sort(defaultComparator);
            chainCache.computeIfAbsent(viewer, _ -> new HashMap<>())
                    .put(entityType, new CachedChain(available.hashCode(), available, copy));
            return copy;
        }
        final PreferenceSet resolved = set;
        List<SegmentKey> copy = new ArrayList<>();
        for (SegmentKey key : available) {
            int id = key.id();
            if (id < 0 || id >= resolved.disabled.length || !resolved.disabled[id]) {
                copy.add(key);
            }
        }
        copy.sort((a, b) -> {
            int oa = a.id() >= 0 && a.id() < resolved.order.length ? resolved.order[a.id()] : Integer.MAX_VALUE;
            int ob = b.id() >= 0 && b.id() < resolved.order.length ? resolved.order[b.id()] : Integer.MAX_VALUE;
            if (oa != ob) return Integer.compare(oa, ob);
            return defaultComparator.compare(a, b);
        });

        // Cache keyed by viewer+entityType, stores available for validation
        chainCache.computeIfAbsent(viewer, _ -> new HashMap<>())
                .put(entityType, new CachedChain(available.hashCode(), available, copy));
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
            int id = key.id();
            if (id >= 0 && id < set.disabled.length && set.disabled[id]) {
                copy.add(key);
            }
        }
        copy.sort(defaultComparator);
        return copy;
    }


    void snapshotChain(UUID viewer, String entityType, List<SegmentKey> available, Comparator<SegmentKey> defaultComparator) {
        List<SegmentKey> chain = getChain(viewer, entityType, available, defaultComparator);
        PreferenceSet set = getSet(viewer, entityType, true);
        for (int i = 0; i < chain.size(); i++) {
            int id = chain.get(i).id();
            set.ensureCapacity(id);
            set.disabled[id] = false;
            set.order[id] = i;
        }
        for (SegmentKey key : available) {
            int id = key.id();
            set.ensureCapacity(id);
            // If not in the chain, mark as disabled
            boolean inChain = false;
            for (SegmentKey chainKey : chain) {
                if (chainKey.equals(key)) { inChain = true; break; }
            }
            if (!inChain) {
                set.disabled[id] = true;
            }
        }
        invalidateChainCache();
    }


    int getSelectedVariant(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) {
            return 0;
        }
        int id = key.id();
        if (id < 0 || id >= set.selectedVariant.length) return 0;
        return set.selectedVariant[id];
    }


    void setSelectedVariant(UUID viewer, String entityType, SegmentKey key, int variantIndex) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.selectedVariant[id] = variantIndex;
    }


    String getPrefix(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "";
        int id = key.id();
        if (id < 0 || id >= set.prefix.length) return "";
        String val = set.prefix[id];
        return val != null ? val : "";
    }


    void setPrefix(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.prefix[id] = (value == null || value.isEmpty()) ? null : value;
    }


    String getSuffix(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "";
        int id = key.id();
        if (id < 0 || id >= set.suffix.length) return "";
        String val = set.suffix[id];
        return val != null ? val : "";
    }


    void setSuffix(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.suffix[id] = (value == null || value.isEmpty()) ? null : value;
    }


    String getBarEmptyChar(UUID viewer, String entityType, SegmentKey key) {
        PreferenceSet set = getSet(viewer, entityType, false);
        if (set == null) return "-";
        int id = key.id();
        if (id < 0 || id >= set.barEmptyChar.length) return "-";
        String val = set.barEmptyChar[id];
        return val != null ? val : "-";
    }


    void setBarEmptyChar(UUID viewer, String entityType, SegmentKey key, String value) {
        PreferenceSet set = getSet(viewer, entityType, true);
        int id = key.id();
        set.ensureCapacity(id);
        set.barEmptyChar[id] = (value == null || value.isEmpty() || "-".equals(value)) ? null : value;
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
            int id = ordered.get(i).id();
            set.ensureCapacity(id);
            set.order[id] = i;
        }
        invalidateChainCache();
    }

    private void seedDefaultChain(UUID viewer, String entityType, List<SegmentKey> available) {
        PreferenceSet set = getSet(viewer, entityType, true);
        boolean isPlayerChain = "_players".equals(entityType);
        String nameSegment = isPlayerChain ? "player-name" : "entity-name";

        int orderVal = 0;
        for (SegmentKey key : available) {
            int id = key.id();
            set.ensureCapacity(id);
            if (nameSegment.equals(key.segmentId())) {
                set.disabled[id] = false;
                set.order[id] = orderVal++;
            }
        }
        for (SegmentKey key : available) {
            int id = key.id();
            if ("health".equals(key.segmentId())) {
                set.disabled[id] = false;
                set.order[id] = orderVal++;
            }
        }
        for (SegmentKey key : available) {
            int id = key.id();
            // Disable segments not explicitly enabled above
            if (set.order[id] == Integer.MAX_VALUE) {
                set.disabled[id] = true;
            }
        }
    }

    private PreferenceSet clonePreferenceSet(PreferenceSet source, UUID viewer, String entityType) {
        PreferenceSet target = getSet(viewer, entityType, true);
        int len = source.disabled.length;
        target.disabled = Arrays.copyOf(source.disabled, len);
        target.order = Arrays.copyOf(source.order, len);
        target.separatorAfter = Arrays.copyOf(source.separatorAfter, len);
        target.selectedVariant = Arrays.copyOf(source.selectedVariant, len);
        target.prefix = Arrays.copyOf(source.prefix, len);
        target.suffix = Arrays.copyOf(source.suffix, len);
        target.barEmptyChar = Arrays.copyOf(source.barEmptyChar, len);
        target.separator = source.separator;
        return target;
    }

    PreferenceSet getSetDirect(UUID viewer, String entityType) {
        return getSet(viewer, entityType, false);
    }

    void removeFakeViewers(UUID[] viewers) {
        for (UUID viewer : viewers) {
            data.remove(viewer);
        }
        invalidateChainCache();
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

    static final class PreferenceSet {
        private static final int INITIAL_CAPACITY = 32;

        // Array-based segment preferences indexed by SegmentKey.id()
        boolean[] disabled = new boolean[INITIAL_CAPACITY];   // inverted: true = disabled, default false = enabled
        int[] order = new int[INITIAL_CAPACITY];              // default Integer.MAX_VALUE = no explicit order
        int[] selectedVariant = new int[INITIAL_CAPACITY];    // default 0
        String[] separatorAfter = new String[INITIAL_CAPACITY]; // default null = use global separator
        String[] prefix = new String[INITIAL_CAPACITY];       // default null = no prefix
        String[] suffix = new String[INITIAL_CAPACITY];       // default null = no suffix
        String[] barEmptyChar = new String[INITIAL_CAPACITY]; // default null = use "-"

        // Non-segment-keyed fields stay as-is
        final Map<String, Boolean> worldEnabled = new HashMap<>();
        boolean useGlobal = false;
        boolean onlyShowWhenLooking = false;
        boolean nameplatesEnabled = true;
        boolean showWelcomeMessage = true;
        String separator = " - ";
        double offset = 0.0;

        PreferenceSet() {
            Arrays.fill(order, Integer.MAX_VALUE);
        }

        void ensureCapacity(int id) {
            if (id >= disabled.length) {
                int newLen = Math.max(disabled.length * 2, id + 1);
                disabled = Arrays.copyOf(disabled, newLen);
                int[] newOrder = new int[newLen];
                Arrays.fill(newOrder, order.length, newLen, Integer.MAX_VALUE);
                System.arraycopy(order, 0, newOrder, 0, order.length);
                order = newOrder;
                selectedVariant = Arrays.copyOf(selectedVariant, newLen);
                separatorAfter = Arrays.copyOf(separatorAfter, newLen);
                prefix = Arrays.copyOf(prefix, newLen);
                suffix = Arrays.copyOf(suffix, newLen);
                barEmptyChar = Arrays.copyOf(barEmptyChar, newLen);
            }
        }
    }
}
