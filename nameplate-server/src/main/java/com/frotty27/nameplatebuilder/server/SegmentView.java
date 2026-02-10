package com.frotty27.nameplatebuilder.server;

/**
 * Immutable view of a segment's UI-relevant metadata.
 *
 * <p>Used by {@link NameplateBuilderPage} to render chain blocks, available
 * blocks, admin panels, and the player Disabled tab. Each instance is built
 * from the {@link NameplateRegistry.Segment} record at display time.</p>
 *
 * <p>Extracted as a top-level record to avoid {@code NoClassDefFoundError}
 * from Hytale's {@code PluginClassLoader} when resolving inner classes
 * after extended server uptime.</p>
 *
 * @param key          the unique segment identifier (plugin + segment ID)
 * @param displayName  human-readable name shown in the UI block header
 * @param modName      name of the plugin that registered this segment
 * @param author       author of the plugin (e.g. {@code "Frotty27"})
 * @param targetLabel  entity target tag (e.g. {@code "[All]"}, {@code "[NPCs]"})
 * @param example      optional preview text (e.g. {@code "67/67"}), may be {@code null}
 *
 * @see NameplateBuilderPage
 * @see NameplateRegistry
 */
record SegmentView(SegmentKey key, String displayName, String modName, String author, String targetLabel, String example) {
}
