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
 * Stores the server-wide admin configuration for required segments, disabled
 * segments, and the server display name.
 *
 * <p><b>Required</b> segments are always displayed on every player's nameplate,
 * regardless of their personal preferences.</p>
 *
 * <p><b>Disabled</b> segments are hidden from all players entirely — they cannot
 * be enabled, added to chains, or even seen in the available list. When every
 * registered segment is disabled, the aggregator blanks all nameplates globally
 * and the welcome message reports nameplates as disabled.</p>
 *
 * <p>A segment cannot be both required and disabled simultaneously; setting one
 * automatically removes the other.</p>
 *
 * <p>The <b>server name</b> is shown in the join welcome message
 * (e.g. {@code [MyServer] - Use /npb to customize your nameplates.}).
 * Defaults to {@code "NameplateBuilder"} when left blank.</p>
 *
 * <p>Persisted as a simple text file:
 * {@code S|serverName} for the server name,
 * {@code R|pluginId|segmentId} for required,
 * {@code D|pluginId|segmentId} for disabled.</p>
 */
final class AdminConfigStore {

    private final Path filePath;
    private final Set<SegmentKey> requiredSegments = ConcurrentHashMap.newKeySet();
    private final Set<SegmentKey> disabledSegments = ConcurrentHashMap.newKeySet();
    private volatile String serverName = "";

    AdminConfigStore(Path filePath) {
        this.filePath = filePath;
    }

    void load() {
        requiredSegments.clear();
        disabledSegments.clear();
        serverName = "";
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
                if (parts.length >= 2 && "S".equals(parts[0])) {
                    serverName = parts[1];
                    continue;
                }
                if (parts.length >= 3) {
                    if ("R".equals(parts[0])) {
                        requiredSegments.add(new SegmentKey(parts[1], parts[2]));
                    } else if ("D".equals(parts[0])) {
                        disabledSegments.add(new SegmentKey(parts[1], parts[2]));
                    }
                }
            }
        } catch (IOException | RuntimeException _) {
            // Corrupt or missing config — safe to ignore, defaults apply
        }
    }

    void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException _) {
            // Best-effort directory creation
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("# Server name for welcome message");
            writer.newLine();
            writer.write("S|" + serverName);
            writer.newLine();
            writer.newLine();
            writer.write("# Required segments — always displayed for all players");
            writer.newLine();
            writer.write("# R|pluginId|segmentId");
            writer.newLine();
            for (SegmentKey key : requiredSegments) {
                writer.write("R|" + key.pluginId() + "|" + key.segmentId());
                writer.newLine();
            }
            writer.newLine();
            writer.write("# Disabled segments — hidden from all players");
            writer.newLine();
            writer.write("# D|pluginId|segmentId");
            writer.newLine();
            for (SegmentKey key : disabledSegments) {
                writer.write("D|" + key.pluginId() + "|" + key.segmentId());
                writer.newLine();
            }
        } catch (IOException _) {
            // Non-critical — will be re-saved next shutdown
        }
    }

    // ── Required ──

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

    Set<SegmentKey> getRequiredSegments() {
        return Collections.unmodifiableSet(requiredSegments);
    }

    // ── Disabled ──

    boolean isDisabled(SegmentKey key) {
        return disabledSegments.contains(key);
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

    Set<SegmentKey> getDisabledSegments() {
        return Collections.unmodifiableSet(disabledSegments);
    }

    // ── Server Name ──

    String getServerName() {
        return serverName;
    }

    void setServerName(String name) {
        this.serverName = name != null ? name : "";
    }

    /** Returns the display name for messages — defaults to "NameplateBuilder" if blank. */
    String getDisplayServerName() {
        return serverName.isBlank() ? "NameplateBuilder" : serverName;
    }
}
