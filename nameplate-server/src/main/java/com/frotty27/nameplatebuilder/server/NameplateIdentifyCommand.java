package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.jspecify.annotations.NonNull;

import java.util.*;

final class NameplateIdentifyCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double SCAN_RADIUS_SQUARED = 30.0 * 30.0;

    private final NameplateRegistry registry;

    NameplateIdentifyCommand(NameplateRegistry registry) {
        super("npbidentify", "Scan nearby entities and log identity report");
        this.registry = registry;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        if (!context.sender().hasPermission(NameplateBuilderCommand.PERMISSION_ADMIN)) {
            context.sendMessage(Message.raw("No permission.").color("RED"));
            return;
        }

        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players.").color("RED"));
            return;
        }

        Vector3d playerPos;
        try {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            if (playerTransform == null) {
                player.sendMessage(Message.raw("[NameplateBuilder] Could not get your position.").color("#FF5555"));
                return;
            }
            playerPos = playerTransform.getPosition();
        } catch (Throwable throwable) {
            player.sendMessage(Message.raw("[NameplateBuilder] Error reading position: " + throwable.getMessage()).color("#FF5555"));
            return;
        }

        player.sendMessage(Message.raw("[NameplateBuilder] Scanning entities within 30 blocks...").color("#FFAA00"));

        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            player.sendMessage(Message.raw("[NameplateBuilder] No entity store available.").color("#FF5555"));
            return;
        }

        Store<EntityStore> ecsStore = entityStore.getStore();

        int[] total = {0};
        int[] vanillaCount = {0};
        int[] moddedCount = {0};
        int[] modRegisteredCount = {0};
        int[] playerCount = {0};

        ecsStore.forEachChunk((chunk, commandBuffer) -> {
            Archetype<EntityStore> archetype = chunk.getArchetype();

            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);

                try {
                    TransformComponent entityTransform = ecsStore.getComponent(entityRef, TransformComponent.getComponentType());
                    if (entityTransform == null) continue;
                    Vector3d entityPos = entityTransform.getPosition();
                    if (entityPos == null) continue;

                    double deltaX = entityPos.getX() - playerPos.getX();
                    double deltaY = entityPos.getY() - playerPos.getY();
                    double deltaZ = entityPos.getZ() - playerPos.getZ();
                    double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

                    if (distanceSquared > SCAN_RADIUS_SQUARED) continue;

                    total[0]++;

                    LOGGER.atInfo().log("[NPB-IDENTIFY] === Entity Report ===");

                    try {
                        String javaClass = "N/A";
                        for (int c = 0; c < archetype.length(); c++) {
                            ComponentType<EntityStore, ?> ct = archetype.get(c);
                            Class<?> cls = ct.getTypeClass();
                            if (cls != null) {
                                javaClass = cls.getName();
                                break;
                            }
                        }
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Java Class: %s", javaClass);
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Java Class: N/A");
                    }

                    boolean isNpc = false;
                    boolean isPlayer = false;
                    NPCEntity npcEntity = null;

                    try {
                        npcEntity = ecsStore.getComponent(entityRef, NPCEntity.getComponentType());
                        isNpc = npcEntity != null;
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Is NPC: N/A");
                    }

                    try {
                        Player playerComp = ecsStore.getComponent(entityRef, Player.getComponentType());
                        isPlayer = playerComp != null;
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Is Player: N/A");
                    }

                    LOGGER.atInfo().log("[NPB-IDENTIFY] Is NPC: %s", isNpc);
                    LOGGER.atInfo().log("[NPB-IDENTIFY] Is Player: %s", isPlayer);

                    try {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Position: %.2f, %.2f, %.2f",
                                entityPos.getX(), entityPos.getY(), entityPos.getZ());
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Position: N/A");
                    }

                    String namespace = "";
                    String roleSourceMod = null;
                    if (isNpc && npcEntity != null) {
                        String typeId = "";
                        try {
                            typeId = npcEntity.getNPCTypeId();
                            LOGGER.atInfo().log("[NPB-IDENTIFY] --- Layer 1: Entity Type ID ---");
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Type ID: %s", typeId);
                            boolean hasColon = typeId.contains(":");
                            namespace = hasColon ? AdminConfigStore.extractNamespace(typeId) : "";
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Namespace: %s", hasColon ? namespace : "(vanilla - no namespace)");
                            boolean isVanilla = !hasColon;
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Is Vanilla Class: %s", isVanilla);
                        } catch (Throwable throwable) {
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Type ID: N/A");
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Namespace: N/A");
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Is Vanilla Class: N/A");
                        }

                        int roleIndex = -1;
                        try {
                            String roleName = npcEntity.getRoleName();
                            roleIndex = npcEntity.getRoleIndex();
                            LOGGER.atInfo().log("[NPB-IDENTIFY] --- Layer 2: Role ---");
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Current Role Name: %s", roleName);
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Role Index: %d", roleIndex);
                        } catch (Throwable throwable) {
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Current Role Name: N/A");
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Role Index: N/A");
                        }

                        try {
                            var builderInfo = NPCPlugin.get().getRoleBuilderInfo(npcEntity.getRoleIndex());
                            if (builderInfo != null) {
                                String pathStr = builderInfo.getPath() != null ? builderInfo.getPath().toString() : "";
                                LOGGER.atInfo().log("[NPB-IDENTIFY] Role Builder Path: %s", pathStr);
                                LOGGER.atInfo().log("[NPB-IDENTIFY] Role Builder Key: %s", builderInfo.getKeyName());

                                boolean isVanillaPath = pathStr.startsWith("/Server/") || pathStr.startsWith("\\Server\\");
                                if (!isVanillaPath && !pathStr.isEmpty()) {
                                    int modsDirectoryIndex = pathStr.indexOf("mods" + java.io.File.separator);
                                    if (modsDirectoryIndex < 0) modsDirectoryIndex = pathStr.indexOf("mods/");
                                    if (modsDirectoryIndex < 0) modsDirectoryIndex = pathStr.indexOf("mods\\");
                                    if (modsDirectoryIndex >= 0) {
                                        String afterMods = pathStr.substring(modsDirectoryIndex + 5);
                                        int separatorIndex = afterMods.indexOf(java.io.File.separator);
                                        if (separatorIndex < 0) separatorIndex = afterMods.indexOf('/');
                                        if (separatorIndex < 0) separatorIndex = afterMods.indexOf('\\');
                                        if (separatorIndex > 0) {
                                            roleSourceMod = afterMods.substring(0, separatorIndex);
                                        }
                                    }
                                }
                                LOGGER.atInfo().log("[NPB-IDENTIFY] Role Source: %s",
                                        isVanillaPath ? "VANILLA (core asset pack)" :
                                        roleSourceMod != null ? "MOD: " + roleSourceMod :
                                        "UNKNOWN (non-vanilla path)");

                                try {
                                    var bm = NPCPlugin.get().getBuilderManager();
                                    for (var field : bm.getClass().getDeclaredFields()) {
                                        if (field.getType().getName().contains("AssetPack")
                                                || field.getType().getName().contains("assetPack")) {
                                            LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] BuilderManager has field: %s (%s)",
                                                    field.getName(), field.getType().getName());
                                        }
                                    }
                                    for (var field : builderInfo.getClass().getDeclaredFields()) {
                                        if (field.getType().getName().contains("AssetPack")
                                                || field.getType().getName().contains("Pack")
                                                || field.getName().toLowerCase().contains("pack")) {
                                            LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] BuilderInfo has field: %s (%s)",
                                                    field.getName(), field.getType().getName());
                                        }
                                    }
                                } catch (Throwable throwable) {
                                    LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] Reflection probe: %s", throwable.getMessage());
                                }

                                try {
                                    String roleKey = builderInfo.getKeyName();
                                    var commonAsset = com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.getByName(roleKey);
                                    if (commonAsset != null) {
                                        LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] CommonAssetRegistry has role '%s': %s", roleKey, commonAsset);
                                    } else {
                                        LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] CommonAssetRegistry: role '%s' NOT found", roleKey);
                                    }
                                } catch (Throwable throwable) {
                                    LOGGER.atInfo().log("[NPB-IDENTIFY-DISCOVERY] CommonAssetRegistry probe: %s", throwable.getMessage());
                                }

                                try {
                                    boolean isVanillaSource = pathStr.startsWith("/Server/") || pathStr.startsWith("\\Server\\");
                                    String detectedModName = null;
                                    if (!isVanillaSource) {
                                        int modsDirectoryIndex = pathStr.indexOf("mods/");
                                        if (modsDirectoryIndex < 0) modsDirectoryIndex = pathStr.indexOf("mods\\");
                                        if (modsDirectoryIndex < 0) modsDirectoryIndex = pathStr.indexOf("mods" + java.io.File.separator);
                                        if (modsDirectoryIndex >= 0) {
                                            String afterModsSegment = pathStr.substring(modsDirectoryIndex + 5);
                                            int separatorIndex = afterModsSegment.indexOf('/');
                                            if (separatorIndex < 0) separatorIndex = afterModsSegment.indexOf('\\');
                                            if (separatorIndex > 0) detectedModName = afterModsSegment.substring(0, separatorIndex);
                                        }
                                    }

                                    roleSourceMod = detectedModName;
                                    String entitySourceStr = isVanillaSource ? "VANILLA" : ("MOD:" + (detectedModName != null ? detectedModName : "unknown"));
                                    LOGGER.atInfo().log("[NPB-IDENTIFY] Entity Source: %s", entitySourceStr);

                                    if (detectedModName != null) {
                                        boolean isIntegrated = false;
                                        for (var segEntry : registry.getSegments().entrySet()) {
                                            String segPluginId = segEntry.getValue().pluginId();
                                            if (segPluginId != null && segPluginId.toLowerCase().contains(detectedModName.toLowerCase())) {
                                                isIntegrated = true;
                                                break;
                                            }
                                        }
                                        LOGGER.atInfo().log("[NPB-IDENTIFY] Mod Integrated: %s", isIntegrated ? "YES (registered segments)" : "NO");
                                        LOGGER.atInfo().log("[NPB-IDENTIFY] NPB Defaults Shown: %s",
                                            isIntegrated ? "YES (integrated mod)" : "NO (non-integrated mod)");
                                    } else if (isVanillaSource) {
                                        LOGGER.atInfo().log("[NPB-IDENTIFY] NPB Defaults Shown: YES (vanilla entity)");
                                    }
                                } catch (Throwable throwable) {
                                    LOGGER.atInfo().log("[NPB-IDENTIFY] Entity Source Detection: ERROR - %s", throwable.getMessage());
                                }
                            }
                        } catch (Throwable throwable) {
                            LOGGER.atInfo().log("[NPB-IDENTIFY] Role Builder Info: ERROR - %s", throwable.getMessage());
                        }
                    }

                    LOGGER.atInfo().log("[NPB-IDENTIFY] --- Layer 3: All Components ---");
                    try {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Component count: %d", archetype.length());
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Component count: N/A");
                    }

                    List<String> componentClassNames = new ArrayList<>();
                    int nullSlots = 0;
                    for (int c = 0; c < archetype.length(); c++) {
                        try {
                            ComponentType<EntityStore, ?> ct = archetype.get(c);
                            if (ct == null) { nullSlots++; continue; }
                            Class<?> cls = ct.getTypeClass();
                            String className = cls != null ? cls.getName() : "unknown";
                            componentClassNames.add(className);
                            LOGGER.atInfo().log("[NPB-IDENTIFY]   [%d] %s", c, className);
                        } catch (Throwable throwable) {
                            nullSlots++;
                        }
                    }
                    if (nullSlots > 0) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY]   (%d slots with no type class - internal/opaque components)", nullSlots);
                    }

                    boolean hasNameplateData = false;
                    boolean hasRPGMobsTier = false;

                    try {
                        var nameplateDataType = com.frotty27.nameplatebuilder.api.NameplateAPI.getComponentType();
                        var data = ecsStore.getComponent(entityRef, nameplateDataType);
                        if (data != null) {
                            hasNameplateData = true;
                            LOGGER.atInfo().log("[NPB-IDENTIFY] NameplateData: PRESENT (entries: %d)", data.getEntriesDirect().size());
                        } else {
                            LOGGER.atInfo().log("[NPB-IDENTIFY] NameplateData: absent");
                        }
                    } catch (Throwable throwable) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] NameplateData: ERROR - %s", throwable.getMessage());
                    }

                    Map<String, List<String>> componentsByMod = new LinkedHashMap<>();
                    for (String className : componentClassNames) {
                        if (className.startsWith("com.hypixel.") || className.startsWith("java.")) continue;
                        if (className.startsWith("com.frotty27.nameplatebuilder.")) continue;
                        String[] parts = className.split("\\.");
                        String modIdentifier = parts.length >= 3 ? parts[2] : className;
                        if (parts.length >= 4 && (parts[2].equals("frotty27") || parts[2].equals("hypixel"))) {
                            modIdentifier = parts[3];
                        }
                        String shortName = className.substring(className.lastIndexOf('.') + 1);
                        componentsByMod.computeIfAbsent(modIdentifier, _ -> new ArrayList<>()).add(shortName);
                    }

                    for (var modEntry : componentsByMod.entrySet()) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] %s components (%d): %s",
                                modEntry.getKey(), modEntry.getValue().size(),
                                String.join(", ", modEntry.getValue()));
                    }
                    if (componentsByMod.isEmpty()) {
                        LOGGER.atInfo().log("[NPB-IDENTIFY] Mod components: none");
                    }

                    String classification;
                    List<String> modifyingMods = new ArrayList<>();
                    if (roleSourceMod != null) {
                        modifyingMods.add(roleSourceMod);
                    }
                    for (String modIdentifier : componentsByMod.keySet()) {
                        if (!modifyingMods.contains(modIdentifier)) {
                            modifyingMods.add(modIdentifier + " (components only)");
                        }
                    }

                    if (!isNpc && isPlayer) {
                        classification = "PLAYER";
                        playerCount[0]++;
                    } else if (!namespace.isEmpty()) {
                        classification = "MOD_ENTITY:" + namespace;
                        modRegisteredCount[0]++;
                    } else if (roleSourceMod != null) {
                        classification = "MOD_ROLE:" + roleSourceMod;
                        moddedCount[0]++;
                    } else if (!componentsByMod.isEmpty()) {
                        classification = "VANILLA_WITH_MOD_COMPONENTS:" + String.join(",", componentsByMod.keySet());
                        vanillaCount[0]++;
                    } else {
                        classification = "VANILLA_UNMODIFIED";
                        vanillaCount[0]++;
                    }
                    LOGGER.atInfo().log("[NPB-IDENTIFY] Classification: %s", classification);

                } catch (Throwable throwable) {
                    LOGGER.atInfo().log("[NPB-IDENTIFY] Entity scan error: %s", throwable.getMessage());
                }
            }
        });

        player.sendMessage(Message.raw(String.format(
                "[NameplateBuilder] Scanned %d entities within 30 blocks: %d vanilla, %d modded, %d mod-registered, %d players",
                total[0], vanillaCount[0], moddedCount[0], modRegisteredCount[0], playerCount[0])).color("#55FF55"));
    }
}
