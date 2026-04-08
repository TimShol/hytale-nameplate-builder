package com.frotty27.nameplatebuilder.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class NameplateData implements Component<EntityStore> {

    public static final BuilderCodec<NameplateData> CODEC = BuilderCodec
            .builder(NameplateData.class, NameplateData::new)
            .append(
                    new com.hypixel.hytale.codec.KeyedCodec<>("NpbEntries", Codec.STRING),
                    NameplateData::deserializeEntries, NameplateData::serializeEntries
            )
            .add()
            .build();

    private final Map<String, String> entries;

    public NameplateData() {
        this.entries = new HashMap<>();
    }

    private NameplateData(Map<String, String> entries) {
        this.entries = new HashMap<>(entries);
    }

    public void setText(String segmentKey, String text) {
        if (text == null || text.isBlank()) {
            entries.remove(segmentKey);
        } else {
            entries.put(segmentKey, text);
        }
    }

    public String getText(String segmentKey) {
        return entries.get(segmentKey);
    }

    public void removeText(String segmentKey) {
        entries.remove(segmentKey);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Map<String, String> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public Map<String, String> getEntriesDirect() {
        return entries;
    }

    @Override
    public NameplateData clone() {
        return new NameplateData(this.entries);
    }

    private String serializeEntries() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(escapeEntry(entry.getKey()))
              .append('=')
              .append(escapeEntry(entry.getValue()));
        }
        return sb.toString();
    }

    private void deserializeEntries(String serialized) {
        entries.clear();
        if (serialized == null || serialized.isBlank()) {
            return;
        }
        String[] pairs = serialized.split(",");
        for (String pair : pairs) {
            int equalsSignIndex = pair.indexOf('=');
            if (equalsSignIndex < 0) {
                continue;
            }
            String key = unescapeEntry(pair.substring(0, equalsSignIndex));
            String value = unescapeEntry(pair.substring(equalsSignIndex + 1));
            if (!key.isBlank() && !value.isBlank()) {
                entries.put(key, value);
            }
        }
    }

    private static String escapeEntry(String s) {
        return s.replace("\\", "\\\\")
                .replace(",", "\\c")
                .replace("=", "\\e");
    }

    private static String unescapeEntry(String s) {
        return s.replace("\\e", "=")
                .replace("\\c", ",")
                .replace("\\\\", "\\");
    }
}
