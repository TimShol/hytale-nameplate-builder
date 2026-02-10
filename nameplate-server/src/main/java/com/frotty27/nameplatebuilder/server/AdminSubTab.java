package com.frotty27.nameplatebuilder.server;

/**
 * Sub-tab selection within the admin panel of the {@link NameplateBuilderPage}.
 *
 * <p>The admin panel is divided into three sub-tabs, each controlling a
 * different aspect of server-wide nameplate configuration.</p>
 *
 * <p>Extracted as a top-level enum to avoid {@code NoClassDefFoundError}
 * from Hytale's {@code PluginClassLoader} when resolving inner classes
 * after extended server uptime.</p>
 *
 * @see NameplateBuilderPage
 * @see AdminConfigStore
 * @see UiState
 */
enum AdminSubTab {

    /** Force segments to appear on all players' nameplates regardless of preferences. */
    REQUIRED,

    /** Hide segments globally so no player can see or enable them. */
    DISABLED,

    /** Server-wide settings such as the custom server display name. */
    SETTINGS
}
