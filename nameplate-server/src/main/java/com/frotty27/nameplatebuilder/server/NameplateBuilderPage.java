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

final class NameplateBuilderPage extends InteractiveCustomUIPage<SettingsData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHAIN_PAGE_SIZE = 4;
    private static final int AVAIL_PAGE_SIZE = 8;
    private static final int ADMIN_PAGE_SIZE = 7;
    private static final int MOD_NAME_MAX_LENGTH = 24;

    private static final String ENTITY_TYPE_GLOBAL = "*";

    static final String ENTITY_TYPE_NPCS = "_npcs";

    static final String ENTITY_TYPE_PLAYERS = "_players";


    private static final String COLOR_REQUIRED = "#3d3820";

    private static final String COLOR_CHAIN_ACTIVE = "#1a3828";

    private static final String COLOR_CHAIN_EMPTY = "#1a2840";

    private static final String COLOR_AVAIL_DEFAULT = "#162236";

    private static final String COLOR_BUILTIN_AVAIL = "#2a1f3d";

    private static final String COLOR_VARIANT_SELECTED = "#2d6b3f";

    private static final int MAX_VARIANT_OPTIONS = 4;

    private static final String COLOR_DISABLED = "#3d2020";

    private static final int DISABLED_PAGE_SIZE = 16;



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
    private AdminSubTab adminSubTab = AdminSubTab.REQUIRED;
    private String adminDisFilter = "";
    private String adminServerName = "";
    private int adminDisLeftPage = 0;
    private int adminDisRightPage = 0;
    private int disabledPage = 0;


    private String pendingOffsetInput = null;


    private String saveMessage = null;
    private boolean saveMessageSuccess = false;


    private int editingSepIndex = -1;
    private String sepText = "";


    private String pendingSepInput = null;


    private SegmentKey editingVariantKey = null;


    private int pendingVariant = 0;


    private int originalVariant = 0;

    private String originalPrefix = "";

    private String originalSuffix = "";

    private String originalBarEmpty = "";


    private String pendingPrefixInput = null;


    private String pendingSuffixInput = null;


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
            this.activeTab = state.activeTab();

            if (this.activeTab == ActiveTab.ADMIN && !isAdmin) {
                this.activeTab = ActiveTab.NPCS;
            }
            this.filter = state.filter();
            this.availPage = state.availPage();
            this.chainPage = state.chainPage();
            this.adminLeftPage = state.adminLeftPage();
            this.adminRightPage = state.adminRightPage();
            this.adminSubTab = state.adminSubTab() != null ? state.adminSubTab() : AdminSubTab.REQUIRED;
            this.adminDisLeftPage = state.adminDisLeftPage();
            this.adminDisRightPage = state.adminDisRightPage();
            this.disabledPage = state.disabledPage();
        }
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, @NonNull Store<EntityStore> store) {
        commands.append("Pages/NameplateBuilder_Editor.ui");
        commands.set("#FilterField.Value", filter);
        commands.set("#AdminFilterField.Value", adminFilter);


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#FilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Filter", "#FilterField.Value"),
                false);


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#AdminFilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@AdminFilter", "#AdminFilterField.Value"),
                false);


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#OffsetField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Offset", "#OffsetField.Value"),
                false);


        bindAction(events, "#NavGeneral", "NavGeneral");
        bindAction(events, "#NavNpcs", "NavNpcs");
        bindAction(events, "#NavPlayers", "NavPlayers");
        bindAction(events, "#NavAdmin", "NavAdmin");
        bindAction(events, "#EnableToggle", "ToggleEnable");


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


        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainBlock" + i + "Left", "Left_" + i);
            bindAction(events, "#ChainBlock" + i + "Right", "Right_" + i);
            bindAction(events, "#ChainBlock" + i + "Remove", "Remove_" + i);
        }




        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainSep" + i, "EditSep_" + i);
        }


        for (int i = 0; i < AVAIL_PAGE_SIZE; i++) {
            bindAction(events, "#AvailBlock" + i + "Add", "Add_" + i);
        }


        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminEnable" + i, "AdminEnable_" + i);
        }


        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminDisable" + i, "AdminDisable_" + i);
        }


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#AdminDisFilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@AdminDisFilter", "#AdminDisFilterField.Value"),
                false);


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#AdminServerNameField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@AdminServerName", "#AdminServerNameField.Value"),
                false);


        bindAction(events, "#AdminSubRequired", "AdminSubRequired");
        bindAction(events, "#AdminSubDisabled", "AdminSubDisabled");
        bindAction(events, "#AdminSubSettings", "AdminSubSettings");
        bindAction(events, "#NavAdminDisabled", "NavAdminDisabled");
        bindAction(events, "#NavAdminSettings", "NavAdminSettings");


        bindAction(events, "#SaveButtonAdminSettings", "SaveAdminSettings");
        bindAction(events, "#ResetButtonAdminSettings", "ResetAdminSettings");
        bindAction(events, "#AdminWelcomeToggle", "ToggleAdminWelcome");


        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminDisDisable" + i, "AdminDisDisable_" + i);
        }
        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            bindAction(events, "#AdminDisEnable" + i, "AdminDisEnable_" + i);
        }
        bindAction(events, "#ResetButtonAdminDis", "ResetAdminDis");
        bindAction(events, "#SaveButtonAdminDis", "SaveAdminDis");


        bindAction(events, "#PrevAdminDisLeft", "PrevAdminDisLeft");
        bindAction(events, "#NextAdminDisLeft", "NextAdminDisLeft");
        bindAction(events, "#PrevAdminDisRight", "PrevAdminDisRight");
        bindAction(events, "#NextAdminDisRight", "NextAdminDisRight");


        bindAction(events, "#NavDisabled", "NavDisabled");
        bindAction(events, "#PrevDisabled", "PrevDisabled");
        bindAction(events, "#NextDisabled", "NextDisabled");


        bindAction(events, "#WelcomeToggle", "ToggleWelcome");


        bindAction(events, "#ClearChainButton", "ClearChain");


        bindAction(events, "#LookToggle", "ToggleLook");


        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#SepTextField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@SepText", "#SepTextField.Value"),
                false);
        bindAction(events, "#SepConfirm", "SepConfirm");
        bindAction(events, "#SepCancel", "SepCancel");


        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainBlock" + i + "Format", "Format_" + i);
        }


        for (int i = 0; i < MAX_VARIANT_OPTIONS; i++) {
            bindAction(events, "#Variant" + i, "VariantSelect_" + i);
        }
        bindAction(events, "#VariantCancel", "VariantCancel");
        bindAction(events, "#VariantConfirm", "VariantConfirm");


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


        if (data.offset == null) {
            pendingOffsetInput = null;
        }


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

        if (data.adminDisFilter != null) {
            adminDisFilter = data.adminDisFilter.trim();
            adminDisLeftPage = 0;
            adminDisRightPage = 0;
        }

        if (data.adminServerName != null) {
            adminServerName = data.adminServerName;

            if (data.action == null && data.filter == null && data.offset == null && data.adminFilter == null
                    && data.adminDisFilter == null && data.sepText == null && data.prefixText == null
                    && data.suffixText == null && data.barEmptyText == null) {
                return;
            }
        }

        if (data.sepText != null) {
            sepText = data.sepText;
            pendingSepInput = data.sepText;


            if (data.action == null && data.filter == null && data.offset == null && data.adminFilter == null
                    && data.prefixText == null && data.suffixText == null && data.barEmptyText == null) {
                return;
            }
        }


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
                    String clamped = data.barEmptyText.length() > 1
                            ? data.barEmptyText.substring(0, 1) : data.barEmptyText;
                    pendingBarEmptyInput = clamped;
                    preferences.setBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey, clamped);
                }
            }

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
                    adminSubTab = AdminSubTab.REQUIRED;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdminDisabled" -> {
                if (isAdmin) {
                    switchTab(ActiveTab.ADMIN);
                    adminSubTab = AdminSubTab.DISABLED;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdminSettings" -> {
                if (isAdmin) {
                    switchTab(ActiveTab.ADMIN);
                    adminSubTab = AdminSubTab.SETTINGS;
                    adminServerName = adminConfig.getServerName();
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "AdminSubRequired" -> {
                adminSubTab = AdminSubTab.REQUIRED;
                sendUpdate(buildUpdate());
                return;
            }
            case "AdminSubDisabled" -> {
                adminSubTab = AdminSubTab.DISABLED;
                sendUpdate(buildUpdate());
                return;
            }
            case "AdminSubSettings" -> {
                adminSubTab = AdminSubTab.SETTINGS;
                adminServerName = adminConfig.getServerName();
                sendUpdate(buildUpdate());
                return;
            }
            case "SaveAdminSettings" -> {
                if (isAdmin) {
                    try {
                        adminConfig.setServerName(adminServerName);
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
            case "ResetAdminSettings" -> {
                if (isAdmin) {
                    adminServerName = "";
                    adminConfig.setServerName("");
                    adminConfig.setWelcomeMessagesEnabled(false);
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleAdminWelcome" -> {
                if (isAdmin) {
                    adminConfig.setWelcomeMessagesEnabled(!adminConfig.isWelcomeMessagesEnabled());
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NavDisabled" -> {
                switchTab(ActiveTab.DISABLED);
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleEnable" -> {

                if (areAllSegmentsDisabled()) {
                    sendUpdate(buildUpdate());
                    return;
                }
                boolean current = preferences.isNameplatesEnabled(viewerUuid);
                preferences.setNameplatesEnabled(viewerUuid, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleWelcome" -> {
                boolean current = preferences.isShowWelcomeMessage(viewerUuid);
                preferences.setShowWelcomeMessage(viewerUuid, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "Close" -> {
                close();
                return;
            }
            case "Save" -> {



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
                    adminConfig.clearAllRequired();
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "SaveAdminDis" -> {
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
            case "ResetAdminDis" -> {
                if (isAdmin) {
                    adminConfig.clearAllDisabled();
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
            case "PrevAdminDisLeft" -> { adminDisLeftPage = Math.max(0, adminDisLeftPage - 1); sendUpdate(buildUpdate()); return; }
            case "NextAdminDisLeft" -> { adminDisLeftPage++; sendUpdate(buildUpdate()); return; }
            case "PrevAdminDisRight" -> { adminDisRightPage = Math.max(0, adminDisRightPage - 1); sendUpdate(buildUpdate()); return; }
            case "NextAdminDisRight" -> { adminDisRightPage++; sendUpdate(buildUpdate()); return; }
            case "PrevDisabled" -> { disabledPage = Math.max(0, disabledPage - 1); sendUpdate(buildUpdate()); return; }
            case "NextDisabled" -> { disabledPage++; sendUpdate(buildUpdate()); return; }
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


        if (data.action.startsWith("EditSep_")) {
            int sepIndex = parseRowIndex(data.action, "EditSep_");
            List<SegmentView> chain = getChainViews();
            int absoluteIndex = chainPage * CHAIN_PAGE_SIZE + sepIndex;
            if (absoluteIndex >= 0 && absoluteIndex < chain.size()) {
                editingSepIndex = sepIndex;
                SegmentKey blockKey = chain.get(absoluteIndex).key();
                sepText = preferences.getSeparatorAfter(viewerUuid, tabEntityType(), blockKey);

                pendingSepInput = null;
            }
            sendUpdate(buildUpdate());
            return;
        }


        if ("SepConfirm".equals(data.action)) {
            if (editingSepIndex >= 0) {
                List<SegmentView> chain = getChainViews();
                int absoluteIndex = chainPage * CHAIN_PAGE_SIZE + editingSepIndex;
                if (absoluteIndex >= 0 && absoluteIndex < chain.size()) {
                    SegmentKey blockKey = chain.get(absoluteIndex).key();
                    String entityType = tabEntityType();



                    String oldDefault = preferences.getSeparator(viewerUuid, entityType);
                    for (SegmentView cv : chain) {
                        if (!cv.key().equals(blockKey)) {
                            String existing = preferences.getSeparatorAfter(viewerUuid, entityType, cv.key());
                            if (existing.equals(oldDefault)) {

                                preferences.setSeparatorAfter(viewerUuid, entityType, cv.key(), existing);
                            }
                        }
                    }

                    preferences.setSeparatorAfter(viewerUuid, entityType, blockKey, sepText);

                    preferences.setSeparator(viewerUuid, entityType, sepText);
                }
            }
            editingSepIndex = -1;
            sepText = "";
            pendingSepInput = null;
            sendUpdate(buildUpdate());
            return;
        }


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


        if (data.action.startsWith("AdminDisDisable_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminDisDisable_");
                disableAdminDisRow(row);
            }
            sendUpdate(buildUpdate());
            return;
        }
        if (data.action.startsWith("AdminDisEnable_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminDisEnable_");
                enableAdminDisRow(row);
            }
            sendUpdate(buildUpdate());
            return;
        }


        if (data.action.startsWith("Format_")) {
            int row = parseRowIndex(data.action, "Format_");
            SegmentView view = getChainRow(row);
            if (view != null) {
                NameplateRegistry.Segment segment = registry.getSegments().get(view.key());
                if (segment != null && segment.variants().size() > 1) {
                    editingVariantKey = view.key();

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


        if (data.action.startsWith("VariantSelect_")) {
            int variantIndex = parseRowIndex(data.action, "VariantSelect_");
            if (editingVariantKey != null && variantIndex >= 0) {

                if (variantIndex == pendingVariant) {
                    sendUpdate(buildUpdate());
                    return;
                }
                pendingVariant = variantIndex;
            }
            sendUpdate(buildUpdate());
            return;
        }


        if ("VariantConfirm".equals(data.action)) {
            if (editingVariantKey != null) {
                preferences.setSelectedVariant(viewerUuid, tabEntityType(), editingVariantKey, pendingVariant);

            }
            editingVariantKey = null;
            pendingPrefixInput = null;
            pendingSuffixInput = null;
            pendingBarEmptyInput = null;
            sendUpdate(buildUpdate());
            return;
        }


        if ("VariantCancel".equals(data.action)) {
            if (editingVariantKey != null) {

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


        String normalized = rawOffset.replace(',', '.').trim();



        if (!normalized.isEmpty() && !isValidOffsetFragment(normalized)) {

            pendingOffsetInput = null;
            return;
        }


        pendingOffsetInput = normalized;


        try {
            double value = Double.parseDouble(normalized);
            preferences.setOffset(viewerUuid, ENTITY_TYPE_GLOBAL, value);
        } catch (NumberFormatException _) {

        }
    }


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
        adminSubTab = AdminSubTab.REQUIRED;
        adminDisLeftPage = 0;
        adminDisRightPage = 0;
        disabledPage = 0;
        pendingOffsetInput = null;
    }



    private boolean areAllSegmentsDisabled() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) return false;
        for (SegmentKey key : segments.keySet()) {
            if (!adminConfig.isDisabled(key)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSegmentVisibleForActiveTab(NameplateRegistry.Segment segment) {
        if (activeTab == ActiveTab.GENERAL || activeTab == ActiveTab.ADMIN || activeTab == ActiveTab.DISABLED) return false;
        SegmentTarget target = segment.target();
        if (target == SegmentTarget.ALL) return true;
        if (activeTab == ActiveTab.NPCS) return target == SegmentTarget.NPCS;
        if (activeTab == ActiveTab.PLAYERS) return target == SegmentTarget.PLAYERS;
        return false;
    }


    private static boolean isValidOffsetFragment(String s) {
        if (s.isEmpty()) return true;
        boolean hasDot = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-') {
                if (i != 0) return false;
            } else if (c == '.') {
                if (hasDot) return false;
                hasDot = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }


    private UICommandBuilder buildUpdate() {
        UICommandBuilder commands = new UICommandBuilder();
        populateCommands(commands);
        return commands;
    }


    private void populateCommands(UICommandBuilder commands) {

        commands.set("#SepPopupOverlay.Visible", false);
        commands.set("#VariantPopupOverlay.Visible", false);


        commands.set("#NavGeneral.Text", activeTab == ActiveTab.GENERAL ? "> Settings" : "  Settings");
        commands.set("#NavNpcs.Text", activeTab == ActiveTab.NPCS ? "> NPCs" : "  NPCs");
        commands.set("#NavPlayers.Text", activeTab == ActiveTab.PLAYERS ? "> Players" : "  Players");
        commands.set("#NavDisabled.Text", activeTab == ActiveTab.DISABLED ? "> Disabled" : "  Disabled");


        commands.set("#AdminSection.Visible", isAdmin);
        if (isAdmin) {
            boolean adminReq = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.REQUIRED;
            boolean adminDis = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.DISABLED;
            boolean adminSet = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.SETTINGS;
            commands.set("#NavAdmin.Text", adminReq ? "> Required" : "  Required");
            commands.set("#NavAdminDisabled.Text", adminDis ? "> Disabled" : "  Disabled");
            commands.set("#NavAdminSettings.Text", adminSet ? "> Settings" : "  Settings");
        }


        commands.set("#TabGeneral.Visible", activeTab == ActiveTab.GENERAL);
        commands.set("#TabEditor.Visible", activeTab == ActiveTab.NPCS || activeTab == ActiveTab.PLAYERS);
        commands.set("#TabAdmin.Visible", activeTab == ActiveTab.ADMIN);
        commands.set("#TabDisabled.Visible", activeTab == ActiveTab.DISABLED);


        if (activeTab == ActiveTab.GENERAL) {

            boolean allDisabled = areAllSegmentsDisabled();

            boolean enabled = !allDisabled && preferences.isNameplatesEnabled(viewerUuid);
            commands.set("#EnableToggle.Text", enabled ? "  [X]  " : "  [ ]  ");

            commands.set("#EnableToggle.Style.Default.Background", allDisabled ? "#1a1a1a" : "#1a2840");
            commands.set("#EnableToggle.Style.Hovered.Background", allDisabled ? "#1a1a1a" : "#253a55");
            commands.set("#AllDisabledNotice.Visible", allDisabled);

            boolean lookOnly = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE_GLOBAL);
            commands.set("#LookToggle.Text", lookOnly ? "  [X]  " : "  [ ]  ");

            boolean welcome = preferences.isShowWelcomeMessage(viewerUuid);
            commands.set("#WelcomeToggle.Text", welcome ? "  [X]  " : "  [ ]  ");


            if (pendingOffsetInput != null) {
                commands.set("#OffsetField.Value", pendingOffsetInput);
            } else {
                double offset = preferences.getOffset(viewerUuid, ENTITY_TYPE_GLOBAL);
                commands.set("#OffsetField.Value", offset == 0.0 ? "0.0" : String.valueOf(offset));
            }


            commands.set("#SaveMessageGeneral.Visible", saveMessage != null);
            if (saveMessage != null) {
                commands.set("#SaveMessageGeneral.Text", saveMessage);
                commands.set("#SaveMessageGeneral.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
            }
            return;
        }


        if (activeTab == ActiveTab.ADMIN) {
            commands.set("#AdminFilterField.Value", adminFilter);


            commands.set("#AdminRequiredContent.Visible", adminSubTab == AdminSubTab.REQUIRED);
            commands.set("#AdminDisabledContent.Visible", adminSubTab == AdminSubTab.DISABLED);
            commands.set("#AdminSettingsContent.Visible", adminSubTab == AdminSubTab.SETTINGS);


            boolean reqActive = adminSubTab == AdminSubTab.REQUIRED;
            commands.set("#AdminSubRequired.Style.Default.Background", reqActive ? "#8b6b2f" : "#1a2840");
            commands.set("#AdminSubRequired.Style.Hovered.Background", reqActive ? "#8b6b2f" : "#a07038");
            commands.set("#AdminSubRequired.Style.Pressed.Background", reqActive ? "#8b6b2f" : "#704b24");


            boolean disActive = adminSubTab == AdminSubTab.DISABLED;
            commands.set("#AdminSubDisabled.Style.Default.Background", disActive ? "#8b3a3a" : "#1a2840");
            commands.set("#AdminSubDisabled.Style.Hovered.Background", disActive ? "#8b3a3a" : "#9b4a4a");
            commands.set("#AdminSubDisabled.Style.Pressed.Background", disActive ? "#8b3a3a" : "#7b2a2a");


            boolean setActive = adminSubTab == AdminSubTab.SETTINGS;
            commands.set("#AdminSubSettings.Style.Default.Background", setActive ? "#2a4a6b" : "#1a2840");
            commands.set("#AdminSubSettings.Style.Hovered.Background", setActive ? "#2a4a6b" : "#3a5a7b");
            commands.set("#AdminSubSettings.Style.Pressed.Background", setActive ? "#2a4a6b" : "#1a3a5b");

            if (adminSubTab == AdminSubTab.REQUIRED) {
                fillAdmin(commands);
            } else if (adminSubTab == AdminSubTab.DISABLED) {
                commands.set("#AdminDisFilterField.Value", adminDisFilter);
                fillAdminDisabled(commands);
            } else if (adminSubTab == AdminSubTab.SETTINGS) {
                commands.set("#AdminServerNameField.Value", adminServerName);
                boolean welcomeEnabled = adminConfig.isWelcomeMessagesEnabled();
                commands.set("#AdminWelcomeToggle.Text", welcomeEnabled ? "  [X]  " : "  [ ]  ");
            }


            String saveMsgId = switch (adminSubTab) {
                case REQUIRED -> "#SaveMessageAdmin";
                case DISABLED -> "#SaveMessageAdminDis";
                case SETTINGS -> "#SaveMessageAdminSettings";
            };
            commands.set(saveMsgId + ".Visible", saveMessage != null);
            if (saveMessage != null) {
                commands.set(saveMsgId + ".Text", saveMessage);
                commands.set(saveMsgId + ".Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
            }
            return;
        }


        if (activeTab == ActiveTab.DISABLED) {
            fillDisabledTab(commands);
            return;
        }


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


        commands.set("#SaveMessageEditor.Visible", saveMessage != null);
        if (saveMessage != null) {
            commands.set("#SaveMessageEditor.Text", saveMessage);
            commands.set("#SaveMessageEditor.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
        }


        boolean showSepPopup = editingSepIndex >= 0;
        commands.set("#SepPopupOverlay.Visible", showSepPopup);
        if (showSepPopup && pendingSepInput == null) {


            commands.set("#SepTextField.Value", sepText);
        }


        boolean showVariantPopup = editingVariantKey != null;
        commands.set("#VariantPopupOverlay.Visible", showVariantPopup);
        if (showVariantPopup) {
            NameplateRegistry.Segment varSeg = registry.getSegments().get(editingVariantKey);
            if (varSeg != null) {
                commands.set("#VariantSegmentName.Text", varSeg.displayName());
                List<String> variants = varSeg.variants();

                String barEmptyChar = pendingBarEmptyInput != null
                        ? pendingBarEmptyInput
                        : preferences.getBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey);

                for (int vi = 0; vi < MAX_VARIANT_OPTIONS; vi++) {
                    boolean vVisible = vi < variants.size();
                    commands.set("#Variant" + vi + ".Visible", vVisible);
                    if (vVisible) {
                        String variantName = variants.get(vi);

                        if (barEmptyChar.length() == 1) {
                            int pStart = variantName.indexOf('(');
                            int pEnd = variantName.lastIndexOf(')');
                            if (pStart >= 0 && pEnd > pStart) {
                                String inside = variantName.substring(pStart + 1, pEnd);
                                inside = inside.replace('-', barEmptyChar.charAt(0));
                                variantName = variantName.substring(0, pStart + 1) + inside + variantName.substring(pEnd);
                            }
                        }
                        boolean selected = vi == pendingVariant;
                        commands.set("#Variant" + vi + ".Text", selected ? "> " + variantName : "  " + variantName);


                        commands.set("#Variant" + vi + ".Style.Default.Background", selected ? COLOR_VARIANT_SELECTED : "#1a2840");
                        commands.set("#Variant" + vi + ".Style.Hovered.Background", selected ? COLOR_VARIANT_SELECTED : "#243650");
                        commands.set("#Variant" + vi + ".Style.Pressed.Background", selected ? COLOR_VARIANT_SELECTED : "#0f1824");
                    }
                }


                boolean showPrefixSuffix = varSeg.supportsPrefixSuffix();
                commands.set("#PrefixSuffixSection.Visible", showPrefixSuffix);
                if (showPrefixSuffix) {

                    if (pendingPrefixInput == null) {
                        String pfx = preferences.getPrefix(viewerUuid, tabEntityType(), editingVariantKey);
                        commands.set("#PrefixField.Value", pfx);
                    }
                    if (pendingSuffixInput == null) {
                        String sfx = preferences.getSuffix(viewerUuid, tabEntityType(), editingVariantKey);
                        commands.set("#SuffixField.Value", sfx);
                    }
                }



                boolean isBarVariant = showPrefixSuffix && pendingVariant >= 0
                        && pendingVariant < variants.size()
                        && variants.get(pendingVariant).startsWith("Bar");
                commands.set("#BarCustomSection.Visible", isBarVariant);
                if (isBarVariant) {
                    String barEmpty = pendingBarEmptyInput != null
                            ? pendingBarEmptyInput
                            : preferences.getBarEmptyChar(viewerUuid, tabEntityType(), editingVariantKey);
                    commands.set("#BarEmptyField.Value", barEmpty);
                }
            }
        }
    }

    private void persistUiState() {
        UI_STATE.put(viewerUuid, new UiState(activeTab, filter, availPage, chainPage,
                adminLeftPage, adminRightPage, adminSubTab, adminDisLeftPage, adminDisRightPage, disabledPage));
    }



    private void fillChain(UICommandBuilder commands, List<SegmentView> chain, int totalPages) {
        boolean hasChain = !chain.isEmpty();
        boolean singleBlock = chain.size() <= 1;
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

                String authorWithTag = "by " + view.author();
                if (!authorWithTag.contains("[")) {
                    authorWithTag += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Author.Text", authorWithTag);

                boolean required = adminConfig.isRequired(view.key());
                NameplateRegistry.Segment chainSeg = registry.getSegments().get(view.key());
                commands.set(prefix + ".Background.Color", required ? COLOR_REQUIRED : COLOR_CHAIN_ACTIVE);


                boolean hasVariants = chainSeg != null && chainSeg.variants().size() > 1;
                int selectedVariant = hasVariants
                        ? preferences.getSelectedVariant(viewerUuid, tabEntityType(), view.key())
                        : 0;


                String example = view.example();
                if (hasVariants && selectedVariant > 0 && selectedVariant < chainSeg.variants().size()) {

                    String variantName = chainSeg.variants().get(selectedVariant);
                    int parenStart = variantName.indexOf('(');
                    int parenEnd = variantName.lastIndexOf(')');
                    if (parenStart >= 0 && parenEnd > parenStart) {
                        example = variantName.substring(parenStart + 1, parenEnd);
                    } else {
                        example = variantName;
                    }

                    if (chainSeg.supportsPrefixSuffix()) {
                        String emptyChar = preferences.getBarEmptyChar(viewerUuid, tabEntityType(), view.key());
                        if (emptyChar.length() == 1) {
                            example = example.replace('-', emptyChar.charAt(0));
                        }
                    }
                }
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }


                commands.set(prefix + "Remove.Visible", !required);


                commands.set(prefix + "Left.Visible", !singleBlock);
                commands.set(prefix + "Right.Visible", !singleBlock);




                boolean centerButtons = (required && !singleBlock) || (!required && singleBlock);
                commands.set(prefix + "TopSpacer.Visible", false);
                commands.set(prefix + "BtnLeftSpacer.Visible", centerButtons);
                commands.set(prefix + "BtnRightSpacer.Visible", centerButtons);



                commands.set(prefix + "Format.Visible", hasVariants);
                if (hasVariants) {
                    boolean variantActive = selectedVariant > 0;
                    commands.set(prefix + "Format.Text", variantActive ? "Formatted" : "Format");
                    commands.set(prefix + "Format.Style.Default.Background", variantActive ? "#2d6b3f" : "#1a2840");
                    commands.set(prefix + "Format.Style.Hovered.Background", variantActive ? "#3a8a50" : "#2d6b3f");
                    commands.set(prefix + "Format.Style.Pressed.Background", variantActive ? "#225530" : "#225530");
                }
            } else {

                commands.set(prefix + ".Background.Color", COLOR_CHAIN_EMPTY);
                commands.set(prefix + "ExampleBar.Visible", false);
                commands.set(prefix + "Remove.Visible", true);
                commands.set(prefix + "Left.Visible", true);
                commands.set(prefix + "Right.Visible", true);
                commands.set(prefix + "TopSpacer.Visible", false);
                commands.set(prefix + "BtnLeftSpacer.Visible", false);
                commands.set(prefix + "BtnRightSpacer.Visible", false);
                commands.set(prefix + "Format.Visible", false);
            }


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



        {
            int lastOnPage = start + CHAIN_PAGE_SIZE - 1;
            boolean trailingSepVisible = lastOnPage < chain.size() && (lastOnPage + 1) < chain.size();
            commands.set("#ChainSep3.Visible", trailingSepVisible);
            if (trailingSepVisible) {
                SegmentKey blockKey = chain.get(lastOnPage).key();
                String sep = preferences.getSeparatorAfter(viewerUuid, tabEntityType(), blockKey);
                String displaySep = sep.isEmpty() ? "." : sep;
                commands.set("#ChainSep3.Text", displaySep);
            }
        }


        boolean showPagination = totalPages > 1;
        commands.set("#PrevChain.Visible", showPagination);
        commands.set("#ChainPageLabel.Visible", showPagination);
        commands.set("#NextChain.Visible", showPagination);
        if (showPagination) {
            commands.set("#ChainPageLabel.Text", "Page " + (chainPage + 1) + "/" + totalPages);
        }
    }



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

                String authorWithTag = "by " + view.author();
                if (!authorWithTag.contains("[")) {

                    authorWithTag += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Author.Text", authorWithTag);

                String example = view.example();
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }


                boolean required = adminConfig.isRequired(view.key());
                NameplateRegistry.Segment availSeg = registry.getSegments().get(view.key());
                boolean builtIn = availSeg != null && availSeg.builtIn();
                String availBg = required ? COLOR_REQUIRED : (builtIn ? COLOR_BUILTIN_AVAIL : COLOR_AVAIL_DEFAULT);
                commands.set(prefix + ".Background.Color", availBg);
            }
        }


        boolean showPagination = totalPages > 1;
        commands.set("#PrevAvail.Visible", showPagination);
        commands.set("#AvailPaginationLabel.Visible", showPagination);
        commands.set("#NextAvail.Visible", showPagination);
        if (showPagination) {
            commands.set("#AvailPaginationLabel.Text", "Page " + (availPage + 1) + "/" + totalPages);
        }
    }



    private void fillAdmin(UICommandBuilder commands) {
        List<SegmentView> allSegments = getAllSegmentViews();
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerAdminFilter = adminFilter.isBlank() ? null : adminFilter.toLowerCase();



        List<SegmentView> leftList = new ArrayList<>();
        List<SegmentView> rightList = new ArrayList<>();
        for (SegmentView view : allSegments) {

            if (adminConfig.isDisabled(view.key())) {
                continue;
            }
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
        commands.set("#AdminLeftPagination.Visible", showPagination);
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
        commands.set("#AdminRightPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#AdminRightPageLabel.Text", "Page " + (adminRightPage + 1) + "/" + totalPages);
        }
    }



    private void fillAdminDisabled(UICommandBuilder commands) {
        List<SegmentView> allSegments = getAllSegmentViews();
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerAdminFilter = adminDisFilter.isBlank() ? null : adminDisFilter.toLowerCase();



        List<SegmentView> leftList = new ArrayList<>();
        List<SegmentView> rightList = new ArrayList<>();
        for (SegmentView view : allSegments) {

            if (adminConfig.isRequired(view.key())) {
                continue;
            }
            if (lowerAdminFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerAdminFilter)) {
                    continue;
                }
            }
            if (adminConfig.isDisabled(view.key())) {
                rightList.add(view);
            } else {
                leftList.add(view);
            }
        }

        fillAdminDisLeft(commands, leftList);
        fillAdminDisRight(commands, rightList);
    }

    private void fillAdminDisLeft(UICommandBuilder commands, List<SegmentView> leftList) {
        boolean hasLeft = !leftList.isEmpty();
        commands.set("#AdminDisLeftEmpty.Visible", !hasLeft);

        int totalPages = Math.max(1, (int) Math.ceil(leftList.size() / (double) ADMIN_PAGE_SIZE));
        if (adminDisLeftPage >= totalPages) adminDisLeftPage = totalPages - 1;

        int start = adminDisLeftPage * ADMIN_PAGE_SIZE;
        int end = Math.min(leftList.size(), start + ADMIN_PAGE_SIZE);

        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            commands.set("#AdminDisLeftRow" + i + ".Visible", visible);
            if (visible) {
                SegmentView view = leftList.get(index);
                String prefix = "#AdminDisLeftBlock" + i;
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
        commands.set("#AdminDisLeftPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#AdminDisLeftPageLabel.Text", "Page " + (adminDisLeftPage + 1) + "/" + totalPages);
        }
    }

    private void fillAdminDisRight(UICommandBuilder commands, List<SegmentView> rightList) {
        boolean hasRight = !rightList.isEmpty();
        commands.set("#AdminDisRightEmpty.Visible", !hasRight);

        int totalPages = Math.max(1, (int) Math.ceil(rightList.size() / (double) ADMIN_PAGE_SIZE));
        if (adminDisRightPage >= totalPages) adminDisRightPage = totalPages - 1;

        int start = adminDisRightPage * ADMIN_PAGE_SIZE;
        int end = Math.min(rightList.size(), start + ADMIN_PAGE_SIZE);

        for (int i = 0; i < ADMIN_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            commands.set("#AdminDisRightRow" + i + ".Visible", visible);
            if (visible) {
                SegmentView view = rightList.get(index);
                String prefix = "#AdminDisRightBlock" + i;
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
        commands.set("#AdminDisRightPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#AdminDisRightPageLabel.Text", "Page " + (adminDisRightPage + 1) + "/" + totalPages);
        }
    }



    private void fillDisabledTab(UICommandBuilder commands) {

        List<SegmentView> disabledViews = new ArrayList<>();
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        for (SegmentView view : getAllSegmentViews()) {
            if (adminConfig.isDisabled(view.key())) {
                disabledViews.add(view);
            }
        }

        boolean hasDisabled = !disabledViews.isEmpty();
        commands.set("#DisabledEmpty.Visible", !hasDisabled);

        int totalPages = Math.max(1, (int) Math.ceil(disabledViews.size() / (double) DISABLED_PAGE_SIZE));
        if (disabledPage >= totalPages) disabledPage = totalPages - 1;

        int start = disabledPage * DISABLED_PAGE_SIZE;
        int end = Math.min(disabledViews.size(), start + DISABLED_PAGE_SIZE);

        for (int i = 0; i < DISABLED_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            String prefix = "#DisabledBlock" + i;
            commands.set(prefix + ".Visible", visible);
            if (visible) {
                SegmentView view = disabledViews.get(index);
                commands.set(prefix + "Label.Text", view.displayName());
                commands.set(prefix + "Sub.Text", truncateModName(view.modName()));
                String authorWithTag = "by " + view.author();
                if (!authorWithTag.contains("[")) {
                    authorWithTag += " [" + view.targetLabel() + "]";
                }
                commands.set(prefix + "Author.Text", authorWithTag);
                String example = view.example();
                boolean hasExample = example != null && !example.isBlank();
                commands.set(prefix + "ExampleBar.Visible", hasExample);
                if (hasExample) {
                    commands.set(prefix + "Example.Text", example);
                }
            }
        }

        boolean showPagination = totalPages > 1;
        commands.set("#PrevDisabled.Visible", showPagination);
        commands.set("#DisabledPageLabel.Visible", showPagination);
        commands.set("#NextDisabled.Visible", showPagination);
        if (showPagination) {
            commands.set("#DisabledPageLabel.Text", "Page " + (disabledPage + 1) + "/" + totalPages);
        }
    }



    private void addRow(int row) {
        SegmentView view = getAvailableRow(row);
        if (view == null) {
            return;
        }



        preferences.enable(viewerUuid, tabEntityType(), view.key());
    }

    private void removeRow(int row) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }

        if (adminConfig.isRequired(view.key())) {
            return;
        }
        preferences.disable(viewerUuid, tabEntityType(), view.key());
    }

    private void clearChain() {
        List<SegmentKey> filteredKeys = getFilteredKeys();
        preferences.disableAll(viewerUuid, tabEntityType(), filteredKeys);

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


    private void enableAdminRow(int row) {
        List<SegmentView> leftList = getAdminLeftList();
        int index = adminLeftPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= leftList.size()) {
            return;
        }
        adminConfig.setRequired(leftList.get(index).key(), true);
    }


    private void disableAdminRow(int row) {
        List<SegmentView> rightList = getAdminRightList();
        int index = adminRightPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= rightList.size()) {
            return;
        }
        adminConfig.setRequired(rightList.get(index).key(), false);
    }


    private void disableAdminDisRow(int row) {
        List<SegmentView> leftList = getAdminDisLeftList();
        int index = adminDisLeftPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= leftList.size()) {
            return;
        }
        adminConfig.setDisabled(leftList.get(index).key(), true);
    }


    private void enableAdminDisRow(int row) {
        List<SegmentView> rightList = getAdminDisRightList();
        int index = adminDisRightPage * ADMIN_PAGE_SIZE + row;
        if (index < 0 || index >= rightList.size()) {
            return;
        }
        adminConfig.setDisabled(rightList.get(index).key(), false);
    }

    private List<SegmentView> getAdminLeftList() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerFilter = adminFilter.isBlank() ? null : adminFilter.toLowerCase();
        List<SegmentView> left = new ArrayList<>();
        for (SegmentView view : getAllSegmentViews()) {

            if (adminConfig.isDisabled(view.key())) {
                continue;
            }
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

            if (adminConfig.isDisabled(view.key())) {
                continue;
            }
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


    private List<SegmentView> getAdminDisLeftList() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerFilter = adminDisFilter.isBlank() ? null : adminDisFilter.toLowerCase();
        List<SegmentView> left = new ArrayList<>();
        for (SegmentView view : getAllSegmentViews()) {

            if (adminConfig.isRequired(view.key())) {
                continue;
            }
            if (lowerFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerFilter)) {
                    continue;
                }
            }
            if (!adminConfig.isDisabled(view.key())) {
                left.add(view);
            }
        }
        return left;
    }


    private List<SegmentView> getAdminDisRightList() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        String lowerFilter = adminDisFilter.isBlank() ? null : adminDisFilter.toLowerCase();
        List<SegmentView> right = new ArrayList<>();
        for (SegmentView view : getAllSegmentViews()) {

            if (adminConfig.isRequired(view.key())) {
                continue;
            }
            if (lowerFilter != null) {
                NameplateRegistry.Segment segment = segments.get(view.key());
                if (segment != null && !matchesFilter(view, segment, lowerFilter)) {
                    continue;
                }
            }
            if (adminConfig.isDisabled(view.key())) {
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




    private List<SegmentKey> getFilteredKeys() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        List<SegmentKey> filtered = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {
            if (isSegmentVisibleForActiveTab(entry.getValue())) {

                if (adminConfig.isDisabled(entry.getKey())) {
                    continue;
                }
                filtered.add(entry.getKey());
            }
        }
        return filtered;
    }


    private List<SegmentKey> getFilteredKeysForEntityType(String entityType) {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        List<SegmentKey> filtered = new ArrayList<>();
        for (Map.Entry<SegmentKey, NameplateRegistry.Segment> entry : segments.entrySet()) {

            if (adminConfig.isDisabled(entry.getKey())) {
                continue;
            }
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


        boolean chainChanged = false;
        for (SegmentKey key : filteredKeys) {
            if (adminConfig.isRequired(key) && !ordered.contains(key)) {

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



}
