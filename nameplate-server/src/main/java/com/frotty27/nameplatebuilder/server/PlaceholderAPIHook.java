package com.frotty27.nameplatebuilder.server;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PlaceholderAPIHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CACHE_TTL_MS = 500;

    private static volatile boolean available = false;
    private static final Map<String, CachedParse> cache = new ConcurrentHashMap<>();

    private PlaceholderAPIHook() {
    }

    static void init() {
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            available = true;
            LOGGER.atInfo().log("PlaceholderAPI detected. Nameplate placeholder parsing enabled.");
        } catch (ClassNotFoundException _) {
            available = false;
        }
    }

    static void registerExpansion() {
        if (!available) {
            return;
        }
        try {
            new NpbExpansion().register();
            LOGGER.atInfo().log("Registered NameplateBuilder PlaceholderAPI expansion.");
        } catch (Throwable e) {
            LOGGER.atWarning().withCause(e).log("Failed to register PlaceholderAPI expansion.");
        }
    }

    static boolean isAvailable() {
        return available;
    }

    static String parse(String text, PlayerRef playerRef) {
        if (!available || text == null || playerRef == null) {
            return text;
        }
        if (!PlaceholderAPI.containsPlaceholders(text)) {
            return text;
        }

        String cacheKey = System.identityHashCode(playerRef) + ":" + text;
        CachedParse cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            return cached.result;
        }

        try {
            String result = PlaceholderAPI.setPlaceholders(playerRef, text);
            if (result != null) {
                cache.put(cacheKey, new CachedParse(result, now));
                return result;
            }
        } catch (Throwable _) {
        }
        return text;
    }

    static void cleanupCache() {
        if (cache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().timestamp) > CACHE_TTL_MS * 4);
    }

    private record CachedParse(String result, long timestamp) {
    }

    private static final class NpbExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "npb";
        }

        @Override
        public String getAuthor() {
            return "Frotty27";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getName() {
            return "NameplateBuilder";
        }

        @Override
        public List<String> getPlaceholders() {
            return List.of("health", "stamina", "mana", "entity_name", "player_name");
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(PlayerRef playerRef, String params) {
            return null;
        }
    }
}
