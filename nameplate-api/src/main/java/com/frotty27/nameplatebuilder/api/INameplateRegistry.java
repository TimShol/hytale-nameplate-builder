package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.List;

public interface INameplateRegistry {

    default SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName) {
        return define(plugin, segmentId, displayName, SegmentTarget.ALL, null);
    }

    default SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        return define(plugin, segmentId, displayName, target, null);
    }

    SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example);

    void defineVariants(JavaPlugin plugin, String segmentId, List<String> variantNames);

    void undefine(JavaPlugin plugin, String segmentId);

    void undefineAll(JavaPlugin plugin);

    void override(JavaPlugin plugin, String segmentId, String description, SegmentResolver resolver);
}
