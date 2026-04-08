package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public final class NameplateAPI {

    private static volatile INameplateRegistry registry;
    private static volatile ComponentType<EntityStore, NameplateData> componentType;

    private NameplateAPI() {
    }

    public static void setRegistry(INameplateRegistry registry) {
        NameplateAPI.registry = registry;
    }

    public static void setComponentType(ComponentType<EntityStore, NameplateData> type) {
        NameplateAPI.componentType = type;
    }

    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName) {
        return define(plugin, segmentId, displayName, SegmentTarget.ALL, null);
    }

    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target) {
        return define(plugin, segmentId, displayName, target, null);
    }

    public static SegmentBuilder define(JavaPlugin plugin, String segmentId, String displayName, SegmentTarget target, String example) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(displayName, "displayName");
        requireNonNull(target, "target");
        return getRegistry().define(plugin, segmentId, displayName, target, example);
    }

    public static void undefine(JavaPlugin plugin, String segmentId) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        getRegistry().undefine(plugin, segmentId);
    }

    public static void defineVariants(JavaPlugin plugin, String segmentId, List<String> variantNames) {
        requireNonNull(plugin, "plugin");
        requireNonBlank(segmentId, "segmentId");
        requireNonNull(variantNames, "variantNames");
        if (variantNames.isEmpty()) {
            throw new NameplateArgumentException("variantNames", "'variantNames' must not be empty");
        }
        getRegistry().defineVariants(plugin, segmentId, variantNames);
    }

    public static void setText(Store<EntityStore> store,
                               Ref<EntityStore> entityRef,
                               String segmentId,
                               String text) {
        requireNonNull(store, "store");
        requireNonNull(entityRef, "entityRef");
        requireNonBlank(segmentId, "segmentId");
        requireNonBlank(text, "text", "must not be blank - use clearText() to clear");

        ComponentType<EntityStore, NameplateData> type = getComponentType();
        NameplateData data = store.getComponent(entityRef, type);
        if (data == null) {
            data = new NameplateData();
            data.setText(segmentId, text);
            store.addComponent(entityRef, type, data);
        } else {
            data.setText(segmentId, text);
        }
    }

    public static void clearText(Store<EntityStore> store,
                                 Ref<EntityStore> entityRef,
                                 String segmentId) {
        requireNonNull(store, "store");
        requireNonNull(entityRef, "entityRef");
        requireNonBlank(segmentId, "segmentId");

        ComponentType<EntityStore, NameplateData> type = getComponentType();
        NameplateData data = store.getComponent(entityRef, type);
        if (data != null) {
            data.removeText(segmentId);
            if (data.isEmpty()) {
                store.tryRemoveComponent(entityRef, type);
            }
        }
    }

    public static ComponentType<EntityStore, NameplateData> getComponentType() {
        ComponentType<EntityStore, NameplateData> current = componentType;
        if (current == null) {
            throw new NameplateNotInitializedException(
                    "NameplateData component not registered. "
                            + "Ensure NameplateBuilder is installed and your manifest.json declares "
                            + "\"Frotty27:NameplateBuilder\": \"*\" in Dependencies.");
        }
        return current;
    }

    static INameplateRegistry getRegistry() {
        INameplateRegistry current = registry;
        if (current == null) {
            throw new NameplateNotInitializedException(
                    "Nameplate API not initialized. "
                            + "Ensure NameplateBuilder is installed and your manifest.json declares "
                            + "\"Frotty27:NameplateBuilder\": \"*\" in Dependencies.");
        }
        return current;
    }

    private static void requireNonNull(Object value, String parameterName) {
        if (value == null) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' must not be null");
        }
    }

    private static void requireNonBlank(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' must not be null or blank");
        }
    }

    private static void requireNonBlank(String value, String parameterName, String detail) {
        if (value == null || value.isBlank()) {
            throw new NameplateArgumentException(parameterName,
                    "'" + parameterName + "' " + detail);
        }
    }
}
