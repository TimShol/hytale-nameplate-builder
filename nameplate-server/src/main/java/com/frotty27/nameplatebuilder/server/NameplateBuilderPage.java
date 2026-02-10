package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jspecify.annotations.NonNull;

import com.frotty27.nameplatebuilder.api.SegmentTarget;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive custom UI page for the Nameplate Builder editor.
 *
 * <p>This page drives the entire player-facing UI: the sidebar navigation
 * (General, NPCs, Players, Admin), the segment chain editor, the available
 * blocks browser, the admin "Required Segments" panel, and the format variant
 * popup (with prefix/suffix text fields and bar empty-fill customization).</p>
 *
 * <p>The format popup uses a confirm/cancel workflow: selecting a variant only
 * updates the in-progress {@link #pendingVariant} state without persisting.
 * Prefix, suffix, and bar empty char edits are saved live via ValueChanged
 * events but reverted on Cancel using snapshotted originals.</p>
 *
 * <p>Opened via the {@link NameplateBuilderCommand} ({@code /npb}).</p>
 *
 * @see NameplateBuilderCommand
 * @see AdminConfigStore
 * @see NameplatePreferenceStore
 */
final class NameplateBuilderPage extends InteractiveCustomUIPage<NameplateBuilderPage.SettingsData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHAIN_PAGE_SIZE = 4;
    private static final int AVAIL_PAGE_SIZE = 8;
    private static final int ADMIN_PAGE_SIZE = 8;
    private static final int MOD_NAME_MAX_LENGTH = 24;
    /** Global preference entity type used for settings that apply regardless of tab. */
    private static final String ENTITY_TYPE_GLOBAL = "*";
    /** Virtual entity type for NPC tab chain/separator preferences. */
    static final String ENTITY_TYPE_NPCS = "_npcs";
    /** Virtual entity type for Player tab chain/separator preferences. */
    static final String ENTITY_TYPE_PLAYERS = "_players";

    /** Yellow-tinted background for required segments in the editor. */
    private static final String COLOR_REQUIRED = "#3d3820";
    /** Green-tinted background for enabled chain blocks. */
    private static final String COLOR_CHAIN_ACTIVE = "#1a3828";
    /** Default background for empty chain block slots. */
    private static final String COLOR_CHAIN_EMPTY = "#1a2840";
    /** Default background for available blocks. */
    private static final String COLOR_AVAIL_DEFAULT = "#162236";
    /** Warm purple-tinted background for built-in blocks in the available list. */
    private static final String COLOR_BUILTIN_AVAIL = "#2a1f3d";
    /** Selected variant option highlight in the variant popup. */
    private static final String COLOR_VARIANT_SELECTED = "#2d6b3f";
    /** Maximum number of variants supported in the popup UI. */
    private static final int MAX_VARIANT_OPTIONS = 4;

    private enum ActiveTab { GENERAL, NPCS, PLAYERS, ADMIN }

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;
    private final boolean isAdmin;
    private final UUID viewerUuid;

    private ActiveTab activeTab = ActiveTab.NPCS;
    private String filter = "";
    private String adminFilter = "";
    private int availPage = 0;
    private int chainPage = 0;
    private int adminLeftPage = 0;
    private int adminRightPage = 0;

    /**
     * When non-null, the user is actively editing the offset field.
     * We keep their raw input string here so {@link #populateCommands} won't
     * overwrite it with the stored numeric value while they're still typing.
     * Reset to {@code null} on any non-offset action (button press, page change, etc.).
     */
    private String pendingOffsetInput = null;

    /**
     * When non-null, a save feedback message is displayed next to the Save button.
     * Cleared on the next user interaction so it auto-disappears.
     */
    private String saveMessage = null;
    private boolean saveMessageSuccess = false;

    /**
     * Index of the separator currently being edited (-1 = popup closed).
     * This is the chain-page-relative index (0-2), corresponding to ChainSep0/1/2.
     */
    private int editingSepIndex = -1;
    private String sepText = "";

    /**
     * When non-null, the user is actively typing in the separator text field.
     * We keep their raw input here so {@link #populateCommands} won't overwrite
     * the field value while they're still editing (same pattern as pendingOffsetInput).
     * Set on every ValueChanged from the text field; reset to {@code null} when the
     * popup is closed (confirm/cancel) or when a non-sep action occurs.
     */
    private String pendingSepInput = null;

    /**
     * When non-null, the variant format popup is open for the given segment key.
     * The popup shows variant names and lets the player select their preferred format.
     */
    private SegmentKey editingVariantKey = null;

    /**
     * Tracks the user's in-progress variant selection within the popup.
     * Not committed to preferences until Confirm is pressed.
     */
    private int pendingVariant = 0;

    /** Original variant index when popup was opened — used to revert on Cancel. */
    private int originalVariant = 0;
    /** Original prefix when popup was opened — used to revert on Cancel. */
    private String originalPrefix = "";
    /** Original suffix when popup was opened — used to revert on Cancel. */
    private String originalSuffix = "";
    /** Original bar empty char when popup was opened — used to revert on Cancel. */
    private String originalBarEmpty = "";

    /**
     * When non-null, the user is actively typing in the prefix text field.
     * Same pattern as {@link #pendingSepInput}: prevents populateCommands from
     * overwriting the field value while the user is still editing.
     */
    private String pendingPrefixInput = null;

    /**
     * When non-null, the user is actively typing in the suffix text field.
     */
    private String pendingSuffixInput = null;

    /**
     * When non-null, the user is actively typing in the bar empty char field.
     */
    private String pendingBarEmptyInput = null;

    private static final Map<UUID, UiState> UI_STATE = new ConcurrentHashMap<>();

    NameplateBuilderPage(PlayerRef playerRef,
                         UUID viewerUuid,
                         NameplateRegistry registry,
                         NameplatePreferenceStore preferences,
                         AdminConfigStore adminConfig,
                         boolean isAdmin) {
        super(playerRef,
                com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime.CanDismiss,
                SettingsData.CODEC);
        this.viewerUuid = viewerUuid;
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
        this.isAdmin = isAdmin;

        UiState state = UI_STATE.get(viewerUuid);
        if (state != null) {
            this.activeTab = state.activeTab;
            // Don't restore ADMIN tab for non-admins
            if (this.activeTab == ActiveTab.ADMIN && !isAdmin) {
                this.activeTab = ActiveTab.NPCS;
            }
            this.filter = state.filter;
            this.availPage = state.availPage;
            this.chainPage = state.chainPage;
            this.adminLeftPage = state.adminLeftPage;
            this.adminRightPage = state.adminRightPage;
        }
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, @NonNull Store<EntityStore> store) {
        commands.append("Pages/NameplateBuilder_Editor.ui");
        commands.set("#FilterField.Value", filter);
        commands.set("#AdminFilterField.Value", adminFilter);

        // Filter field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#FilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Filter", "#FilterField.Value"),
                false);

        // Admin filter field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#AdminFilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@AdminFilter", "#AdminFilterField.Value"),
                false);

        // Offset field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#OffsetField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Offset", "#OffsetField.Value"),
                false);

        // Sidebar navigation bindings
        bindAction(events, "#NavGeneral", "NavGeneral");
        bindAction(events, "#NavNpcs", "NavNpcs");
        bindAction(events, "#NavPlayers", "NavPlayers");
        bindAction(events, "#NavAdmin", "NavAdmin");
        bindAction(events, "#EnableToggle", "ToggleEnable");

        // Button bindings — both Save buttons (one per tab) share same action
        bindAction(events, "#SaveButton", "Save");
        bindAction(events, "#SaveButtonGeneral", "Save");
        bindAction(events, "#SaveButtonAdmin", "SaveAdmin");
        bindAction(events, "#ResetButtonAdmin", "ResetAdmin");
        bindAction(events, "#CloseButton", "Close");
        bindAction(events, "#PrevAvail", "PrevAvail");
        bindAction(events, "#NextAvail", "NextAvail");
        bindAction(events, "#PrevChain", "PrevChain");
        bindAction(events, "#NextChain", "NextChain");
        bindAction(events, "#PrevAdminLeft", "PrevAdminLeft");
        bindAction(events, "#NextAdminLeft", "NextAdminLeft");
        bindAction(events, "#PrevAdminRight", "PrevAdminRight");
        bindAction(events, "#NextAdminRight", "NextAdminRight");

        // Chain block buttons (4 blocks)
        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainBlock" + i + "Left", "Left_" + i);
            bindAction(events, "#ChainBlock" + i + "Right", "Right_" + i);
            bindAction(events, "#ChainBlock" + i + "Remove", "Remove_" + i);
        }

        // Chain separator indicators — still clickable for visual feedback but no editing
        for (int i = 0; i < CHAIN_PAGE_SIZE - 1; i++) {
            bindAction(events, "#ChainSep" + i, "EditSep_" + i);
        }

        // Available block buttons (8 blocks)
        for (int i = 0; i < AVAIL_PAGE_SIZE; i++) {
            bindAction(events, "#AvailBlock" + i + "Add", "Add_" + i);
        }

        // Admin enable buttons (left column — 8 rows)
        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminEnable" + i, "AdminEnable_" + i);
        }

        // Admin disable buttons (right column — 8 rows)
        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminDisable" + i, "AdminDisable_" + i);
        }

        // Clear chain
        bindAction(events, "#ClearChainButton", "ClearChain");

        // Look-at toggle
        bindAction(events, "#LookToggle", "ToggleLook");

        // Separator popup
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#SepTextField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@SepText", "#SepTextField.Value"),
                false);
        bindAction(events, "#SepConfirm", "SepConfirm");
        bindAction(events, "#SepCancel", "SepCancel");

        // Chain block format buttons (4 blocks)
        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainBlock" + i + "Format", "Format_" + i);
        }

        // Variant popup buttons (4 option slots + cancel)
        for (int i = 0; i < MAX_VARIANT_OPTIONS; i++) {
            bindAction(events, "#Variant" + i, "VariantSelect_" + i);
        }
        bindAction(events, "#VariantCancel", "VariantCancel");
        bindAction(events, "#VariantConfirm", "VariantConfirm");

        // Prefix/suffix field bindings (inside variant popup)
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#PrefixField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@PrefixText", "#PrefixField.Value"),
                false);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#SuffixField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@SuffixText", "#SuffixField.Value"),
                false);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#BarEmptyField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@BarEmptyText", "#BarEmptyField.Value"),
                false);

        populateCommands(commands);
    }

    private void bindAction(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                selector,
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", action),
                false);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref, @NonNull Store<EntityStore> store, @NonNull SettingsData data) {
        if (data == null) {
            return;
        }
        // Clear pending offset input when the user performs any non-offset action
        // (button click, page navigation, etc.) so the field shows the committed value.
        if (data.offset == null) {
            pendingOffsetInput = null;
        }

        // Clear save message on any non-save interaction so it auto-disappears
        if (data.action == null || (!"Save".equals(data.action) && !"SaveAdmin".equals(data.action))) {
            saveMessage = null;
        }

        if (data.filter != null) {
            filter = data.filter.trim();
            availPage = 0;
        }

        if (data.adminFilter != null) {
            adminFilter = data.adminFilter.trim();
            adminLeftPage = 0;
            adminRightPage = 0;
        }

        if (data.sepText != null) {
            sepText = data.sepText;
            pendingSepInput = data.sepText;
            // If only the sep text changed (no action), just store it and return
            // without sending an update — avoids disrupting the active text field.
            if (data.action == null && data.filter == null && data.offset == null && data.adminFilter == null
                    && data.prefixText == null && data.suffixText == null && data.barEmptyText == null) {
                return;
            }
        }

        // Prefix/suffix/barEmpty text changes — save immediately for live preview
        if (data.prefixText != null || data.suffixText != null || data.barEmptyText != null) {
            if (editingVariantKey != null) {
                if (data.prefixText != null) {
                    pendingPrefixInput = data.prefixText;
                    preferences.setPrefix(viewerUuid, tabEntityType(), editingVariantKey, data.prefixText);
                }
                if (data.suffixText != null) {
                    pendingSuffixInput = data.suffixText;
                    preferences.setSuffix(viewerUuid, tabEntityType(), editingVariantKey, data.suffixText);
                }
                if (data.barEmptyText != null) {
                    pendingBarEmptyInput = data.barEmptyText;
                    preferences.setBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey, data.barEmptyText);
                }
            }
            // If only text fields changed (no action), just store and return
            if (data.action == null && data.filter == null && data.offset == null
                    && data.adminFilter == null && data.sepText == null) {
                return;
            }
        }

        if (data.offset != null) {
            handleOffsetChange(data.offset);
        }

        if (data.action == null || data.action.isBlank()) {
            sendUpdate(buildUpdate());
            return;
        }

        switch (data.action) {
            case "NavGeneral" -> {
                switchTab(ActiveTab.GENERAL);
                sendUpdate(buildUpdate());
                return;
            }
            case "NavNpcs" -> {
                switchTab(ActiveTab.NPCS);
                sendUpdate(buildUpdate());
                return;
            }
            case "NavPlayers" -> {
                switchTab(ActiveTab.PLAYERS);
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdmin" -> {
                if (isAdmin) {
                    switchTab(ActiveTab.ADMIN);
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleEnable" -> {
                boolean current = preferences.isNameplatesEnabled(viewerUuid);
                preferences.setNameplatesEnabled(viewerUuid, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "Close" -> {
                close();
                return;
            }
            case "Save" -> {
                // Snapshot both tab chains so that segment enabled/order state
                // is persisted even if the user never explicitly added/removed blocks.
                // Use filtered keys per entity type so only relevant segments are snapshotted.
                try {
                    List<SegmentKey> npcKeys = getFilteredKeysForEntityType(ENTITY_TYPE_NPCS);
                    List<SegmentKey> playerKeys = getFilteredKeysForEntityType(ENTITY_TYPE_PLAYERS);
                    preferences.snapshotChain(viewerUuid, ENTITY_TYPE_NPCS, npcKeys, getDefaultComparator());
                    preferences.snapshotChain(viewerUuid, ENTITY_TYPE_PLAYERS, playerKeys, getDefaultComparator());
                    preferences.save();
                    persistUiState();
                    saveMessage = "Saved changes successfully!";
                    saveMessageSuccess = true;
                } catch (Throwable e) {
                    LOGGER.atWarning().withCause(e).log("Failed to save preferences for player %s", viewerUuid);
                    saveMessage = "Could not save changes!";
                    saveMessageSuccess = false;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "SaveAdmin" -> {
                if (isAdmin) {
                    try {
                        adminConfig.save();
                        persistUiState();
                        saveMessage = "Admin config saved!";
                        saveMessageSuccess = true;
                    } catch (Throwable e) {
                        LOGGER.atWarning().withCause(e).log("Failed to save admin config");
                        saveMessage = "Could not save admin config!";
                        saveMessageSuccess = false;
                    }
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ResetAdmin" -> {
                if (isAdmin) {
                    adminConfig.clearAll();
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "PrevAvail" -> {
                availPage = Math.max(0, availPage - 1);
                sendUpdate(buildUpdate());
                return;
            }
            case "NextAvail" -> {
                availPage = availPage + 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "PrevChain" -> {
                chainPage = Math.max(0, chainPage - 1);
                sendUpdate(buildUpdate());
                return;
            }
            case "NextChain" -> {
                chainPage = chainPage + 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "PrevAdminLeft" -> {
                adminLeftPage = Math.max(0, adminLeftPage - 1);
                sendUpdate(buildUpdate());
                return;
            }
            case "NextAdminLeft" -> {
                adminLeftPage = adminLeftPage + 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "PrevAdminRight" -> {
                adminRightPage = Math.max(0, adminRightPage - 1);
                sendUpdate(buildUpdate());
                return;
            }
            case "NextAdminRight" -> {
                adminRightPage = adminRightPage + 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleLook" -> {
                boolean current = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE_GLOBAL);
                preferences.setOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE_GLOBAL, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "ClearChain" -> {
                clearChain();
                chainPage = 0;
                sendUpdate(buildUpdate());
                return;
            }
        }

        // Separator click — open separator edit popup
        if (data.action.startsWith("EditSep_")) {
            int sepIndex = parseRowIndex(data.action, "EditSep_");
            List<SegmentView> chain = getChainViews();
            int absoluteIndex = chainPage * CHAIN_PAGE_SIZE + sepIndex;
            if (absoluteIndex >= 0 && absoluteIndex < chain.size()) {
                editingSepIndex = sepIndex;
                SegmentKey blockKey = chain.get(absoluteIndex).key();
                sepText = preferences.getSeparatorAfter(viewerUuid, tabEntityType(), blockKey);
                // Reset pending input so populateCommands sets the initial value
                pendingSepInput = null;
            }
            sendUpdate(buildUpdate());
            return;
        }

        // Separator popup confirm
        if ("SepConfirm".equals(data.action)) {
            if (editingSepIndex >= 0) {
                List<SegmentView> chain = getChainViews();
                int absoluteIndex = chainPage * CHAIN_PAGE_SIZE + editingSepIndex;
                if (absoluteIndex >= 0 && absoluteIndex < chain.size()) {
                    SegmentKey blockKey = chain.get(absoluteIndex).key();
                    // Save exactly what the user typed — empty string is valid
                    preferences.setSeparatorAfter(viewerUuid, tabEntityType(), blockKey, sepText);
                    // Also update the global default separator so newly added blocks
                    // use the last confirmed separator text
                    preferences.setSeparator(viewerUuid, tabEntityType(), sepText);
                }
            }
            editingSepIndex = -1;
            sepText = "";
            pendingSepInput = null;
            sendUpdate(buildUpdate());
            return;
        }

        // Separator popup cancel
        if ("SepCancel".equals(data.action)) {
            editingSepIndex = -1;
            sepText = "";
            pendingSepInput = null;
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Add_")) {
            int row = parseRowIndex(data.action, "Add_");
            addRow(row);
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Remove_")) {
            int row = parseRowIndex(data.action, "Remove_");
            removeRow(row);
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Left_")) {
            int row = parseRowIndex(data.action, "Left_");
            moveRow(row, -1);
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Right_")) {
            int row = parseRowIndex(data.action, "Right_");
            moveRow(row, 1);
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("AdminEnable_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminEnable_");
                enableAdminRow(row);
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("AdminDisable_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminDisable_");
                disableAdminRow(row);
            }
            sendUpdate(buildUpdate());
            return;
        }

        // Format button on chain block — open variant popup
        if (data.action.startsWith("Format_")) {
            int row = parseRowIndex(data.action, "Format_");
            SegmentView view = getChainRow(row);
            if (view != null) {
                NameplateRegistry.Segment segment = registry.getSegments().get(view.key());
                if (segment != null && segment.variants().size() > 1) {
                    editingVariantKey = view.key();
                    // Snapshot current values so Cancel can revert
                    originalVariant = preferences.getSelectedVariant(viewerUuid, tabEntityType(), view.key());
                    pendingVariant = originalVariant;
                    originalPrefix = preferences.getPrefix(viewerUuid, tabEntityType(), view.key());
                    originalSuffix = preferences.getSuffix(viewerUuid, tabEntityType(), view.key());
                    originalBarEmpty = preferences.getBarEmptyChar(viewerUuid, tabEntityType(), view.key());
                    pendingPrefixInput = null;
                    pendingSuffixInput = null;
                    pendingBarEmptyInput = null;
                }
            }
            sendUpdate(buildUpdate());
            return;
        }

        // Variant popup selection — stays in popup, just updates pending choice
        if (data.action.startsWith("VariantSelect_")) {
            int variantIndex = parseRowIndex(data.action, "VariantSelect_");
            if (editingVariantKey != null && variantIndex >= 0) {
                // Ignore clicks on the already-selected variant
                if (variantIndex == pendingVariant) {
                    sendUpdate(buildUpdate());
                    return;
                }
                pendingVariant = variantIndex;
            }
            sendUpdate(buildUpdate());
            return;
        }

        // Variant popup confirm — persist selections and close
        if ("VariantConfirm".equals(data.action)) {
            if (editingVariantKey != null) {
                preferences.setSelectedVariant(viewerUuid, tabEntityType(), editingVariantKey, pendingVariant);
                // Prefix/suffix/barEmpty are already saved live via ValueChanged — no extra work needed
            }
            editingVariantKey = null;
            pendingPrefixInput = null;
            pendingSuffixInput = null;
            pendingBarEmptyInput = null;
            sendUpdate(buildUpdate());
            return;
        }

        // Variant popup cancel — revert to originals and close
        if ("VariantCancel".equals(data.action)) {
            if (editingVariantKey != null) {
                // Revert variant, prefix, suffix, barEmpty to what they were before popup opened
                preferences.setSelectedVariant(viewerUuid, tabEntityType(), editingVariantKey, originalVariant);
                preferences.setPrefix(viewerUuid, tabEntityType(), editingVariantKey, originalPrefix);
                preferences.setSuffix(viewerUuid, tabEntityType(), editingVariantKey, originalSuffix);
                preferences.setBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey, originalBarEmpty);
            }
            editingVariantKey = null;
            pendingPrefixInput = null;
            pendingSuffixInput = null;
            pendingBarEmptyInput = null;
            sendUpdate(buildUpdate());
        }
    }

    private void handleOffsetChange(String rawOffset) {
        if (rawOffset == null) {
            pendingOffsetInput = null;
            return;
        }

        // Normalize commas to dots and strip whitespace
        String normalized = rawOffset.replace(',', '.').trim();

        // Reject anything that isn't a plausible numeric fragment.
        // Allow: empty, "-", digits with at most one dot, optional leading minus.
        if (!normalized.isEmpty() && !isValidOffsetFragment(normalized)) {
            // Invalid characters — reset the field to the stored value
            pendingOffsetInput = null;
            return;
        }

        // Keep the raw input so the UI doesn't overwrite it while typing
        pendingOffsetInput = normalized;

        // Try to parse as a finished number and commit to preferences
        try {
            double value = Double.parseDouble(normalized);
            preferences.setOffset(viewerUuid, ENTITY_TYPE_GLOBAL, value);
        } catch (NumberFormatException _) {
            // Intermediate state like "", "-", "1.", "-0." — don't update the stored value yet
        }
    }

    /** Returns the virtual entity type for the current tab's chain preferences. */
    private String tabEntityType() {
        return activeTab == ActiveTab.PLAYERS ? ENTITY_TYPE_PLAYERS : ENTITY_TYPE_NPCS;
    }

    private void switchTab(ActiveTab tab) {
        activeTab = tab;
        filter = "";
        adminFilter = "";
        availPage = 0;
        chainPage = 0;
        adminLeftPage = 0;
        adminRightPage = 0;
        pendingOffsetInput = null;
    }

    /**
     * Returns {@code true} if the given segment should be visible in the currently active tab.
     * Segments with {@code target = ALL} appear in both NPCS and PLAYERS tabs.
     */
    private boolean isSegmentVisibleForActiveTab(NameplateRegistry.Segment segment) {
        if (activeTab == ActiveTab.GENERAL || activeTab == ActiveTab.ADMIN) return false;
        SegmentTarget target = segment.target();
        if (target == SegmentTarget.ALL) return true;
        if (activeTab == ActiveTab.NPCS) return target == SegmentTarget.NPCS;
        if (activeTab == ActiveTab.PLAYERS) return target == SegmentTarget.PLAYERS;
        return false;
    }

    /**
     * Returns {@code true} if the string looks like a valid partial numeric input.
     * Allows: empty, "-", "1", "-1", "1.", "-1.", "1.5", "-1.5", ".5", "-.5", etc.
     * Rejects anything with letters or multiple dots/minuses.
     */
    private static boolean isValidOffsetFragment(String s) {
        if (s.isEmpty()) return true;
        boolean hasDot = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-') {
                if (i != 0) return false; // minus only at start
            } else if (c == '.') {
                if (hasDot) return false; // only one dot
                hasDot = true;
            } else if (c < '0' || c > '9') {
                return false; // only digits, dot, and leading minus
            }
        }
        return true;
    }

    /**
     * Build an incremental update — only set property values on existing elements.
     * Never re-append the .ui page or re-bind events; those are set once in {@link #build}.
     */
    private UICommandBuilder buildUpdate() {
        UICommandBuilder commands = new UICommandBuilder();
        populateCommands(commands);
        return commands;
    }

    /** Shared logic for populating all dynamic UI properties (used by both build and update). */
    private void populateCommands(UICommandBuilder commands) {
        // ── Popups ── (hide unless editor tab explicitly shows them)
        commands.set("#SepPopupOverlay.Visible", false);
        commands.set("#VariantPopupOverlay.Visible", false);

        // ── Sidebar active indicators ──
        commands.set("#NavGeneral.Text", activeTab == ActiveTab.GENERAL ? "> Settings" : "  Settings");
        commands.set("#NavNpcs.Text", activeTab == ActiveTab.NPCS ? "> NPCs" : "  NPCs");
        commands.set("#NavPlayers.Text", activeTab == ActiveTab.PLAYERS ? "> Players" : "  Players");

        // Admin sidebar — only visible to admins
        commands.set("#AdminSection.Visible", isAdmin);
        if (isAdmin) {
            commands.set("#NavAdmin.Text", activeTab == ActiveTab.ADMIN ? "> Settings" : "  Settings");
        }

        // ── Tab visibility ──
        commands.set("#TabGeneral.Visible", activeTab == ActiveTab.GENERAL);
        commands.set("#TabEditor.Visible", activeTab == ActiveTab.NPCS || activeTab == ActiveTab.PLAYERS);
        commands.set("#TabAdmin.Visible", activeTab == ActiveTab.ADMIN);

        // ── General tab content ──
        if (activeTab == ActiveTab.GENERAL) {
            boolean enabled = preferences.isNameplatesEnabled(viewerUuid);
            commands.set("#EnableToggle.Text", enabled ? "  [X]  " : "  [ ]  ");

            boolean lookOnly = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE_GLOBAL);
            commands.set("#LookToggle.Text", lookOnly ? "  [X]  " : "  [ ]  ");

            // Offset field — preserve the user's raw input while they're typing
            if (pendingOffsetInput != null) {
                commands.set("#OffsetField.Value", pendingOffsetInput);
            } else {
                double offset = preferences.getOffset(viewerUuid, ENTITY_TYPE_GLOBAL);
                commands.set("#OffsetField.Value", offset == 0.0 ? "0.0" : String.valueOf(offset));
            }

            // Save feedback message
            commands.set("#SaveMessageGeneral.Visible", saveMessage != null);
            if (saveMessage != null) {
                commands.set("#SaveMessageGeneral.Text", saveMessage);
                commands.set("#SaveMessageGeneral.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
            }
            return;
        }

        // ── Admin tab content ──
        if (activeTab == ActiveTab.ADMIN) {
            commands.set("#AdminFilterField.Value", adminFilter);
            fillAdmin(commands);

            // Save feedback message
            commands.set("#SaveMessageAdmin.Visible", saveMessage != null);
            if (saveMessage != null) {
                commands.set("#SaveMessageAdmin.Text", saveMessage);
                commands.set("#SaveMessageAdmin.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
            }
            return;
        }

        // ── Editor tab content ──
        commands.set("#EditorTitle.Text", activeTab == ActiveTab.NPCS ? "YOUR NPC NAMEPLATE" : "YOUR PLAYER NAMEPLATE");

        commands.set("#FilterField.Value", filter);

        List<SegmentView> chain = getChainViews();
        List<SegmentView> available = getAvailableViews();

        int totalAvailPages = Math.max(1, (int) Math.ceil(available.size() / (double) AVAIL_PAGE_SIZE));
        int totalChainPages = Math.max(1, (int) Math.ceil(chain.size() / (double) CHAIN_PAGE_SIZE));
        if (availPage >= totalAvailPages) availPage = totalAvailPages - 1;
        if (chainPage >= totalChainPages) chainPage = totalChainPages - 1;

        fillChain(commands, chain, totalChainPages);
        fillAvailable(commands, available, totalAvailPages);

        commands.set("#PreviewText.Text", buildPreview(chain));

        // Save feedback message
        commands.set("#SaveMessageEditor.Visible", saveMessage != null);
        if (saveMessage != null) {
            commands.set("#SaveMessageEditor.Text", saveMessage);
            commands.set("#SaveMessageEditor.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
        }

        // Separator edit popup
        boolean showSepPopup = editingSepIndex >= 0;
        commands.set("#SepPopupOverlay.Visible", showSepPopup);
        if (showSepPopup && pendingSepInput == null) {
            // Only set the field value when initially opening the popup,
            // not while the user is actively typing (pendingSepInput != null).
            commands.set("#SepTextField.Value", sepText);
        }

        // Variant format popup
        boolean showVariantPopup = editingVariantKey != null;
        commands.set("#VariantPopupOverlay.Visible", showVariantPopup);
        if (showVariantPopup) {
            NameplateRegistry.Segment varSeg = registry.getSegments().get(editingVariantKey);
            if (varSeg != null) {
                commands.set("#VariantSegmentName.Text", varSeg.displayName());
                List<String> variants = varSeg.variants();
                // Use pendingVariant (in-progress selection) instead of committed preferences
                for (int vi = 0; vi < MAX_VARIANT_OPTIONS; vi++) {
                    boolean vVisible = vi < variants.size();
                    commands.set("#Variant" + vi + ".Visible", vVisible);
                    if (vVisible) {
                        String variantName = variants.get(vi);
                        boolean selected = vi == pendingVariant;
                        commands.set("#Variant" + vi + ".Text", selected ? "> " + variantName : "  " + variantName);
                        // Selected option: locked green background on all states (no hover effect)
                        // Unselected options: normal hover/press feedback
                        commands.set("#Variant" + vi + ".Style.Default.Background", selected ? COLOR_VARIANT_SELECTED : "#1a2840");
                        commands.set("#Variant" + vi + ".Style.Hovered.Background", selected ? COLOR_VARIANT_SELECTED : "#243650");
                        commands.set("#Variant" + vi + ".Style.Pressed.Background", selected ? COLOR_VARIANT_SELECTED : "#0f1824");
                    }
                }

                // Prefix/suffix section — only shown for segments that support it
                boolean showPrefixSuffix = varSeg.supportsPrefixSuffix();
                commands.set("#PrefixSuffixSection.Visible", showPrefixSuffix);
                if (showPrefixSuffix) {
                    // Only set field values when not actively typing (same pattern as sep text)
                    if (pendingPrefixInput == null) {
                        String pfx = preferences.getPrefix(viewerUuid, tabEntityType(), editingVariantKey);
                        commands.set("#PrefixField.Value", pfx);
                    }
                    if (pendingSuffixInput == null) {
                        String sfx = preferences.getSuffix(viewerUuid, tabEntityType(), editingVariantKey);
                        commands.set("#SuffixField.Value", sfx);
                    }
                }

                // Bar customization section — shown for segments with prefix/suffix support
                // (currently only Health) so the player can customize the empty fill character
                commands.set("#BarCustomSection.Visible", showPrefixSuffix);
                if (showPrefixSuffix) {
                    if (pendingBarEmptyInput == null) {
                        String barEmpty = preferences.getBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey);
                        commands.set("#BarEmptyField.Value", barEmpty);
                    }
                }
            }
        }
    }

    private void persistUiState() {
        UI_STATE.put(viewerUuid, new UiState(activeTab, filter, availPage, chainPage, adminLeftPage, adminRightPage));
    }

    // ── Fill Chain Strip ──

    private void fillChain(UICommandBuilder commands, List<SegmentView> chain, int totalPages) {
        boolean hasChain = !chain.isEmpty();
        commands.set("#ChainEmpty.Visible", !hasChain);
        commands.set("#ChainStrip.Visible", hasChain);

        int start = chainPage * CHAIN_PAGE_SIZE;
        int end = Math.min(chain.size(), start + CHAIN_PAGE_SIZE);

        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            String prefix = "#ChainBlock" + i;
            commands.set(prefix + ".Visible", visible);
            if (visible) {
                SegmentView view = chain.get(index);
                commands.set(prefix + "Label.Text", view.displayName());
                commands.set(prefix + "Sub.Text", truncateModName(view.modName()));
                // Show target tag on chain blocks too
                String authorWithTag = "by " + view.author();
                if (!authorWithTag.contains("[")) {
                    authorWithTag += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Author.Text", authorWithTag);
                // Background tint — yellow for required, green for active (including built-in)
                boolean required = adminConfig.isRequired(view.key());
                NameplateRegistry.Segment chainSeg = registry.getSegments().get(view.key());
                commands.set(prefix + ".Background.Color", required ? COLOR_REQUIRED : COLOR_CHAIN_ACTIVE);

                // Variant state — used for both example bar and format button
                boolean hasVariants = chainSeg != null && chainSeg.variants().size() > 1;
                int selectedVariant = hasVariants
                        ? preferences.getSelectedVariant(viewerUuid, tabEntityType(), view.key())
                        : 0;

                // Example bar — show selected variant's example value when non-default
                String example = view.example();
                if (hasVariants && selectedVariant > 0 && selectedVariant < chainSeg.variants().size()) {
                    // Extract the value inside parentheses if present, e.g. "Percentage (69%)" → "69%"
                    String variantName = chainSeg.variants().get(selectedVariant);
                    int parenStart = variantName.indexOf('(');
                    int parenEnd = variantName.lastIndexOf(')');
                    if (parenStart >= 0 && parenEnd > parenStart) {
                        example = variantName.substring(parenStart + 1, parenEnd);
                    } else {
                        example = variantName;
                    }
                }
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }

                // Hide remove button for required segments (locked in chain)
                commands.set(prefix + "Remove.Visible", !required);

                // For required blocks, show spacers to center the < > buttons
                commands.set(prefix + "TopSpacer.Visible", required);
                commands.set(prefix + "BtnLeftSpacer.Visible", required);
                commands.set(prefix + "BtnRightSpacer.Visible", required);

                // Format button — shown when the segment has multiple variants
                // Green background + "Formatted" text when a non-default variant is selected
                commands.set(prefix + "Format.Visible", hasVariants);
                if (hasVariants) {
                    boolean variantActive = selectedVariant > 0;
                    commands.set(prefix + "Format.Text", variantActive ? "Formatted" : "Format");
                    commands.set(prefix + "Format.Style.Default.Background", variantActive ? "#2d6b3f" : "#1a2840");
                    commands.set(prefix + "Format.Style.Hovered.Background", variantActive ? "#3a8a50" : "#2d6b3f");
                    commands.set(prefix + "Format.Style.Pressed.Background", variantActive ? "#225530" : "#225530");
                }
            } else {
                // Reset to default color when slot is empty
                commands.set(prefix + ".Background.Color", COLOR_CHAIN_EMPTY);
                commands.set(prefix + "ExampleBar.Visible", false);
                commands.set(prefix + "Remove.Visible", true);
                commands.set(prefix + "TopSpacer.Visible", false);
                commands.set(prefix + "BtnLeftSpacer.Visible", false);
                commands.set(prefix + "BtnRightSpacer.Visible", false);
                commands.set(prefix + "Format.Visible", false);
            }

            // Separator indicator between blocks — shows the auto-managed separator
            if (i < CHAIN_PAGE_SIZE - 1) {
                boolean sepVisible = visible && (start + i + 1) < end;
                String sepId = "#ChainSep" + i;
                commands.set(sepId + ".Visible", sepVisible);
                if (sepVisible) {
                    SegmentKey blockKey = chain.get(index).key();
                    String sep = preferences.getSeparatorAfter(viewerUuid, tabEntityType(), blockKey);
                    String displaySep = sep.isEmpty() ? "." : sep;
                    commands.set(sepId + ".Text", displaySep);
                }
            }
        }

        // Pagination — container always visible to reserve space; hide children instead
        boolean showPagination = totalPages > 1;
        commands.set("#PrevChain.Visible", showPagination);
        commands.set("#ChainPageLabel.Visible", showPagination);
        commands.set("#NextChain.Visible", showPagination);
        if (showPagination) {
            commands.set("#ChainPageLabel.Text", "Page " + (chainPage + 1) + "/" + totalPages);
        }
    }

    // ── Fill Available Blocks ──

    private void fillAvailable(UICommandBuilder commands, List<SegmentView> available, int totalPages) {
        boolean hasAvailable = !available.isEmpty();
        commands.set("#AvailEmpty.Visible", !hasAvailable);

        int start = availPage * AVAIL_PAGE_SIZE;
        int end = Math.min(available.size(), start + AVAIL_PAGE_SIZE);

        for (int i = 0; i < AVAIL_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            String prefix = "#AvailBlock" + i;
            commands.set(prefix + ".Visible", visible);
            if (visible) {
                SegmentView view = available.get(index);
                commands.set(prefix + "Label.Text", view.displayName());
                commands.set(prefix + "Sub.Text", truncateModName(view.modName()));
                // Show target tag: "by Author [All]" etc.
                String authorWithTag = "by " + view.author();
                if (!authorWithTag.contains("[")) {
                    // Always show the target scope so users know what tab it applies to
                    authorWithTag += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Author.Text", authorWithTag);
                // Example bar
                String example = view.example();
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }

                // Yellow tint for required, warm purple for built-in, default otherwise
                boolean required = adminConfig.isRequired(view.key());
                NameplateRegistry.Segment availSeg = registry.getSegments().get(view.key());
                boolean builtIn = availSeg != null && availSeg.builtIn();
                String availBg = required ? COLOR_REQUIRED : (builtIn ? COLOR_BUILTIN_AVAIL : COLOR_AVAIL_DEFAULT);
                commands.set(prefix + ".Background.Color", availBg);
            }
        }

        // Pagination — container always visible to reserve space; hide children instead
        boolean showPagination = totalPages > 1;
        commands.set("#PrevAvail.Visible", showPagination);
        commands.set("#AvailPaginationLabel.Visible", showPagination);
        commands.set("#NextAvail.Visible", showPagination);
        if (showPagination) {
            commands.set("#AvailPaginationLabel.Text", "Page " + (availPage + 1) + "/" + totalPages);
        }
    }

    // ── Fill Admin Columns ──

    private void fillAdmin(UICommandBuilder commands) {
        List<SegmentView> allSegments = getAllSegmentViews();
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerAdminFilter = adminFilter.isBlank() ? null : adminFilter.toLowerCase();

        // Split into left (available / not required) and right (required), applying filter
        List<SegmentView> leftList = new ArrayList<>();
        List<SegmentView> rightList = new ArrayList<>();
        for (SegmentView view : allSegments) {
            if (lowerAdminFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerAdminFilter)) {
                    continue;
                }
            }
            if (adminConfig.isRequired(view.key())) {
                rightList.add(view);
            } else {
                leftList.add(view);
            }
        }

        fillAdminLeft(commands, leftList);
        fillAdminRight(commands, rightList);
    }

    private void fillAdminLeft(UICommandBuilder commands, List<SegmentView> leftList) {
        boolean hasLeft = !leftList.isEmpty();
        commands.set("#AdminLeftEmpty.Visible", !hasLeft);

        int totalPages = Math.max(1, (int) Math.ceil(leftList.size() / (double) ADMIN_PAGE_SIZE));
        if (adminLeftPage >= totalPages) adminLeftPage = totalPages - 1;

        int start = adminLeftPage * ADMIN_PAGE_SIZE;
        int end = Math.min(leftList.size(), start + ADMIN_PAGE_SIZE);

        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            commands.set("#AdminLeftRow" + i + ".Visible", visible);
            if (visible) {
                SegmentView view = leftList.get(index);
                String prefix = "#AdminLeftBlock" + i;
                commands.set(prefix + "Label.Text", view.displayName());
                String sub = truncateModName(view.modName()) + " - by " + view.author();
                if (!sub.contains("[")) {
                    sub += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Sub.Text", sub);
                String example = view.example();
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }
            }
        }

        boolean showPagination = totalPages > 1;
        commands.set("#PrevAdminLeft.Visible", showPagination);
        commands.set("#AdminLeftPageLabel.Visible", showPagination);
        commands.set("#NextAdminLeft.Visible", showPagination);
        if (showPagination) {
            commands.set("#AdminLeftPageLabel.Text", "Page " + (adminLeftPage + 1) + "/" + totalPages);
        }
    }

    private void fillAdminRight(UICommandBuilder commands, List<SegmentView> rightList) {
        boolean hasRight = !rightList.isEmpty();
        commands.set("#AdminRightEmpty.Visible", !hasRight);

        int totalPages = Math.max(1, (int) Math.ceil(rightList.size() / (double) ADMIN_PAGE_SIZE));
        if (adminRightPage >= totalPages) adminRightPage = totalPages - 1;

        int start = adminRightPage * ADMIN_PAGE_SIZE;
        int end = Math.min(rightList.size(), start + ADMIN_PAGE_SIZE);

        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            commands.set("#AdminRightRow" + i + ".Visible", visible);
            if (visible) {
                SegmentView view = rightList.get(index);
                String prefix = "#AdminRightBlock" + i;
                commands.set(prefix + "Label.Text", view.displayName());
                String sub = truncateModName(view.modName()) + " - by " + view.author();
                if (!sub.contains("[")) {
                    sub += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Sub.Text", sub);
                String example = view.example();
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }
            }
        }

        boolean showPagination = totalPages > 1;
        commands.set("#PrevAdminRight.Visible", showPagination);
        commands.set("#AdminRightPageLabel.Visible", showPagination);
        commands.set("#NextAdminRight.Visible", showPagination);
        if (showPagination) {
            commands.set("#AdminRightPageLabel.Text", "Page " + (adminRightPage + 1) + "/" + totalPages);
        }
    }

    // ── Actions ──

    private void addRow(int row) {
        SegmentView view = getAvailableRow(row);
        if (view == null) {
            return;
        }
        // Enable the segment in this tab's chain.
        // New blocks automatically use the global default separator
        // (set by the last SepConfirm action) via getSeparatorAfter fallback.
        preferences.enable(viewerUuid, tabEntityType(), view.key());
    }

    private void removeRow(int row) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }
        // Don't allow removing required segments
        if (adminConfig.isRequired(view.key())) {
            return;
        }
        preferences.disable(viewerUuid, tabEntityType(), view.key());
    }

    private void clearChain() {
        List<SegmentKey> filteredKeys = getFilteredKeys();
        preferences.disableAll(viewerUuid, tabEntityType(), filteredKeys);
        // Re-enable required segments after clearing
        for (SegmentKey key : filteredKeys) {
            if (adminConfig.isRequired(key)) {
                preferences.enable(viewerUuid, tabEntityType(), key);
            }
        }
    }

    private void moveRow(int row, int delta) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }
        List<SegmentKey> filteredKeys = getFilteredKeys();
        preferences.move(viewerUuid, tabEntityType(), view.key(), delta, filteredKeys, getDefaultComparator());
    }

    /** Move a segment from the left (available) column to the right (required) column. */
    private void enableAdminRow(int row) {
        List<SegmentView> leftList = getAdminLeftList();
        int index = adminLeftPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= leftList.size()) {
            return;
        }
        adminConfig.setRequired(leftList.get(index).key(), true);
    }

    /** Move a segment from the right (required) column back to the left (available) column. */
    private void disableAdminRow(int row) {
        List<SegmentView> rightList = getAdminRightList();
        int index = adminRightPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= rightList.size()) {
            return;
        }
        adminConfig.setRequired(rightList.get(index).key(), false);
    }

    private List<SegmentView> getAdminLeftList() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerFilter = adminFilter.isBlank() ? null : adminFilter.toLowerCase();
        List<SegmentView> left = new ArrayList<>();
        for (SegmentView view : getAllSegmentViews()) {
            if (lowerFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerFilter)) {
                    continue;
                }
            }
            if (!adminConfig.isRequired(view.key())) {
                left.add(view);
            }
        }
        return left;
    }

    private List<SegmentView> getAdminRightList() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerFilter = adminFilter.isBlank() ? null : adminFilter.toLowerCase();
        List<SegmentView> right = new ArrayList<>();
        for (SegmentView view : getAllSegmentViews()) {
            if (lowerFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerFilter)) {
                    continue;
                }
            }
            if (adminConfig.isRequired(view.key())) {
                right.add(view);
            }
        }
        return right;
    }

    private SegmentView getAvailableRow(int row) {
        List<SegmentView> list = getAvailableViews();
        int start = availPage * AVAIL_PAGE_SIZE;
        int index = start + row;
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private SegmentView getChainRow(int row) {
        List<SegmentView> list = getChainViews();
        int start = chainPage * CHAIN_PAGE_SIZE;
        int index = start + row;
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    // ── Data Views ──

    /**
     * Returns the list of segment keys visible in the current tab, used for
     * preference queries. This filters the full registry by the active tab's
     * target type, so that chain/available lists only show relevant segments.
     */
    private List<SegmentKey> getFilteredKeys() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        List<SegmentKey> filtered = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            if (isSegmentVisibleForActiveTab(entry.getValue())) {
                filtered.add(entry.getKey());
            }
        }
        return filtered;
    }

    /**
     * Returns the list of segment keys matching the given entity type,
     * independent of the currently active tab. Used by the Save handler to
     * snapshot both NPC and Player chains with the correct key subsets.
     */
    private List<SegmentKey> getFilteredKeysForEntityType(String entityType) {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        List<SegmentKey> filtered = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            SegmentTarget target = entry.getValue().target();
            if (target == SegmentTarget.ALL) {
                filtered.add(entry.getKey());
            } else if (ENTITY_TYPE_NPCS.equals(entityType) && target == SegmentTarget.NPCS) {
                filtered.add(entry.getKey());
            } else if (ENTITY_TYPE_PLAYERS.equals(entityType) && target == SegmentTarget.PLAYERS) {
                filtered.add(entry.getKey());
            }
        }
        return filtered;
    }

    private List<SegmentView> getAvailableViews() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        List<SegmentKey> filteredKeys = getFilteredKeys();
        List<SegmentKey> ordered = preferences.getAvailable(viewerUuid, tabEntityType(), filteredKeys, getDefaultComparator());
        List<SegmentView> views = new ArrayList<>();
        String lowerFilter = filter.isBlank() ? null : filter.toLowerCase();
        for (SegmentKey key : ordered) {
            NameplateRegistry.Segment segment = segments.get(key);
            if (segment == null) {
                continue;
            }
            SegmentView view = toView(key, segment);
            if (lowerFilter != null && !matchesFilter(view, segment, lowerFilter)) {
                continue;
            }
            views.add(view);
        }
        return views;
    }

    private List<SegmentView> getChainViews() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        List<SegmentKey> filteredKeys = getFilteredKeys();
        List<SegmentKey> ordered = preferences.getChain(viewerUuid, tabEntityType(), filteredKeys, getDefaultComparator());

        // Ensure required segments are always in the chain
        boolean chainChanged = false;
        for (SegmentKey key : filteredKeys) {
            if (adminConfig.isRequired(key) && !ordered.contains(key)) {
                // Force-enable the required segment
                preferences.enable(viewerUuid, tabEntityType(), key);
                chainChanged = true;
            }
        }
        if (chainChanged) {
            ordered = preferences.getChain(viewerUuid, tabEntityType(), filteredKeys, getDefaultComparator());
        }

        List<SegmentView> views = new ArrayList<>();
        for (SegmentKey key : ordered) {
            NameplateRegistry.Segment segment = segments.get(key);
            if (segment == null) {
                continue;
            }
            views.add(toView(key, segment));
        }
        return views;
    }

    /**
     * Returns views for ALL registered segments (across all targets), sorted alphabetically.
     * Used by the admin panel to list every segment regardless of tab context.
     */
    private List<SegmentView> getAllSegmentViews() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<SegmentKey, NameplateRegistry.Segment>> entries = new ArrayList<>(segments.entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<SegmentKey, NameplateRegistry.Segment> e) -> e.getValue().pluginName())
                .thenComparing(e -> e.getValue().displayName()));

        List<SegmentView> views = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : entries) {
            views.add(toView(entry.getKey(), entry.getValue()));
        }
        return views;
    }

    private static SegmentView toView(SegmentKey key, NameplateRegistry.Segment segment) {
        return new SegmentView(
                key,
                segment.displayName(),
                segment.pluginName(),
                formatAuthorWithTarget(segment),
                segment.target().getLabel(),
                segment.example());
    }

    private static boolean matchesFilter(SegmentView view, NameplateRegistry.Segment segment, String lowerFilter) {
        return view.displayName().toLowerCase().contains(lowerFilter)
                || view.modName().toLowerCase().contains(lowerFilter)
                || view.author().toLowerCase().contains(lowerFilter)
                || segment.target().getLabel().toLowerCase().contains(lowerFilter)
                || segment.pluginId().toLowerCase().contains(lowerFilter);
    }

    private static String formatAuthorWithTarget(NameplateRegistry.Segment segment) {
        if (segment.target() == SegmentTarget.ALL) {
            return segment.pluginAuthor();
        }
        return segment.pluginAuthor() + " [" + segment.target().getLabel() + "]";
    }

    private Comparator<SegmentKey> getDefaultComparator() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        return Comparator
                .comparing((SegmentKey k) -> segments.get(k).pluginName())
                .thenComparing(k -> segments.get(k).displayName());
    }

    private String buildPreview(List<SegmentView> views) {
        if (views.isEmpty()) {
            return "(no blocks enabled)";
        }

        // Build the full preview string with per-block separators and bracketed names.
        // The preview bar is horizontally scrollable, so no truncation is needed.
        StringBuilder builder = new StringBuilder();
        SegmentView prevView = null;
        for (SegmentView view : views) {
            if (prevView != null) {
                builder.append(preferences.getSeparatorAfter(viewerUuid, tabEntityType(), prevView.key()));
            }
            builder.append('[').append(view.displayName()).append(']');
            prevView = view;
        }
        return builder.toString();
    }

    private static String truncateModName(String name) {
        if (name == null || name.length() <= MOD_NAME_MAX_LENGTH) {
            return name;
        }
        return name.substring(0, MOD_NAME_MAX_LENGTH - 1) + "...";
    }

    private int parseRowIndex(String action, String prefix) {
        try {
            return Integer.parseInt(action.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Records ──

    private record SegmentView(SegmentKey key, String displayName, String modName, String author, String targetLabel, String example) {
    }

    private record UiState(ActiveTab activeTab, String filter, int availPage, int chainPage, int adminLeftPage, int adminRightPage) {
    }

    static final class SettingsData {
        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec
                .builder(SettingsData.class, SettingsData::new)
                .append(new KeyedCodec<>("@Filter", Codec.STRING),
                        (SettingsData data, String value) -> data.filter = value,
                        (SettingsData data) -> data.filter)
                .add()
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (SettingsData data, String value) -> data.action = value,
                        (SettingsData data) -> data.action)
                .add()
                .append(new KeyedCodec<>("@Offset", Codec.STRING),
                        (SettingsData data, String value) -> data.offset = value,
                        (SettingsData data) -> data.offset)
                .add()
                .append(new KeyedCodec<>("@AdminFilter", Codec.STRING),
                        (SettingsData data, String value) -> data.adminFilter = value,
                        (SettingsData data) -> data.adminFilter)
                .add()
                .append(new KeyedCodec<>("@SepText", Codec.STRING),
                        (SettingsData data, String value) -> data.sepText = value,
                        (SettingsData data) -> data.sepText)
                .add()
                .append(new KeyedCodec<>("@PrefixText", Codec.STRING),
                        (SettingsData data, String value) -> data.prefixText = value,
                        (SettingsData data) -> data.prefixText)
                .add()
                .append(new KeyedCodec<>("@SuffixText", Codec.STRING),
                        (SettingsData data, String value) -> data.suffixText = value,
                        (SettingsData data) -> data.suffixText)
                .add()
                .append(new KeyedCodec<>("@BarEmptyText", Codec.STRING),
                        (SettingsData data, String value) -> data.barEmptyText = value,
                        (SettingsData data) -> data.barEmptyText)
                .add()
                .build();

        String filter;
        String action;
        String offset;
        String adminFilter;
        String sepText;
        String prefixText;
        String suffixText;
        String barEmptyText;
    }
}
