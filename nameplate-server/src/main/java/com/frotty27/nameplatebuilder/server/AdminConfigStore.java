package com.frotty27.nameplatebuilder.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the server-wide admin configuration for required segments.
 *
 * <p>Required segments are always displayed on every player's nameplate,
 * regardless of their personal preferences. This lets server owners enforce
 * visibility of certain segments (e.g. rank, guild tag).</p>
 *
 * <p>Persisted as a simple text file with one line per required segment:
 * {@code R|pluginId|segmentId}</p>
 */
final class AdminConfigStore {

    private final Path filePath;
    private final Set<SegmentKey> requiredSegments = ConcurrentHashMap.newKeySet();

    AdminConfigStore(Path filePath) {
        this.filePath = filePath;
    }

    void load() {
        requiredSegments.clear();
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
                if (parts.length >= 3 && "R".equals(parts[0])) {
                    requiredSegments.add(new SegmentKey(parts[1], parts[2]));
                }
            }
        } catch (IOException | RuntimeException _) {
            // Corrupt or missing config — safe to ignore, defaults apply (none required)
        }
    }

    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException _) {
            // Best-effort directory creation
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("# Required segments — always displayed for all players");
            writer.newLine();
            writer.write("# R|pluginId|segmentId");
            writer.newLine();
            for (SegmentKey key : requiredSegments) {
                writer.write("R|" + key.pluginId() + "|" + key.segmentId());
                writer.newLine();
            }
        } catch (IOException _) {
            // Non-critical — will be re-saved next shutdown
        }
    }

    boolean isRequired(SegmentKey key) {
        return requiredSegments.contains(key);
    }

    void setRequired(SegmentKey key, boolean required) {
        if (required) {
            requiredSegments.add(key);
        } else {
            requiredSegments.remove(key);
        }
    }

    void clearAll() {
        requiredSegments.clear();
    }

    Set<SegmentKey> getRequiredSegments() {
        return Collections.unmodifiableSet(requiredSegments);
    }
}
