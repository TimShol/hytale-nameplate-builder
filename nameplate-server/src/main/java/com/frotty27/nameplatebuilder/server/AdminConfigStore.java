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

    /**
     * Creates a new admin config store backed by the given file.
     *
     * @param filePath path to the {@code admin_config.txt} file
     */
    AdminConfigStore(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Loads admin configuration from disk, replacing any in-memory state.
     *
     * <p>If the file does not exist or is corrupt, all collections are left
     * empty and the server name defaults to blank.</p>
     */
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

    /**
     * Persists the current admin configuration to disk.
     *
     * <p>Writes the server name, required segments, and disabled segments
     * in a human-readable pipe-delimited format with comments.</p>
     */
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

    /**
     * Returns {@code true} if the given segment is marked as required.
     *
     * @param key the segment key to check
     * @return whether the segment is required for all players
     */
    boolean isRequired(SegmentKey key) {
        return requiredSegments.contains(key);
    }

    /**
     * Marks or unmarks a segment as required.
     *
     * <p>Setting a segment as required automatically removes it from the
     * disabled set to enforce mutual exclusion.</p>
     *
     * @param key      the segment key
     * @param required {@code true} to require, {@code false} to un-require
     */
    void setRequired(SegmentKey key, boolean required) {
        if (required) {
            requiredSegments.add(key);
            disabledSegments.remove(key);
        } else {
            requiredSegments.remove(key);
        }
    }

    /** Removes all required segment entries. */
    void clearAllRequired() {
        requiredSegments.clear();
    }

    /**
     * Returns an unmodifiable view of all required segment keys.
     *
     * @return immutable set of required segments
     */
    Set<SegmentKey> getRequiredSegments() {
        return Collections.unmodifiableSet(requiredSegments);
    }

    // ── Disabled ──

    /**
     * Returns {@code true} if the given segment is globally disabled.
     *
     * @param key the segment key to check
     * @return whether the segment is hidden from all players
     */
    boolean isDisabled(SegmentKey key) {
        return disabledSegments.contains(key);
    }

    /**
     * Marks or unmarks a segment as globally disabled.
     *
     * <p>Setting a segment as disabled automatically removes it from the
     * required set to enforce mutual exclusion.</p>
     *
     * @param key      the segment key
     * @param disabled {@code true} to disable, {@code false} to re-enable
     */
    void setDisabled(SegmentKey key, boolean disabled) {
        if (disabled) {
            disabledSegments.add(key);
            requiredSegments.remove(key);
        } else {
            disabledSegments.remove(key);
        }
    }

    /** Removes all disabled segment entries. */
    void clearAllDisabled() {
        disabledSegments.clear();
    }

    /**
     * Returns an unmodifiable view of all disabled segment keys.
     *
     * @return immutable set of disabled segments
     */
    Set<SegmentKey> getDisabledSegments() {
        return Collections.unmodifiableSet(disabledSegments);
    }

    // ── Server Name ──

    /**
     * Returns the raw server name string (may be empty).
     *
     * @return the configured server name, or an empty string if unset
     */
    String getServerName() {
        return serverName;
    }

    /**
     * Sets the server display name for the welcome message.
     *
     * @param name the server name, or {@code null} to clear
     */
    void setServerName(String name) {
        this.serverName = name != null ? name : "";
    }

    /**
     * Returns the display name for messages, defaulting to
     * {@code "NameplateBuilder"} if no custom name is configured.
     *
     * @return the server display name (never blank)
     */
    String getDisplayServerName() {
        return serverName.isBlank() ? "NameplateBuilder" : serverName;
    }
}
