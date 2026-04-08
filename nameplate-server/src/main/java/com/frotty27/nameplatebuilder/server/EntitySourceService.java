package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

final class EntitySourceService {

    enum SourceType { VANILLA, MOD }

    record EntitySource(SourceType type, String modName) {
        static final EntitySource VANILLA = new EntitySource(SourceType.VANILLA, null);
    }

    private final Map<Integer, EntitySource> cache = new ConcurrentHashMap<>();

    EntitySource getSource(NPCEntity npcEntity) {
        if (npcEntity == null) return EntitySource.VANILLA;
        try {
            int roleIndex = npcEntity.getRoleIndex();
            EntitySource cached = cache.get(roleIndex);
            if (cached != null) return cached;

            var builderInfo = NPCPlugin.get().getRoleBuilderInfo(roleIndex);
            if (builderInfo == null || builderInfo.getPath() == null) {
                cache.put(roleIndex, EntitySource.VANILLA);
                return EntitySource.VANILLA;
            }

            String pathStr = builderInfo.getPath().toString();

            if (pathStr.startsWith("/Server/") || pathStr.startsWith("\\Server\\")) {
                cache.put(roleIndex, EntitySource.VANILLA);
                return EntitySource.VANILLA;
            }

            String modName = extractModName(pathStr);
            EntitySource source = modName != null
                    ? new EntitySource(SourceType.MOD, modName)
                    : EntitySource.VANILLA;
            cache.put(roleIndex, source);
            return source;
        } catch (Throwable t) {
            return EntitySource.VANILLA;
        }
    }

    private static String extractModName(String pathStr) {
        int modsDirectoryIndex = indexOfMods(pathStr);
        if (modsDirectoryIndex < 0) return null;

        String afterMods = pathStr.substring(modsDirectoryIndex);
        int separatorIndex = firstSeparator(afterMods);
        if (separatorIndex <= 0) return null;
        return afterMods.substring(0, separatorIndex);
    }

    private static int indexOfMods(String path) {
        String[] patterns = {"mods/", "mods\\", "mods" + java.io.File.separator};
        for (String p : patterns) {
            int modsDirectoryIndex = path.indexOf(p);
            if (modsDirectoryIndex >= 0) return modsDirectoryIndex + p.length();
        }
        return -1;
    }

    private static int firstSeparator(String s) {
        int forwardSlashIndex = s.indexOf('/');
        int backslashIndex = s.indexOf('\\');
        int systemSeparatorIndex = s.indexOf(java.io.File.separatorChar);
        int minimumIndex = Integer.MAX_VALUE;
        if (forwardSlashIndex > 0 && forwardSlashIndex < minimumIndex) minimumIndex = forwardSlashIndex;
        if (backslashIndex > 0 && backslashIndex < minimumIndex) minimumIndex = backslashIndex;
        if (systemSeparatorIndex > 0 && systemSeparatorIndex < minimumIndex) minimumIndex = systemSeparatorIndex;
        return minimumIndex == Integer.MAX_VALUE ? -1 : minimumIndex;
    }

    void clearCache() {
        cache.clear();
    }
}
