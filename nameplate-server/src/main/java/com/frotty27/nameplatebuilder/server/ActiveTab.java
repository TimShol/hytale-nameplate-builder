package com.frotty27.nameplatebuilder.server;

/**
 * Sidebar tab selection for the {@link NameplateBuilderPage} editor UI.
 *
 * <p>Each constant maps to a tab button in the left sidebar. The
 * {@link #ADMIN} tab is only visible to players with the
 * {@code nameplatebuilder.admin} permission.</p>
 *
 * <p>Extracted as a top-level enum to avoid {@code NoClassDefFoundError}
 * from Hytale's {@code PluginClassLoader} when resolving inner classes
 * after extended server uptime.</p>
 *
 * @see NameplateBuilderPage
 * @see UiState
 */
enum ActiveTab {

    /** Global settings: master toggle, look-at filter, vertical offset, welcome message. */
    GENERAL,

    /** NPC segment chain editor and available blocks browser. */
    NPCS,

    /** Player segment chain editor and available blocks browser. */
    PLAYERS,

    /** Admin panel with Required, Disabled, and Settings sub-tabs. */
    ADMIN,

    /** Read-only view of all admin-disabled segments. */
    DISABLED
}
