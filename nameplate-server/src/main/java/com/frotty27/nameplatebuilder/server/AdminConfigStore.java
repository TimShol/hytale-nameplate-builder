package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class AdminConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static String pluginVersion = "unknown";

    static void setPluginVersion(String version) { pluginVersion = version; }

    private final AtomicInteger configVersion = new AtomicInteger(0);

    int getConfigVersion() { return configVersion.get(); }
    private void bumpVersion() { configVersion.incrementAndGet(); }

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
    private final Set<String> blacklistPatterns = ConcurrentHashMap.newKeySet();
    private volatile Pattern[] compiledPatterns = new Pattern[0];
    private final Map<String, Boolean> entitySourceDefaults = new ConcurrentHashMap<>();


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
        blacklistPatterns.clear();
        compiledPatterns = new Pattern[0];
        entitySourceDefaults.clear();
        serverName = "";
        welcomeMessagesEnabled = false;
        npcChainLocked = false;
        playerChainLocked = false;
        masterEnabled = true;
        playerChainEnabled = true;
        npcChainEnabled = true;

        if (Files.exists(filePath)) {
            loadJson(filePath);
            return;
        }

        Path legacyPath = filePath.getParent().resolve("admin_config.txt");
        if (Files.exists(legacyPath)) {
            LOGGER.atInfo().log("Migrating admin config from %s to %s", legacyPath, filePath);
            loadLegacy(legacyPath);
            seedDefaultPatterns();
            save();
            try {
                Files.delete(legacyPath);
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to delete legacy config %s", legacyPath);
            }
            return;
        }

        seedDefaultPatterns();
        save();
    }

    private void seedDefaultPatterns() {
        if (blacklistPatterns.isEmpty()) {
            addBlacklistPattern("Citizen.*");
            addBlacklistPattern("Mount_.*");
            addBlacklistPattern("Pet_.*");
        }
    }


    private void loadJson(Path path) {
        try {
            String content = Files.readString(path);
            Map<String, Object> root = SimpleJson.parseObject(content);
            if (root == null) return;

            serverName = SimpleJson.getString(root, "serverName", "");
            welcomeMessagesEnabled = SimpleJson.getBoolean(root, "welcomeMessagesEnabled", false);
            masterEnabled = SimpleJson.getBoolean(root, "masterEnabled", true);
            playerChainEnabled = SimpleJson.getBoolean(root, "playerChainEnabled", true);
            npcChainEnabled = SimpleJson.getBoolean(root, "npcChainEnabled", true);
            npcChainLocked = SimpleJson.getBoolean(root, "npcChainLocked", false);
            playerChainLocked = SimpleJson.getBoolean(root, "playerChainLocked", false);

            Map<String, Boolean> nsMap = SimpleJson.getBooleanMap(root, "namespaceEnabled");
            namespaceEnabled.putAll(nsMap);

            Map<String, Boolean> weMap = SimpleJson.getBooleanMap(root, "worldEnabled");
            worldEnabled.putAll(weMap);

            List<String> reqList = SimpleJson.getStringList(root, "requiredSegments");
            for (String entry : reqList) {
                int pipeIndex = entry.indexOf('|');
                if (pipeIndex > 0 && pipeIndex < entry.length() - 1) {
                    requiredSegments.add(new SegmentKey(entry.substring(0, pipeIndex), entry.substring(pipeIndex + 1)));
                }
            }

            List<String> disList = SimpleJson.getStringList(root, "disabledSegments");
            for (String entry : disList) {
                int pipeIndex = entry.indexOf('|');
                if (pipeIndex > 0 && pipeIndex < entry.length() - 1) {
                    disabledSegments.add(new SegmentKey(entry.substring(0, pipeIndex), entry.substring(pipeIndex + 1)));
                }
            }

            List<String> blList = SimpleJson.getStringList(root, "blacklistedNpcs");
            blacklistedNpcs.addAll(blList);

            List<String> patList = SimpleJson.getStringList(root, "blacklistPatterns");
            for (String pat : patList) {
                blacklistPatterns.add(pat);
            }
            recompilePatterns();

            Map<String, Boolean> esMap = SimpleJson.getBooleanMap(root, "entitySourceDefaults");
            entitySourceDefaults.putAll(esMap);

        } catch (IOException | RuntimeException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to load admin config from %s", path);
        }
    }


    private void loadLegacy(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
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
                    case "BL" -> blacklistedNpcs.add(parts[1]);
                    case "ES" -> {
                        if (parts.length >= 3) {
                            entitySourceDefaults.put(parts[1].trim().toLowerCase(Locale.ROOT), Boolean.parseBoolean(parts[2]));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to load legacy admin config from %s", path);
        }
    }


    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to create admin config directory");
        }

        Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");

        SimpleJson.Writer w = new SimpleJson.Writer();
        w.beginObject();
        w.keyValue("version", pluginVersion);
        w.keyValue("serverName", serverName);
        w.keyValue("welcomeMessagesEnabled", welcomeMessagesEnabled);
        w.keyValue("masterEnabled", masterEnabled);
        w.keyValue("playerChainEnabled", playerChainEnabled);
        w.keyValue("npcChainEnabled", npcChainEnabled);
        w.keyValue("npcChainLocked", npcChainLocked);
        w.keyValue("playerChainLocked", playerChainLocked);
        w.keyBooleanMap("namespaceEnabled", namespaceEnabled);
        w.keyBooleanMap("worldEnabled", worldEnabled);

        w.key("requiredSegments");
        w.beginArray();
        for (SegmentKey key : requiredSegments) {
            w.value(key.pluginId() + "|" + key.segmentId());
        }
        w.endArray();

        w.key("disabledSegments");
        w.beginArray();
        for (SegmentKey key : disabledSegments) {
            w.value(key.pluginId() + "|" + key.segmentId());
        }
        w.endArray();

        w.keyStringArray("blacklistedNpcs", blacklistedNpcs);
        w.keyStringArray("blacklistPatterns", blacklistPatterns);
        w.keyBooleanMap("entitySourceDefaults", entitySourceDefaults);
        w.endObject();

        try {
            Files.writeString(tempPath, w.toString());
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to save admin config to %s", filePath);
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
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
            bumpVersion();
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

        for (String namespace : integratedNamespaces) {
            if (namespace == null || namespace.isEmpty() || "hytale".equals(namespace) || "nameplatebuilder".equals(namespace)
                    || "npc".equals(namespace) || namespace.startsWith("_")) {
                continue;
            }
            Set<String> nsSegments = segmentsByNamespace.get(namespace);
            if (nsSegments != null && !nsSegments.isEmpty()) {
                Set<String> combined = new HashSet<>(vanillaSegments);
                combined.addAll(nsSegments);
                String displayName = getNamespaceDisplayName(namespace);
                profiles.put(displayName, combined);
            }
        }

        return profiles;
    }

    void prePopulateProfiles(Map<SegmentKey, NameplateRegistry.Segment> segments) {
        for (var entry : segments.entrySet()) {
            SegmentKey key = entry.getKey();
            NameplateRegistry.Segment segment = entry.getValue();
            if (segment.builtIn()) {
                trackNamespaceSegment("hytale", key.segmentId(), true);
            } else {
                String modNamespace = extractModName(segment.pluginId());
                if (modNamespace.isEmpty()) modNamespace = extractNamespace(segment.pluginId());
                trackNamespaceSegment(modNamespace, key.segmentId(), false);
                if (segment.pluginName() != null) {
                    trackNamespaceDisplayName(modNamespace, segment.pluginName());
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


    Set<String> getBlacklistPatterns() {
        return Collections.unmodifiableSet(blacklistPatterns);
    }

    void addBlacklistPattern(String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            String trimmed = pattern.trim();
            if (blacklistPatterns.add(trimmed)) {
                recompilePatterns();
                bumpVersion();
            }
        }
    }

    void removeBlacklistPattern(String pattern) {
        if (blacklistPatterns.remove(pattern)) {
            recompilePatterns();
            bumpVersion();
        }
    }

    void clearBlacklistPatterns() {
        blacklistPatterns.clear();
        compiledPatterns = new Pattern[0];
        bumpVersion();
    }

    boolean matchesBlacklistPattern(String roleName) {
        Pattern[] patterns = compiledPatterns;
        if (patterns.length == 0) return false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(roleName).matches()) {
                return true;
            }
        }
        return false;
    }

    private void recompilePatterns() {
        List<Pattern> compiled = new ArrayList<>();
        for (String pat : blacklistPatterns) {
            try {
                compiled.add(Pattern.compile(pat));
            } catch (PatternSyntaxException e) {
                LOGGER.atWarning().log("Invalid blacklist pattern '%s': %s", pat, e.getMessage());
            }
        }
        compiledPatterns = compiled.toArray(new Pattern[0]);
    }


    boolean isEntitySourceDefaultsEnabled(String modName) {
        if (modName == null) return true;
        return entitySourceDefaults.getOrDefault(modName.toLowerCase(Locale.ROOT), true);
    }

    void autoPopulateEntitySourceDefault(String modName, boolean isIntegrated) {
        String normalizedModName = modName.toLowerCase(Locale.ROOT);
        if (!entitySourceDefaults.containsKey(normalizedModName)) {
            entitySourceDefaults.put(normalizedModName, isIntegrated);
        } else if (isIntegrated && !entitySourceDefaults.getOrDefault(normalizedModName, false)) {
            entitySourceDefaults.put(normalizedModName, true);
        }
    }

    static String extractNamespace(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return "";
        }
        int colonIndex = entityTypeId.indexOf(':');
        String namespace = colonIndex > 0 ? entityTypeId.substring(0, colonIndex) : entityTypeId;
        return namespace.toLowerCase(Locale.ROOT);
    }

    static String extractModName(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return "";
        }
        int colonIndex = pluginId.indexOf(':');
        String name = colonIndex >= 0 ? pluginId.substring(colonIndex + 1) : pluginId;
        return name.toLowerCase(Locale.ROOT);
    }
}
