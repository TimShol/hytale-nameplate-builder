package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class AdminConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Set<String> BUILT_IN_SEGMENT_IDS = Set.of(
            "entity-name", "player-name", "player-name.1",
            "health", "health.1", "health.2",
            "stamina", "stamina.1", "stamina.2",
            "mana", "mana.1", "mana.2");

    private final Path filePath;
    private final Set<SegmentKey> requiredSegments = ConcurrentHashMap.newKeySet();
    private final Set<SegmentKey> disabledSegments = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> namespaceEnabled = new ConcurrentHashMap<>();
    private final Map<String, Boolean> worldEnabled = new ConcurrentHashMap<>();
    private volatile String serverName = "";
    private volatile boolean welcomeMessagesEnabled = false;
    private volatile boolean npcChainLocked = false;
    private volatile boolean playerChainLocked = false;
    private volatile boolean masterEnabled = true;
    private volatile boolean playerChainEnabled = true;
    private volatile boolean npcChainEnabled = true;

    private final Map<String, Set<String>> segmentsByNamespace = new ConcurrentHashMap<>();
    private final Set<String> integratedNamespaces = ConcurrentHashMap.newKeySet();
    private final Map<String, String> namespaceDisplayNames = new ConcurrentHashMap<>();
    private final Set<String> blacklistedNpcs = ConcurrentHashMap.newKeySet();


    AdminConfigStore(Path filePath) {
        this.filePath = filePath;
        namespaceDisplayNames.put("hytale", "NameplateBuilder");
    }


    void load() {
        requiredSegments.clear();
        disabledSegments.clear();
        namespaceEnabled.clear();
        worldEnabled.clear();
        blacklistedNpcs.clear();
        serverName = "";
        welcomeMessagesEnabled = false;
        npcChainLocked = false;
        playerChainLocked = false;
        masterEnabled = true;
        playerChainEnabled = true;
        npcChainEnabled = true;
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
                    case "S" -> serverName = parts[1];
                    case "G" -> welcomeMessagesEnabled = Boolean.parseBoolean(parts[1]);
                    case "L" -> {
                        boolean locked = Boolean.parseBoolean(parts[1]);
                        npcChainLocked = locked;
                        playerChainLocked = locked;
                    }
                    case "LN" -> npcChainLocked = Boolean.parseBoolean(parts[1]);
                    case "LP" -> playerChainLocked = Boolean.parseBoolean(parts[1]);
                    case "M" -> masterEnabled = Boolean.parseBoolean(parts[1]);
                    case "PC" -> playerChainEnabled = Boolean.parseBoolean(parts[1]);
                    case "NC" -> npcChainEnabled = Boolean.parseBoolean(parts[1]);
                    case "NS" -> {
                        if (parts.length >= 3) {
                            String namespace = parts[1].trim();
                            if (!namespace.isEmpty()) {
                                namespaceEnabled.put(namespace, Boolean.parseBoolean(parts[2]));
                            }
                        }
                    }
                    case "WE" -> {
                        if (parts.length >= 3) {
                            String worldName = parts[1].trim();
                            if (!worldName.isEmpty()) {
                                worldEnabled.put(worldName, Boolean.parseBoolean(parts[2]));
                            }
                        }
                    }
                    case "A" -> {
                        String namespace = parts[1].trim();
                        if (!namespace.isEmpty()) {
                            namespaceEnabled.put(namespace, true);
                        }
                    }
                    case "X" -> {
                        String namespace = parts[1].trim();
                        if (!namespace.isEmpty()) {
                            namespaceEnabled.put(namespace, false);
                        }
                    }
                    case "R" -> {
                        if (parts.length >= 3) {
                            requiredSegments.add(new SegmentKey(parts[1], parts[2]));
                        }
                    }
                    case "D" -> {
                        if (parts.length >= 3) {
                            disabledSegments.add(new SegmentKey(parts[1], parts[2]));
                        }
                    }
                    case "BL" -> {
                        if (parts.length >= 2) {
                            blacklistedNpcs.add(parts[1]);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to load admin config from %s", filePath);
        }
    }


    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to create admin config directory");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("# Server name for welcome message");
            writer.newLine();
            writer.write("S|" + serverName);
            writer.newLine();
            writer.newLine();
            writer.write("# Show welcome messages for all players");
            writer.newLine();
            writer.write("G|" + welcomeMessagesEnabled);
            writer.newLine();
            writer.newLine();
            writer.write("# Lock nameplate order to admin-configured chain (per-chain)");
            writer.newLine();
            writer.write("LN|" + npcChainLocked);
            writer.newLine();
            writer.write("LP|" + playerChainLocked);
            writer.newLine();
            writer.newLine();
            writer.write("# Master enable - when false, NameplateBuilder does nothing");
            writer.newLine();
            writer.write("M|" + masterEnabled);
            writer.newLine();
            writer.newLine();
            writer.write("# Player chain enable - when false, no player nameplates");
            writer.newLine();
            writer.write("PC|" + playerChainEnabled);
            writer.newLine();
            writer.newLine();
            writer.write("# NPC chain enable - when false, no NPC nameplates");
            writer.newLine();
            writer.write("NC|" + npcChainEnabled);
            writer.newLine();
            writer.newLine();
            writer.write("# Per-namespace enable/disable");
            writer.newLine();
            writer.write("# NS|namespace|true/false");
            writer.newLine();
            for (Map.Entry<String, Boolean> entry : namespaceEnabled.entrySet()) {
                writer.write("NS|" + entry.getKey() + "|" + entry.getValue());
                writer.newLine();
            }
            writer.newLine();
            writer.write("# Per-world enable/disable");
            writer.newLine();
            writer.write("# WE|worldName|true/false");
            writer.newLine();
            for (Map.Entry<String, Boolean> entry : worldEnabled.entrySet()) {
                writer.write("WE|" + entry.getKey() + "|" + entry.getValue());
                writer.newLine();
            }
            writer.newLine();
            writer.write("# Required segments - always displayed for all players");
            writer.newLine();
            writer.write("# R|pluginId|segmentId");
            writer.newLine();
            for (SegmentKey key : requiredSegments) {
                writer.write("R|" + key.pluginId() + "|" + key.segmentId());
                writer.newLine();
            }
            writer.newLine();
            writer.write("# Disabled segments - hidden from all players");
            writer.newLine();
            writer.write("# D|pluginId|segmentId");
            writer.newLine();
            for (SegmentKey key : disabledSegments) {
                writer.write("D|" + key.pluginId() + "|" + key.segmentId());
                writer.newLine();
            }
            writer.newLine();
            writer.write("# Blacklisted NPCs - these entity types never get nameplates");
            writer.newLine();
            writer.write("# BL|entityTypeId");
            writer.newLine();
            for (String npc : blacklistedNpcs) {
                writer.write("BL|" + npc);
                writer.newLine();
            }
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to save admin config to %s", filePath);
        }
    }


    boolean isRequired(SegmentKey key) {
        return requiredSegments.contains(key);
    }

    void setRequired(SegmentKey key, boolean required) {
        if (required) {
            requiredSegments.add(key);
            disabledSegments.remove(key);
        } else {
            requiredSegments.remove(key);
        }
    }

    void clearAllRequired() {
        requiredSegments.clear();
    }

    boolean isDisabled(SegmentKey key) {
        return disabledSegments.contains(key);
    }

    boolean areAllSegmentsDisabled(Set<SegmentKey> keys) {
        if (disabledSegments.isEmpty()) return false;
        for (SegmentKey key : keys) {
            if (!disabledSegments.contains(key)) return false;
        }
        return true;
    }

    void setDisabled(SegmentKey key, boolean disabled) {
        if (disabled) {
            disabledSegments.add(key);
            requiredSegments.remove(key);
        } else {
            disabledSegments.remove(key);
        }
    }

    void clearAllDisabled() {
        disabledSegments.clear();
    }

    String getServerName() {
        return serverName;
    }

    void setServerName(String name) {
        this.serverName = name != null ? name : "";
    }

    String getDisplayServerName() {
        return serverName.isBlank() ? "NameplateBuilder" : serverName;
    }

    boolean isWelcomeMessagesEnabled() {
        return welcomeMessagesEnabled;
    }

    void setWelcomeMessagesEnabled(boolean enabled) {
        this.welcomeMessagesEnabled = enabled;
    }


    boolean isNpcChainLocked() {
        return npcChainLocked;
    }

    void setNpcChainLocked(boolean locked) {
        this.npcChainLocked = locked;
    }

    boolean isPlayerChainLocked() {
        return playerChainLocked;
    }

    void setPlayerChainLocked(boolean locked) {
        this.playerChainLocked = locked;
    }

    boolean isMasterEnabled() {
        return masterEnabled;
    }

    void setMasterEnabled(boolean enabled) {
        this.masterEnabled = enabled;
    }

    boolean isPlayerChainEnabled() {
        return playerChainEnabled;
    }

    void setPlayerChainEnabled(boolean enabled) {
        this.playerChainEnabled = enabled;
    }

    boolean isNpcChainEnabled() {
        return npcChainEnabled;
    }

    void setNpcChainEnabled(boolean enabled) {
        this.npcChainEnabled = enabled;
    }


    boolean isNamespaceEnabled(String namespace) {
        if (namespace == null || namespace.isEmpty() || "*".equals(namespace)) {
            return true;
        }
        return namespaceEnabled.getOrDefault(namespace, true);
    }

    void setNamespaceEnabled(String namespace, boolean enabled) {
        if (namespace != null && !namespace.isBlank()) {
            namespaceEnabled.put(namespace.trim(), enabled);
        }
    }

    boolean isWorldEnabled(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return true;
        }
        return worldEnabled.getOrDefault(worldName, true);
    }

    void setWorldEnabled(String worldName, boolean enabled) {
        if (worldName != null && !worldName.isBlank()) {
            worldEnabled.put(worldName.trim(), enabled);
        }
    }

    Map<String, Boolean> getWorldEnabled() {
        return Collections.unmodifiableMap(worldEnabled);
    }


    void trackNamespaceSegment(String namespace, String segmentId, boolean builtIn) {
        if (namespace == null || namespace.isEmpty() || "*".equals(namespace) || segmentId == null) {
            return;
        }
        segmentsByNamespace.computeIfAbsent(namespace, _ -> ConcurrentHashMap.newKeySet()).add(segmentId);
        if (!builtIn) {
            integratedNamespaces.add(namespace);
        }
    }

    Map<String, Set<String>> getSegmentsByNamespace() {
        return segmentsByNamespace;
    }

    void trackNamespaceDisplayName(String namespace, String displayName) {
        if (namespace != null && displayName != null && !displayName.isBlank()) {
            namespaceDisplayNames.putIfAbsent(namespace, displayName);
        }
    }

    String getNamespaceDisplayName(String namespace) {
        if (namespace == null) return "";
        String display = namespaceDisplayNames.get(namespace);
        return display != null ? display : formatNamespaceFallback(namespace);
    }

    private static String formatNamespaceFallback(String namespace) {
        if (namespace == null || namespace.isEmpty()) return namespace;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < namespace.length(); i++) {
            char character = namespace.charAt(i);
            if (character == '_' || character == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                if (Character.isUpperCase(character) && Character.isLowerCase(namespace.charAt(i - 1))) {
                    result.append(' ');
                }
                result.append(character);
            }
        }
        return result.toString();
    }


    Map<String, Set<String>> getSegmentProfiles() {
        Map<String, Set<String>> profiles = new LinkedHashMap<>();

        Set<String> vanillaSegments = segmentsByNamespace.getOrDefault("hytale", Set.of());
        if (!vanillaSegments.isEmpty()) {
            profiles.put("Vanilla", new HashSet<>(vanillaSegments));
        }

        for (String ns : integratedNamespaces) {
            if (ns == null || ns.isEmpty() || "hytale".equals(ns) || "nameplatebuilder".equals(ns)
                    || "npc".equals(ns) || ns.startsWith("_")) {
                continue;
            }
            Set<String> nsSegments = segmentsByNamespace.get(ns);
            if (nsSegments != null && !nsSegments.isEmpty()) {
                Set<String> combined = new HashSet<>(vanillaSegments);
                combined.addAll(nsSegments);
                String displayName = getNamespaceDisplayName(ns);
                profiles.put(displayName, combined);
            }
        }

        return profiles;
    }

    void prePopulateProfiles(Map<SegmentKey, NameplateRegistry.Segment> segments) {
        for (var entry : segments.entrySet()) {
            SegmentKey key = entry.getKey();
            NameplateRegistry.Segment seg = entry.getValue();
            if (seg.builtIn()) {
                trackNamespaceSegment("hytale", key.segmentId(), true);
            } else {
                String modNs = extractModName(seg.pluginId());
                if (modNs.isEmpty()) modNs = extractNamespace(seg.pluginId());
                trackNamespaceSegment(modNs, key.segmentId(), false);
                if (seg.pluginName() != null) {
                    trackNamespaceDisplayName(modNs, seg.pluginName());
                }
            }
        }
    }

    Set<String> getBlacklistedNpcs() {
        return Collections.unmodifiableSet(blacklistedNpcs);
    }

    boolean isNpcBlacklisted(String npcTypeId) {
        return blacklistedNpcs.contains(npcTypeId);
    }

    void addBlacklistedNpc(String npcTypeId) {
        if (npcTypeId != null && !npcTypeId.isBlank()) {
            blacklistedNpcs.add(npcTypeId.trim());
        }
    }

    void removeBlacklistedNpc(String npcTypeId) {
        blacklistedNpcs.remove(npcTypeId);
    }

    void clearBlacklistedNpcs() {
        blacklistedNpcs.clear();
    }

    boolean isBuiltInSegmentId(String segmentId) {
        return BUILT_IN_SEGMENT_IDS.contains(segmentId);
    }

    static String extractNamespace(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return "";
        }
        int colon = entityTypeId.indexOf(':');
        String ns = colon > 0 ? entityTypeId.substring(0, colon) : entityTypeId;
        return ns.toLowerCase(Locale.ROOT);
    }

    static String extractModName(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return "";
        }
        int colon = pluginId.indexOf(':');
        String name = colon >= 0 ? pluginId.substring(colon + 1) : pluginId;
        return name.toLowerCase(Locale.ROOT);
    }
}
