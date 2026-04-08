package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.NameplateData;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.*;

final class NameplateBenchmarkCommand extends AbstractPlayerCommand {

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;

    NameplateBenchmarkCommand(NameplateRegistry registry,
                              NameplatePreferenceStore preferences,
                              AdminConfigStore adminConfig) {
        super("npbbench", "Run nameplate aggregator benchmark");
        setAllowsExtraArguments(true);
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
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

        String[] args = context.getInputString().trim().split("\\s+");
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /npbbench <players> <seconds>").color("#FFAA00"));
            player.sendMessage(Message.raw("Example: /npbbench 50 5").color("#AAAAAA"));
            return;
        }
        int viewerCount;
        int seconds;
        try {
            viewerCount = Integer.parseInt(args[1]);
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Usage: /npbbench <players> <seconds>").color("#FFAA00"));
            return;
        }
        viewerCount = Math.max(1, Math.min(500, viewerCount));
        seconds = Math.max(1, Math.min(30, seconds));
        int tickCount = seconds * 30;

        player.sendMessage(Message.raw("Starting benchmark...").color("#FFAA00"));

        UUID[] viewers = new UUID[viewerCount];
        for (int i = 0; i < viewerCount; i++) {
            viewers[i] = new UUID(0xBE_0000_0000_0000L, i);
        }

        ComponentType<EntityStore, NameplateData> nameplateDataType =
                com.frotty27.nameplatebuilder.api.NameplateAPI.getComponentType();

        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            player.sendMessage(Message.raw("No segments registered. Nothing to benchmark.").color("RED"));
            return;
        }

        Comparator<SegmentKey> comparator = Comparator
                .comparing((SegmentKey key) -> {
                    NameplateRegistry.Segment segment = segments.get(key);
                    return segment != null ? segment.pluginName() : key.pluginId();
                })
                .thenComparing(key -> {
                    NameplateRegistry.Segment segment = segments.get(key);
                    return segment != null ? segment.displayName() : key.segmentId();
                });

        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            player.sendMessage(Message.raw("No entity store available.").color("RED"));
            return;
        }

        Store<EntityStore> ecsStore = entityStore.getStore();
        List<Ref<EntityStore>> entities = new ArrayList<>();
        List<NameplateData> entityDataList = new ArrayList<>();

        ecsStore.forEachChunk((chunk, commandBuffer) -> {
            Archetype<EntityStore> archetype = chunk.getArchetype();
            boolean hasNameplateData = false;
            for (int i = 0; i < archetype.length(); i++) {
                if (archetype.get(i) == nameplateDataType) {
                    hasNameplateData = true;
                    break;
                }
            }
            if (!hasNameplateData) return;

            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                NameplateData data = ecsStore.getComponent(entityRef, nameplateDataType);
                if (data != null) {
                    entities.add(entityRef);
                    entityDataList.add(data);
                }
            }
        });

        int entityCount = entities.size();
        if (entityCount == 0) {
            player.sendMessage(Message.raw("No entities with nameplates found in this world.").color("RED"));
            return;
        }

        List<SegmentKey> allKeys = new ArrayList<>(segments.keySet());
        Random random = new Random(42);
        int defaultCount = 0, customOrderCount = 0, partialDisableCount = 0, heavyCustomCount = 0;
        for (int i = 0; i < viewerCount; i++) {
            UUID viewerUuid = viewers[i];
            double roll = (double) i / viewerCount;
            if (roll < 0.40) {
                defaultCount++;
            } else if (roll < 0.70) {
                customOrderCount++;
                for (int s = 0; s < allKeys.size(); s++) {
                    SegmentKey key = allKeys.get(s);
                    preferences.enable(viewerUuid, "_npcs", key);
                }
                for (int m = 0; m < 3; m++) {
                    SegmentKey key = allKeys.get(random.nextInt(allKeys.size()));
                    preferences.move(viewerUuid, "_npcs", key, random.nextInt(3) - 1, allKeys, comparator);
                }
            } else if (roll < 0.90) {
                partialDisableCount++;
                for (SegmentKey key : allKeys) {
                    if (random.nextDouble() < 0.3) {
                        preferences.disable(viewerUuid, "_npcs", key);
                    } else {
                        preferences.enable(viewerUuid, "_npcs", key);
                    }
                }
            } else {
                heavyCustomCount++;
                for (SegmentKey key : allKeys) {
                    if (random.nextDouble() < 0.4) {
                        preferences.disable(viewerUuid, "_npcs", key);
                    } else {
                        preferences.enable(viewerUuid, "_npcs", key);
                    }
                    int id = key.id();
                    if (id >= 0) {
                        var prefs = preferences.getSetDirect(viewerUuid, "_npcs");
                        if (prefs != null) {
                            prefs.ensureCapacity(id);
                            if (random.nextDouble() < 0.3) prefs.prefix[id] = "[";
                            if (random.nextDouble() < 0.3) prefs.suffix[id] = "]";
                            if (random.nextDouble() < 0.2) prefs.selectedVariant[id] = 1;
                        }
                    }
                }
                for (int m = 0; m < 5; m++) {
                    SegmentKey key = allKeys.get(random.nextInt(allKeys.size()));
                    preferences.move(viewerUuid, "_npcs", key, random.nextInt(5) - 2, allKeys, comparator);
                }
            }
        }
        preferences.invalidateChainCache();

        player.sendMessage(Message.raw(String.format(
                "Viewers: %d default, %d custom order, %d partial disable, %d heavy custom",
                defaultCount, customOrderCount, partialDisableCount, heavyCustomCount)).color("#AAAAAA"));

        player.sendMessage(Message.raw(String.format(
                "Benchmarking: %d entities x %d viewers x %ds (%d ticks) = %,d buildText() calls",
                entityCount, viewerCount, seconds, tickCount,
                (long) entityCount * viewerCount * tickCount)).color("#AAAAAA"));

        long resolverTimeNs = 0;
        long buildTextTimeNs = 0;
        long filterTimeNs = 0;
        int totalCalls = 0;

        for (int tick = 0; tick < tickCount; tick++) {
            for (int entityIndex = 0; entityIndex < entityCount; entityIndex++) {
                Ref<EntityStore> entityRef = entities.get(entityIndex);
                NameplateData entityData = entityDataList.get(entityIndex);

                long filterStart = System.nanoTime();

                List<SegmentKey> available = new ArrayList<>();
                for (String entryKey : entityData.getEntriesDirect().keySet()) {
                    if (entryKey.charAt(0) == '_' || entryKey.indexOf('.') >= 0) continue;
                    SegmentKey matched = registry.findBySegmentId(entryKey);
                    if (matched != null && !adminConfig.isDisabled(matched)) {
                        available.add(matched);
                    }
                }

                for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
                    if (entry.getValue().resolver() == null) continue;
                    if (adminConfig.isDisabled(entry.getKey())) continue;
                    if (available.contains(entry.getKey())) continue;
                    available.add(entry.getKey());
                }

                filterTimeNs += System.nanoTime() - filterStart;

                for (int viewerIndex = 0; viewerIndex < viewerCount; viewerIndex++) {
                    UUID viewerUuid = viewers[viewerIndex];

                    long buildStart = System.nanoTime();

                    List<SegmentKey> chain = preferences.getChain(viewerUuid, "_npcs", available, comparator);

                    var prefs = preferences.getSetDirect(viewerUuid, "_npcs");
                    String defaultSep = prefs != null ? prefs.separator : " - ";
                    StringBuilder builder = new StringBuilder();
                    SegmentKey previousKey = null;

                    for (int segmentIndex = 0, segmentCount = chain.size(); segmentIndex < segmentCount; segmentIndex++) {
                        SegmentKey key = chain.get(segmentIndex);
                        if (adminConfig.isDisabled(key)) continue;
                        int id = key.id();
                        if (!adminConfig.isRequired(key)) {
                            boolean disabled = prefs != null && id >= 0 && id < prefs.disabled.length && prefs.disabled[id];
                            if (disabled) continue;
                        }

                        int variantIndex = prefs != null && id >= 0 && id < prefs.selectedVariant.length ? prefs.selectedVariant[id] : 0;
                        String segmentId = key.segmentId();
                        String text;
                        if (variantIndex > 0) {
                            String variantText = entityData.getText(segmentId + "." + variantIndex);
                            text = variantText != null && !variantText.isBlank() ? variantText : entityData.getText(segmentId);
                        } else {
                            text = entityData.getText(segmentId);
                        }

                        if (text == null || text.isBlank()) {
                            NameplateRegistry.Segment segment = segments.get(key);
                            if (segment != null && segment.resolver() != null) {
                                long resolverStart = System.nanoTime();
                                try {
                                    text = segment.resolver().resolve(ecsStore, entityRef, variantIndex);
                                } catch (Exception ignored) {
                                    text = null;
                                }
                                resolverTimeNs += System.nanoTime() - resolverStart;
                            }
                        }

                        if (text == null || text.isBlank()) continue;

                        if (!builder.isEmpty() && previousKey != null) {
                            int previousKeyId = previousKey.id();
                            String sep = prefs != null && previousKeyId >= 0 && previousKeyId < prefs.separatorAfter.length && prefs.separatorAfter[previousKeyId] != null
                                    ? prefs.separatorAfter[previousKeyId] : defaultSep;
                            builder.append(sep);
                        }

                        String prefix = prefs != null && id >= 0 && id < prefs.prefix.length && prefs.prefix[id] != null ? prefs.prefix[id] : "";
                        String suffix = prefs != null && id >= 0 && id < prefs.suffix.length && prefs.suffix[id] != null ? prefs.suffix[id] : "";
                        if (!prefix.isEmpty()) builder.append(prefix);
                        builder.append(text);
                        if (!suffix.isEmpty()) builder.append(suffix);

                        previousKey = key;
                    }

                    String result = builder.toString();

                    buildTextTimeNs += System.nanoTime() - buildStart;
                    totalCalls++;
                }
            }
        }

        double totalMs = (filterTimeNs + buildTextTimeNs) / 1_000_000.0;
        double avgPerTick = totalMs / tickCount;
        double resolverMs = resolverTimeNs / 1_000_000.0;
        double buildMs = buildTextTimeNs / 1_000_000.0;
        double filterMs = filterTimeNs / 1_000_000.0;
        double tickBudget = 1000.0 / 30.0;
        double tickPercent = (avgPerTick / tickBudget) * 100.0;

        player.sendMessage(Message.raw("--- NPB Benchmark Results ---").color("#55FF55"));
        player.sendMessage(Message.raw(String.format(
                "Entities: %d | Viewers: %d | Ticks: %d", entityCount, viewerCount, tickCount)).color("#AAAAAA"));
        player.sendMessage(Message.raw(String.format(
                "Total: %.1fms | Avg: %.2fms/tick", totalMs, avgPerTick)).color("#FFFFFF"));
        player.sendMessage(Message.raw(String.format(
                "Breakdown: resolvers %.2fms | buildText %.2fms | filtering %.2fms",
                resolverMs / tickCount, buildMs / tickCount, filterMs / tickCount)).color("#AAAAAA"));
        player.sendMessage(Message.raw(String.format(
                "Tick budget: %.1fms (30 TPS) | NPB usage: %.1f%%", tickBudget, tickPercent))
                .color(tickPercent < 20 ? "#55FF55" : tickPercent < 50 ? "#FFAA00" : "#FF5555"));
        player.sendMessage(Message.raw(String.format(
                "Per entity-viewer: %.3fus | Total buildText calls: %,d",
                (buildTextTimeNs / 1000.0) / totalCalls, totalCalls)).color("#AAAAAA"));

        preferences.removeFakeViewers(viewers);
    }
}
