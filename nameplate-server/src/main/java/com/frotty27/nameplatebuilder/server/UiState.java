package com.frotty27.nameplatebuilder.server;

/**
 * Snapshot of the editor UI state, persisted in memory across page reopens.
 *
 * <p>When a player closes and reopens the {@link NameplateBuilderPage}, the
 * previous tab selection, filter text, and pagination positions are restored
 * from a stored {@code UiState} instance keyed by UUID.</p>
 *
 * <p>Extracted as a top-level record to avoid {@code NoClassDefFoundError}
 * from Hytale's {@code PluginClassLoader} when resolving inner classes
 * after extended server uptime.</p>
 *
 * @param activeTab         the sidebar tab the player was viewing
 * @param filter            the search/filter text in the available blocks panel
 * @param availPage         current page index of the available blocks list
 * @param chainPage         current page index of the segment chain
 * @param adminLeftPage     current page of the admin Required left column (enabled)
 * @param adminRightPage    current page of the admin Required right column (available)
 * @param adminSubTab       which admin sub-tab was active (Required, Disabled, Settings)
 * @param adminDisLeftPage  current page of the admin Disabled left column (enabled)
 * @param adminDisRightPage current page of the admin Disabled right column (disabled)
 * @param disabledPage      current page of the player-facing Disabled tab grid
 *
 * @see NameplateBuilderPage
 * @see ActiveTab
 * @see AdminSubTab
 */
record UiState(ActiveTab activeTab, String filter, int availPage, int chainPage,
               int adminLeftPage, int adminRightPage, AdminSubTab adminSubTab,
               int adminDisLeftPage, int adminDisRightPage, int disabledPage) {
}
