package com.frotty27.nameplatebuilder.server;

/**
 * Unique identifier for a nameplate segment, composed of a plugin ID and segment ID.
 *
 * <p>Used as a map key throughout the preference, registry, and admin config stores.
 * Two segment keys are equal if and only if both their plugin ID and segment ID match.</p>
 *
 * @param pluginId  the owning plugin's identifier (e.g. {@code "Frotty27:ExampleMod"})
 * @param segmentId the segment identifier within that plugin (e.g. {@code "health"})
 */
record SegmentKey(String pluginId, String segmentId) {
}
