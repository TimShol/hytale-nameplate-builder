package com.frotty27.nameplatebuilder.server;

import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class NameplateBuilderPage extends InteractiveCustomUIPage<SettingsData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_CHAIN_BLOCKS = 10;
    private static final int MAX_PREVIEW_TARGETS = 8;
    private static final int AVAIL_PAGE_SIZE = 8;
    private static final int ADMIN_PAGE_SIZE = 7;
    private static final int MOD_NAME_MAX_LENGTH = 24;

    private static final String ENTITY_TYPE_GLOBAL = "*";

    static final String ENTITY_TYPE_NPCS = "_npcs";

    static final String ENTITY_TYPE_PLAYERS = "_players";

    private static final UUID ADMIN_CHAIN_UUID = new UUID(0L, 0L);

    private static final String COLOR_REQUIRED = "#3d3820";

    private static final String COLOR_CHAIN_ACTIVE = "#1a3828";

    private static final String COLOR_CHAIN_EMPTY = "#1a2840";

    private static final String COLOR_AVAIL_DEFAULT = "#162236";

    private static final String COLOR_BUILTIN_AVAIL = "#2a1f3d";

    private static final String COLOR_VARIANT_SELECTED = "#2d6b3f";

    private static final int MAX_VARIANT_OPTIONS = 4;

    private static final int DISABLED_PAGE_SIZE = 16;


    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;
    private final boolean isAdmin;
    private final UUID viewerUuid;
    private final NameplateBuilderPlugin plugin;

    private ActiveTab activeTab = ActiveTab.NPCS;
    private String filter = "";
    private String adminFilter = "";
    private int availPage = 0;
    private int adminLeftPage = 0;
    private int adminRightPage = 0;
    private AdminSubTab adminSubTab = AdminSubTab.REQUIRED;
    private String adminDisFilter = "";
    private String adminServerName = "";
    private int adminDisLeftPage = 0;
    private int adminDisRightPage = 0;
    private int disabledPage = 0;
    private ChainSubTab chainSubTab = ChainSubTab.CHAIN;
    private boolean adminOrderIsNpc = true;
    private int worldPage = 0;
    private int instPage = 0;
    private int adminWorldPage = 0;
    private int adminInstPage = 0;

    private int selectedPreviewTarget = 0;

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

    private boolean npcPickerOpen = false;
    private boolean npcPickerDirty = false;
    private String npcPickerFilter = "";
    private int npcPickerPage = 0;
    private String npcPickerSelectedItem = null;
    private final List<String> npcPickerFiltered = new ArrayList<>();
    private int blacklistPage = 0;
    private String blacklistFilter = "";
    private static final int NPC_PICKER_ROW_COUNT = 10;
    private static final int BLACKLIST_ROW_COUNT = 18;
    private static volatile List<String> cachedNpcIds = null;

    private static final Map<UUID, UiState> UI_STATE = new ConcurrentHashMap<>();

    NameplateBuilderPage(PlayerRef playerRef,
                         UUID viewerUuid,
                         NameplateRegistry registry,
                         NameplatePreferenceStore preferences,
                         AdminConfigStore adminConfig,
                         boolean isAdmin,
                         NameplateBuilderPlugin plugin) {
        super(playerRef,
                CustomPageLifetime.CanDismiss,
                SettingsData.CODEC);
        this.viewerUuid = viewerUuid;
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
        this.isAdmin = isAdmin;
        this.plugin = plugin;

        UiState state = UI_STATE.get(viewerUuid);
        if (state != null) {
            this.activeTab = state.activeTab();

            if (this.activeTab == ActiveTab.ADMIN && !isAdmin) {
                this.activeTab = ActiveTab.NPCS;
            }
            this.filter = state.filter();
            this.availPage = state.availPage();
            this.adminLeftPage = state.adminLeftPage();
            this.adminRightPage = state.adminRightPage();
            this.adminSubTab = state.adminSubTab() != null ? state.adminSubTab() : AdminSubTab.REQUIRED;
            this.adminDisLeftPage = state.adminDisLeftPage();
            this.adminDisRightPage = state.adminDisRightPage();
            this.disabledPage = state.disabledPage();
            this.chainSubTab = state.chainSubTab() != null ? state.chainSubTab() : ChainSubTab.CHAIN;
        }
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, @NonNull Store<EntityStore> store) {
        commands.append("Pages/NameplateBuilder_Editor.ui");
        commands.set("#FilterField.Value", filter);
        commands.set("#AdminFilterField.Value", adminFilter);


        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#FilterField",
                EventData.of("@Filter", "#FilterField.Value"),
                false);


        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#AdminFilterField",
                EventData.of("@AdminFilter", "#AdminFilterField.Value"),
                false);


        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#OffsetField",
                EventData.of("@Offset", "#OffsetField.Value"),
                false);


        bindAction(events, "#NavGeneral", "NavGeneral");
        bindAction(events, "#NavGeneralActive", "NavGeneral");
        bindAction(events, "#NavNpcs", "NavNpcs");
        bindAction(events, "#NavNpcsActive", "NavNpcs");
        bindAction(events, "#NavPlayers", "NavPlayers");
        bindAction(events, "#NavPlayersActive", "NavPlayers");
        bindAction(events, "#NavAdminNpcs", "NavAdminNpcs");
        bindAction(events, "#NavAdminNpcsActive", "NavAdminNpcs");
        bindAction(events, "#NavAdminPlayers", "NavAdminPlayers");
        bindAction(events, "#NavAdminPlayersActive", "NavAdminPlayers");
        bindAction(events, "#EnableToggleOn", "ToggleEnable");
        bindAction(events, "#EnableToggleOff", "ToggleEnable");
        bindAction(events, "#AdminMasterToggleOn", "ToggleAdminMaster");
        bindAction(events, "#AdminMasterToggleOff", "ToggleAdminMaster");
        bindAction(events, "#AdminPlayerChainToggleOn", "ToggleAdminPlayerChain");
        bindAction(events, "#AdminPlayerChainToggleOff", "ToggleAdminPlayerChain");
        bindAction(events, "#AdminNpcChainToggleOn", "ToggleAdminNpcChain");
        bindAction(events, "#AdminNpcChainToggleOff", "ToggleAdminNpcChain");
        bindAction(events, "#LockChainOn", "ToggleLockChain");
        bindAction(events, "#LockChainOff", "ToggleLockChain");


        bindAction(events, "#SaveButton", "Save");
        bindAction(events, "#SaveButtonGeneral", "Save");
        bindAction(events, "#SaveButtonAdmin", "SaveAdmin");
        bindAction(events, "#ResetButtonAdmin", "ResetAdmin");
        bindAction(events, "#CloseButton", "Close");
        bindAction(events, "#PrevAvail", "PrevAvail");
        bindAction(events, "#NextAvail", "NextAvail");
        bindAction(events, "#PrevAdminLeft", "PrevAdminLeft");
        bindAction(events, "#NextAdminLeft", "NextAdminLeft");
        bindAction(events, "#PrevAdminRight", "PrevAdminRight");
        bindAction(events, "#NextAdminRight", "NextAdminRight");


        for (int i = 0; i < MAX_CHAIN_BLOCKS; i++) {
            bindAction(events, "#ChainBlock" + i + "Left", "Left_" + i);
            bindAction(events, "#ChainBlock" + i + "Right", "Right_" + i);
            bindAction(events, "#ChainBlock" + i + "Remove", "Remove_" + i);
        }


        for (int i = 0; i < MAX_CHAIN_BLOCKS; i++) {
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
                CustomUIEventBindingType.ValueChanged,
                "#AdminDisFilterField",
                EventData.of("@AdminDisFilter", "#AdminDisFilterField.Value"),
                false);


        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#AdminServerNameField",
                EventData.of("@AdminServerName", "#AdminServerNameField.Value"),
                false);


        bindAction(events, "#AdminSubTab0", "AdminSubRequired");
        bindAction(events, "#AdminSubTab0Active", "AdminSubRequired");
        bindAction(events, "#AdminSubTab1", "AdminSubDisabled");
        bindAction(events, "#AdminSubTab1Active", "AdminSubDisabled");
        bindAction(events, "#AdminSubTab2", "AdminSubSettings");
        bindAction(events, "#AdminSubTab2Active", "AdminSubSettings");
        bindAction(events, "#NavAdminConfig", "NavAdminConfig");
        bindAction(events, "#NavAdminConfigActive", "NavAdminConfig");

        bindAction(events, "#AdminSubTab3", "AdminSubBlacklist");
        bindAction(events, "#AdminSubTab3Active", "AdminSubBlacklist");
        bindAction(events, "#BlacklistAddBtn", "BlacklistAdd");
        bindAction(events, "#BlacklistRemoveFiltered", "BlacklistRemoveFiltered");
        bindAction(events, "#SaveButtonBlacklist", "SaveBlacklist");
        for (int i = 0; i < BLACKLIST_ROW_COUNT; i++) {
            bindAction(events, "#BlacklistRow" + i + "Remove", "BlacklistRemove_" + i);
        }
        bindAction(events, "#BlacklistFirst", "BlacklistFirst");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#BlacklistFilterField",
                EventData.of("@BlacklistFilter", "#BlacklistFilterField.Value"),
                false);
        bindAction(events, "#BlacklistPrev", "BlacklistPrev");
        bindAction(events, "#BlacklistNext", "BlacklistNext");
        bindAction(events, "#BlacklistLast", "BlacklistLast");

        bindAction(events, "#NpcPickerCancel", "NpcPickerCancel");
        bindAction(events, "#NpcPickerBackdrop", "NpcPickerCancel");
        bindAction(events, "#NpcPickerAdd", "NpcPickerAddConfirm");
        bindAction(events, "#NpcPickerAddAllFiltered", "NpcPickerAddAllFiltered");
        bindAction(events, "#BlacklistClearAll", "BlacklistClearAll");
        for (int i = 0; i < NPC_PICKER_ROW_COUNT; i++) {
            bindAction(events, "#NpcPickerRowBtn" + i, "NpcPickerRow_" + i);
        }
        bindAction(events, "#NpcPickerFirstPage", "NpcPickerFirstPage");
        bindAction(events, "#NpcPickerPrevPage", "NpcPickerPrevPage");
        bindAction(events, "#NpcPickerNextPage", "NpcPickerNextPage");
        bindAction(events, "#NpcPickerLastPage", "NpcPickerLastPage");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#NpcPickerFilter",
                EventData.of("@NpcPickerFilter", "#NpcPickerFilter.Value"),
                false);

        bindAction(events, "#SaveButtonAdminSettings", "SaveAdminSettings");
        bindAction(events, "#ResetButtonAdminSettings", "ResetAdminSettings");
        bindAction(events, "#AdminWelcomeToggleOn", "ToggleAdminWelcome");
        bindAction(events, "#AdminWelcomeToggleOff", "ToggleAdminWelcome");

        for (int i = 0; i < 5; i++) {
            bindAction(events, "#AdminWorldRow" + i + "On", "AdminToggleWorld_" + i);
            bindAction(events, "#AdminWorldRow" + i + "Off", "AdminToggleWorld_" + i);
        }
        bindAction(events, "#AdminWorldFirst", "AdminWorldFirst");
        bindAction(events, "#AdminWorldPrev", "AdminWorldPrev");
        bindAction(events, "#AdminWorldNext", "AdminWorldNext");
        bindAction(events, "#AdminWorldLast", "AdminWorldLast");

        for (int i = 0; i < 5; i++) {
            bindAction(events, "#AdminInstRow" + i + "On", "AdminToggleInst_" + i);
            bindAction(events, "#AdminInstRow" + i + "Off", "AdminToggleInst_" + i);
        }
        bindAction(events, "#AdminInstFirst", "AdminInstFirst");
        bindAction(events, "#AdminInstPrev", "AdminInstPrev");
        bindAction(events, "#AdminInstNext", "AdminInstNext");
        bindAction(events, "#AdminInstLast", "AdminInstLast");

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
        bindAction(events, "#NavDisabledActive", "NavDisabled");
        bindAction(events, "#PrevDisabled", "PrevDisabled");
        bindAction(events, "#NextDisabled", "NextDisabled");


        bindAction(events, "#WelcomeToggleOn", "ToggleWelcome");
        bindAction(events, "#WelcomeToggleOff", "ToggleWelcome");


        bindAction(events, "#ClearChainButton", "ClearChain");


        bindAction(events, "#LookToggleOn", "ToggleLook");
        bindAction(events, "#LookToggleOff", "ToggleLook");


        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SepTextField",
                EventData.of("@SepText", "#SepTextField.Value"),
                false);
        bindAction(events, "#SepConfirm", "SepConfirm");
        bindAction(events, "#SepCancel", "SepCancel");


        for (int i = 0; i < MAX_CHAIN_BLOCKS; i++) {
            bindAction(events, "#ChainBlock" + i + "Format", "Format_" + i);
        }


        for (int i = 0; i < MAX_VARIANT_OPTIONS; i++) {
            bindAction(events, "#Variant" + i, "VariantSelect_" + i);
        }
        bindAction(events, "#VariantCancel", "VariantCancel");
        bindAction(events, "#VariantConfirm", "VariantConfirm");

        bindAction(events, "#SubTabChain", "SubTabChain");
        bindAction(events, "#SubTabChainActive", "SubTabChain");
        bindAction(events, "#SubTabSettings", "SubTabSettings");
        bindAction(events, "#SubTabSettingsActive", "SubTabSettings");
        bindAction(events, "#ChainEnabledOn", "ToggleChainEnabled");
        bindAction(events, "#ChainEnabledOff", "ToggleChainEnabled");
        for (int i = 0; i < 8; i++) {
            bindAction(events, "#ModRow" + i + "On", "ToggleMod_" + i);
            bindAction(events, "#ModRow" + i + "Off", "ToggleMod_" + i);
        }
        for (int i = 0; i < 5; i++) {
            bindAction(events, "#WorldRow" + i + "On", "ToggleWorld_" + i);
            bindAction(events, "#WorldRow" + i + "Off", "ToggleWorld_" + i);
        }
        bindAction(events, "#WorldFirst", "WorldFirst");
        bindAction(events, "#WorldPrev", "WorldPrev");
        bindAction(events, "#WorldNext", "WorldNext");
        bindAction(events, "#WorldLast", "WorldLast");
        for (int i = 0; i < 5; i++) {
            bindAction(events, "#InstRow" + i + "On", "ToggleInst_" + i);
            bindAction(events, "#InstRow" + i + "Off", "ToggleInst_" + i);
        }
        bindAction(events, "#InstFirst", "InstFirst");
        bindAction(events, "#InstPrev", "InstPrev");
        bindAction(events, "#InstNext", "InstNext");
        bindAction(events, "#InstLast", "InstLast");
        bindAction(events, "#SaveButtonSettings", "SaveSettings");

        for (int i = 0; i < MAX_PREVIEW_TARGETS; i++) {
            bindAction(events, "#PreviewTargetBtn" + i, "PreviewTarget_" + i);
            bindAction(events, "#PreviewTargetBtn" + i + "Active", "PreviewTarget_" + i);
        }

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PrefixField",
                EventData.of("@PrefixText", "#PrefixField.Value"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SuffixField",
                EventData.of("@SuffixText", "#SuffixField.Value"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#BarEmptyField",
                EventData.of("@BarEmptyText", "#BarEmptyField.Value"),
                false);

        populateCommands(commands);
    }

    private void bindAction(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action),
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


        if ((!"Save".equals(data.action) && !"SaveAdmin".equals(data.action) && !"SaveSettings".equals(data.action) && !"SaveBlacklist".equals(
                data.action) && !"SaveAdminSettings".equals(data.action) && !"SaveAdminDis".equals(data.action)
                && !"NpcPickerAddAllFiltered".equals(data.action) && !"BlacklistClearAll".equals(data.action)
                && !"BlacklistRemoveFiltered".equals(data.action))) {
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
                    && data.suffixText == null && data.barEmptyText == null && data.npcPickerFilter == null) {
                return;
            }
        }

        if (data.npcPickerFilter != null) {
            String trimmed = data.npcPickerFilter.trim();
            if (!trimmed.equals(npcPickerFilter)) {
                npcPickerFilter = trimmed;
                npcPickerPage = 0;
                npcPickerSelectedItem = null;
                npcPickerDirty = true;
            }
        }

        if (data.blacklistFilter != null) {
            blacklistFilter = data.blacklistFilter.trim();
            blacklistPage = 0;
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
                    preferences.setPrefix(chainViewerUuid(), tabEntityType(), editingVariantKey, data.prefixText);
                }
                if (data.suffixText != null) {
                    pendingSuffixInput = data.suffixText;
                    preferences.setSuffix(chainViewerUuid(), tabEntityType(), editingVariantKey, data.suffixText);
                }
                if (data.barEmptyText != null) {
                    String clamped = data.barEmptyText.length() > 1
                            ? data.barEmptyText.substring(0, 1) : data.barEmptyText;
                    pendingBarEmptyInput = clamped;
                    preferences.setBarEmptyChar(chainViewerUuid(), tabEntityType(), editingVariantKey, clamped);
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

        if (npcPickerOpen && !data.action.startsWith("NpcPicker") && !"BlacklistAdd".equals(data.action)) {
            closeNpcPicker();
        }

        switch (data.action) {
            case "NavGeneral" -> {
                switchTab(ActiveTab.GENERAL);
                sendUpdate(buildUpdate());
                return;
            }
            case "NavPlayers" -> {
                switchTab(ActiveTab.PLAYERS);
                chainSubTab = ChainSubTab.CHAIN;
                sendUpdate(buildUpdate());
                return;
            }
            case "NavNpcs" -> {
                switchTab(ActiveTab.NPCS);
                chainSubTab = ChainSubTab.CHAIN;
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdminNpcs" -> {
                if (isAdmin) {
                    activeTab = ActiveTab.ADMIN;
                    adminSubTab = AdminSubTab.ORDER;
                    adminOrderIsNpc = true;
                    chainSubTab = ChainSubTab.CHAIN;
                    availPage = 0;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdminPlayers" -> {
                if (isAdmin) {
                    activeTab = ActiveTab.ADMIN;
                    adminSubTab = AdminSubTab.ORDER;
                    adminOrderIsNpc = false;
                    chainSubTab = ChainSubTab.CHAIN;
                    availPage = 0;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NavAdminConfig" -> {
                if (isAdmin) {
                    switchTab(ActiveTab.ADMIN);
                    adminSubTab = AdminSubTab.SETTINGS;
                    adminServerName = adminConfig.getServerName();
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleAdminMaster" -> {
                if (isAdmin) {
                    adminConfig.setMasterEnabled(!adminConfig.isMasterEnabled());
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleAdminPlayerChain" -> {
                if (isAdmin) {
                    adminConfig.setPlayerChainEnabled(!adminConfig.isPlayerChainEnabled());
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleAdminNpcChain" -> {
                if (isAdmin) {
                    adminConfig.setNpcChainEnabled(!adminConfig.isNpcChainEnabled());
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleLockChain" -> {
                if (isAdmin) {
                    boolean isNpcTab = activeTab == ActiveTab.NPCS || (activeTab == ActiveTab.ADMIN && adminOrderIsNpc);
                    if (isNpcTab) {
                        adminConfig.setNpcChainLocked(!adminConfig.isNpcChainLocked());
                    } else {
                        adminConfig.setPlayerChainLocked(!adminConfig.isPlayerChainLocked());
                    }
                    adminConfig.save();
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
            case "AdminSubBlacklist" -> {
                adminSubTab = AdminSubTab.BLACKLIST;
                sendUpdate(buildUpdate());
                return;
            }
            case "BlacklistAdd" -> {
                openNpcPicker();
                sendUpdate(buildUpdate());
                return;
            }
            case "NpcPickerCancel" -> {
                closeNpcPicker();
                sendUpdate(buildUpdate());
                return;
            }
            case "NpcPickerAddConfirm" -> {
                if (npcPickerSelectedItem != null) {
                    adminConfig.addBlacklistedNpc(npcPickerSelectedItem);
                    adminConfig.save();
                    closeNpcPicker();
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "NpcPickerAddAllFiltered" -> {
                if (isAdmin && !npcPickerFilter.isEmpty() && !npcPickerFiltered.isEmpty()) {
                    int count = npcPickerFiltered.size();
                    for (String npcId : npcPickerFiltered) {
                        adminConfig.addBlacklistedNpc(npcId);
                    }
                    adminConfig.save();
                    closeNpcPicker();
                    saveMessage = "Added " + count + " NPCs to blacklist!";
                    saveMessageSuccess = true;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "BlacklistClearAll" -> {
                if (isAdmin) {
                    adminConfig.clearBlacklistedNpcs();
                    adminConfig.save();
                    blacklistPage = 0;
                    blacklistFilter = "";
                    saveMessage = "Blacklist cleared!";
                    saveMessageSuccess = true;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "BlacklistRemoveFiltered" -> {
                if (isAdmin && !blacklistFilter.isEmpty()) {
                    String lowerFilter = blacklistFilter.toLowerCase(java.util.Locale.ROOT);
                    List<String> toRemove = new ArrayList<>();
                    for (String npcId : adminConfig.getBlacklistedNpcs()) {
                        if (npcId.toLowerCase(java.util.Locale.ROOT).contains(lowerFilter)) {
                            toRemove.add(npcId);
                        }
                    }
                    for (String npcId : toRemove) {
                        adminConfig.removeBlacklistedNpc(npcId);
                    }
                    adminConfig.save();
                    blacklistPage = 0;
                    saveMessage = "Removed " + toRemove.size() + " NPCs from blacklist!";
                    saveMessageSuccess = true;
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "SaveBlacklist" -> {
                if (isAdmin) {
                    try {
                        adminConfig.save();
                        persistUiState();
                        saveMessage = "Blacklist saved!";
                        saveMessageSuccess = true;
                    } catch (Throwable e) {
                        LOGGER.atWarning().withCause(e).log("Failed to save blacklist");
                        saveMessage = "Could not save blacklist!";
                        saveMessageSuccess = false;
                    }
                }
                sendUpdate(buildUpdate());
                return;
            }
            case "BlacklistFirst" -> { blacklistPage = 0; sendUpdate(buildUpdate()); return; }
            case "BlacklistPrev" -> { blacklistPage = Math.max(0, blacklistPage - 1); sendUpdate(buildUpdate()); return; }
            case "BlacklistNext" -> { blacklistPage++; sendUpdate(buildUpdate()); return; }
            case "BlacklistLast" -> {
                List<String> blacklist = new ArrayList<>(adminConfig.getBlacklistedNpcs());
                int totalBlacklistPages = Math.max(1, (int) Math.ceil(blacklist.size() / (double) BLACKLIST_ROW_COUNT));
                blacklistPage = totalBlacklistPages - 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "NpcPickerFirstPage" -> { npcPickerPage = 0; sendUpdate(buildUpdate()); return; }
            case "NpcPickerPrevPage" -> { npcPickerPage = Math.max(0, npcPickerPage - 1); sendUpdate(buildUpdate()); return; }
            case "NpcPickerNextPage" -> { npcPickerPage++; sendUpdate(buildUpdate()); return; }
            case "NpcPickerLastPage" -> {
                rebuildNpcPickerFiltered();
                int totalNpcPages = Math.max(1, (int) Math.ceil(npcPickerFiltered.size() / (double) NPC_PICKER_ROW_COUNT));
                npcPickerPage = totalNpcPages - 1;
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
            case "AdminWorldFirst" -> { adminWorldPage = 0; sendUpdate(buildUpdate()); return; }
            case "AdminWorldPrev" -> { adminWorldPage = Math.max(0, adminWorldPage - 1); sendUpdate(buildUpdate()); return; }
            case "AdminWorldNext" -> { adminWorldPage++; sendUpdate(buildUpdate()); return; }
            case "AdminWorldLast" -> {
                List<String> worldNames = getWorldNames();
                int totalPages = Math.max(1, (int) Math.ceil(worldNames.size() / 5.0));
                adminWorldPage = totalPages - 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "AdminInstFirst" -> { adminInstPage = 0; sendUpdate(buildUpdate()); return; }
            case "AdminInstPrev" -> { adminInstPage = Math.max(0, adminInstPage - 1); sendUpdate(buildUpdate()); return; }
            case "AdminInstNext" -> { adminInstPage++; sendUpdate(buildUpdate()); return; }
            case "AdminInstLast" -> {
                List<String> instanceNames = getInstanceNames();
                int totalPages = Math.max(1, (int) Math.ceil(instanceNames.size() / 5.0));
                adminInstPage = totalPages - 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "NavDisabled" -> {
                switchTab(ActiveTab.DISABLED);
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleEnable" -> {
                if (!isAdmin && !adminConfig.isMasterEnabled()) {
                    sendUpdate(buildUpdate());
                    return;
                }
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
                if (!isAdmin && !adminConfig.isWelcomeMessagesEnabled()) {
                    sendUpdate(buildUpdate());
                    return;
                }
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
                    UUID saveUuid = chainViewerUuid();
                    String currentEntityType = tabEntityType();
                    List<SegmentKey> keys = getFilteredKeys();
                    preferences.snapshotChain(saveUuid, currentEntityType, keys, getDefaultComparator());
                    preferences.save();
                    if (activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER) {
                        adminConfig.save();
                    }
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
            case "SaveAdmin", "SaveAdminDis" -> {
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
                sendUpdate(buildUpdate());
                return;
            }
            case "SubTabChain" -> {
                chainSubTab = ChainSubTab.CHAIN;
                sendUpdate(buildUpdate());
                return;
            }
            case "SubTabSettings" -> {
                chainSubTab = ChainSubTab.SETTINGS;
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleChainEnabled" -> {
                String chainType = tabEntityType();
                boolean isNpcChain = ENTITY_TYPE_NPCS.equals(chainType);
                boolean adminChainDisabled = isNpcChain
                        ? !adminConfig.isNpcChainEnabled()
                        : !adminConfig.isPlayerChainEnabled();
                if (!isAdmin && adminChainDisabled) {
                    sendUpdate(buildUpdate());
                    return;
                }
                boolean current = preferences.isChainEnabled(viewerUuid, chainType);
                preferences.setChainEnabled(viewerUuid, chainType, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "SaveSettings" -> {
                try {
                    preferences.save();
                    persistUiState();
                    saveMessage = "Settings saved!";
                    saveMessageSuccess = true;
                } catch (Throwable e) {
                    LOGGER.atWarning().withCause(e).log("Failed to save settings for player %s", viewerUuid);
                    saveMessage = "Could not save settings!";
                    saveMessageSuccess = false;
                }
                sendUpdate(buildUpdate());
                return;
            }
        }

        if (data.action.startsWith("NpcPickerRow_")) {
            rebuildNpcPickerFiltered();
            int rowIndex = parseRowIndex(data.action, "NpcPickerRow_");
            int actualIndex = npcPickerPage * NPC_PICKER_ROW_COUNT + rowIndex;
            if (actualIndex >= 0 && actualIndex < npcPickerFiltered.size()) {
                npcPickerSelectedItem = npcPickerFiltered.get(actualIndex);
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("BlacklistRemove_")) {
            int row = parseRowIndex(data.action, "BlacklistRemove_");
            List<String> blacklist = new ArrayList<>(adminConfig.getBlacklistedNpcs());
            blacklist.sort(String.CASE_INSENSITIVE_ORDER);
            if (!blacklistFilter.isEmpty()) {
                String lowerFilter = blacklistFilter.toLowerCase(java.util.Locale.ROOT);
                blacklist.removeIf(npcId -> !npcId.toLowerCase(java.util.Locale.ROOT).contains(lowerFilter));
            }
            int index = blacklistPage * BLACKLIST_ROW_COUNT + row;
            if (index >= 0 && index < blacklist.size()) {
                adminConfig.removeBlacklistedNpc(blacklist.get(index));
                adminConfig.save();
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("PreviewTarget_")) {
            selectedPreviewTarget = parseRowIndex(data.action, "PreviewTarget_");
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("ToggleMod_")) {
            if (!isAdmin) {
                sendUpdate(buildUpdate());
                return;
            }
            int row = parseRowIndex(data.action, "ToggleMod_");
            List<String> namespaces = getSortedNamespaces();
            if (row >= 0 && row < namespaces.size()) {
                String namespace = namespaces.get(row);
                boolean current = adminConfig.isNamespaceEnabled(namespace);
                adminConfig.setNamespaceEnabled(namespace, !current);
                adminConfig.save();
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("AdminToggleWorld_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminToggleWorld_");
                List<String> worldNames = getWorldNames();
            int index = adminWorldPage * 5 + row;
                if (index >= 0 && index <worldNames.size()) {
                    String name = worldNames.get(index);
                    boolean current = adminConfig.isWorldEnabled(name);
                    adminConfig.setWorldEnabled(name, !current);
                    adminConfig.save();
                }
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("AdminToggleInst_")) {
            if (isAdmin) {
                int row = parseRowIndex(data.action, "AdminToggleInst_");
                List<String> instNames = getInstanceNames();
            int index = adminInstPage * 5 + row;
                if (index >= 0 && index <instNames.size()) {
                    String name = instNames.get(index);
                    boolean current = adminConfig.isWorldEnabled(name);
                    adminConfig.setWorldEnabled(name, !current);
                    adminConfig.save();
                }
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("ToggleWorld_")) {
            int row = parseRowIndex(data.action, "ToggleWorld_");
            List<String> worldNames = getWorldNames();
            int index = worldPage * 5 + row;
            if (index >= 0 && index <worldNames.size()) {
                String worldName = worldNames.get(index);
                if (!isAdmin && !adminConfig.isWorldEnabled(worldName)) {
                    sendUpdate(buildUpdate());
                    return;
                }
                if (isAdmin) {
                    boolean current = adminConfig.isWorldEnabled(worldName);
                    adminConfig.setWorldEnabled(worldName, !current);
                    adminConfig.save();
                } else {
                    boolean current = preferences.isWorldEnabled(viewerUuid, worldName);
                    preferences.setWorldEnabled(viewerUuid, worldName, !current);
                }
            }
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("ToggleInst_")) {
            int row = parseRowIndex(data.action, "ToggleInst_");
            List<String> instNames = getInstanceNames();
            int index = instPage * 5 + row;
            if (index >= 0 && index <instNames.size()) {
                String instName = instNames.get(index);
                if (!isAdmin && !adminConfig.isWorldEnabled(instName)) {
                    sendUpdate(buildUpdate());
                    return;
                }
                if (isAdmin) {
                    boolean current = adminConfig.isWorldEnabled(instName);
                    adminConfig.setWorldEnabled(instName, !current);
                    adminConfig.save();
                } else {
                    boolean current = preferences.isWorldEnabled(viewerUuid, instName);
                    preferences.setWorldEnabled(viewerUuid, instName, !current);
                }
            }
            sendUpdate(buildUpdate());
            return;
        }

        switch (data.action) {
            case "WorldFirst" -> { worldPage = 0; sendUpdate(buildUpdate()); return; }
            case "WorldPrev" -> { worldPage = Math.max(0, worldPage - 1); sendUpdate(buildUpdate()); return; }
            case "WorldNext" -> { worldPage++; sendUpdate(buildUpdate()); return; }
            case "WorldLast" -> {
                int totalWorldPages = Math.max(1, (int) Math.ceil(getWorldNames().size() / 5.0));
                worldPage = totalWorldPages - 1;
                sendUpdate(buildUpdate());
                return;
            }
            case "InstFirst" -> { instPage = 0; sendUpdate(buildUpdate()); return; }
            case "InstPrev" -> { instPage = Math.max(0, instPage - 1); sendUpdate(buildUpdate()); return; }
            case "InstNext" -> { instPage++; sendUpdate(buildUpdate()); return; }
            case "InstLast" -> {
                int totalInstPages = Math.max(1, (int) Math.ceil(getInstanceNames().size() / 5.0));
                instPage = totalInstPages - 1;
                sendUpdate(buildUpdate());
                return;
            }
            default -> {}
        }

        if (data.action.startsWith("EditSep_")) {
            int sepIndex = parseRowIndex(data.action, "EditSep_");
            List<SegmentView> chain = getChainViews();
            if (sepIndex >= 0 && sepIndex < chain.size()) {
                editingSepIndex = sepIndex;
                SegmentKey blockKey = chain.get(sepIndex).key();
                sepText = preferences.getSeparatorAfter(chainViewerUuid(), tabEntityType(), blockKey);

                pendingSepInput = null;
            }
            sendUpdate(buildUpdate());
            return;
        }


        if ("SepConfirm".equals(data.action)) {
            if (editingSepIndex >= 0) {
                List<SegmentView> chain = getChainViews();
                int absoluteIndex = editingSepIndex;
                if (absoluteIndex >= 0 && absoluteIndex < chain.size()) {
                    SegmentKey blockKey = chain.get(absoluteIndex).key();
                    String entityType = tabEntityType();


                    UUID chainUuid = chainViewerUuid();
                    String oldDefault = preferences.getSeparator(chainUuid, entityType);
                    for (SegmentView cv : chain) {
                        if (!cv.key().equals(blockKey)) {
                            String existing = preferences.getSeparatorAfter(chainUuid, entityType, cv.key());
                            if (existing.equals(oldDefault)) {
                                preferences.setSeparatorAfter(chainUuid, entityType, cv.key(), existing);
                            }
                        }
                    }

                    preferences.setSeparatorAfter(chainUuid, entityType, blockKey, sepText);
                    preferences.setSeparator(chainUuid, entityType, sepText);
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

                    originalVariant = preferences.getSelectedVariant(chainViewerUuid(), tabEntityType(), view.key());
                    pendingVariant = originalVariant;
                    originalPrefix = preferences.getPrefix(chainViewerUuid(), tabEntityType(), view.key());
                    originalSuffix = preferences.getSuffix(chainViewerUuid(), tabEntityType(), view.key());
                    originalBarEmpty = preferences.getBarEmptyChar(chainViewerUuid(), tabEntityType(), view.key());
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
                UUID uuid = chainViewerUuid();
                preferences.setSelectedVariant(uuid, tabEntityType(), editingVariantKey, pendingVariant);

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

                preferences.setSelectedVariant(chainViewerUuid(), tabEntityType(), editingVariantKey, originalVariant);
                preferences.setPrefix(chainViewerUuid(), tabEntityType(), editingVariantKey, originalPrefix);
                preferences.setSuffix(chainViewerUuid(), tabEntityType(), editingVariantKey, originalSuffix);
                preferences.setBarEmptyChar(chainViewerUuid(), tabEntityType(), editingVariantKey, originalBarEmpty);
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
        if (activeTab == ActiveTab.PLAYERS) {
            return ENTITY_TYPE_PLAYERS;
        }
        if (activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER && !adminOrderIsNpc) {
            return ENTITY_TYPE_PLAYERS;
        }
        return ENTITY_TYPE_NPCS;
    }

    private UUID chainViewerUuid() {
        if (activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER) {
            return ADMIN_CHAIN_UUID;
        }
        if (activeTab == ActiveTab.NPCS && adminConfig.isNpcChainLocked()) {
            return ADMIN_CHAIN_UUID;
        }
        if (activeTab == ActiveTab.PLAYERS && adminConfig.isPlayerChainLocked()) {
            return ADMIN_CHAIN_UUID;
        }
        return viewerUuid;
    }

    private void switchTab(ActiveTab tab) {
        activeTab = tab;
        filter = "";
        adminFilter = "";
        availPage = 0;
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
        if (activeTab == ActiveTab.GENERAL || activeTab == ActiveTab.DISABLED) return false;
        if (activeTab == ActiveTab.ADMIN && adminSubTab != AdminSubTab.ORDER) return false;
        SegmentTarget target = segment.target();
        if (activeTab == ActiveTab.PLAYERS) return target == SegmentTarget.ALL || target == SegmentTarget.PLAYERS;
        if (activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER) {
            if (adminOrderIsNpc) {
                return target == SegmentTarget.ALL || target == SegmentTarget.NPCS;
            } else {
                return target == SegmentTarget.ALL || target == SegmentTarget.PLAYERS;
            }
        }
        if (activeTab == ActiveTab.NPCS) {
            return target == SegmentTarget.ALL || target == SegmentTarget.NPCS;
        }
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
        if (!npcPickerOpen) {
            commands.set("#NpcPickerPopup.Visible", false);
        }


        setSidebarNav2State(commands, "#NavGeneral", activeTab == ActiveTab.GENERAL);
        setSidebarNav2State(commands, "#NavDisabled", activeTab == ActiveTab.DISABLED);
        setSidebarNav2State(commands, "#NavNpcs", activeTab == ActiveTab.NPCS);
        setSidebarNav2State(commands, "#NavPlayers", activeTab == ActiveTab.PLAYERS);

        boolean showNameplates = adminConfig.isMasterEnabled() && preferences.isNameplatesEnabled(viewerUuid);
        commands.set("#NameplatesHeader.Visible", showNameplates);
        commands.set("#NavNpcsGroup.Visible", showNameplates);
        commands.set("#NavPlayersGroup.Visible", showNameplates);

        commands.set("#AdminSection.Visible", isAdmin);
        if (isAdmin) {
            boolean adminConfig_ = activeTab == ActiveTab.ADMIN && adminSubTab != AdminSubTab.ORDER;
            boolean adminOrderNpc = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER && adminOrderIsNpc;
            boolean adminOrderPlayer = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER && !adminOrderIsNpc;
            setSidebarNav2State(commands, "#NavAdminNpcs", adminOrderNpc);
            setSidebarNav2State(commands, "#NavAdminPlayers", adminOrderPlayer);
            setSidebarNav2State(commands, "#NavAdminConfig", adminConfig_);
        }

        boolean isAdminOrder = activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER;

        commands.set("#TabGeneral.Visible", activeTab == ActiveTab.GENERAL);
        commands.set("#TabEditor.Visible", activeTab == ActiveTab.NPCS || activeTab == ActiveTab.PLAYERS || isAdminOrder);
        commands.set("#TabAdmin.Visible", activeTab == ActiveTab.ADMIN && !isAdminOrder);
        commands.set("#TabDisabled.Visible", activeTab == ActiveTab.DISABLED);


        if (activeTab == ActiveTab.GENERAL) {

            boolean allDisabled = areAllSegmentsDisabled();
            boolean adminMasterDisabled = !adminConfig.isMasterEnabled();

            if (adminMasterDisabled && !isAdmin) {
                commands.set("#EnableToggleLabel.Text", "Enable Nameplates (Disabled by Admin)");
                renderToggle(commands, "#EnableToggle", false);
            } else {
                commands.set("#EnableToggleLabel.Text", "Enable Nameplates");
                boolean enabled = !allDisabled && preferences.isNameplatesEnabled(viewerUuid);
                renderToggle(commands, "#EnableToggle", enabled);
            }

            commands.set("#AllDisabledNotice.Visible", allDisabled && !adminMasterDisabled);

            boolean lookOnly = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE_GLOBAL);
            renderToggle(commands, "#LookToggle", lookOnly);

            boolean adminWelcomeDisabled = !adminConfig.isWelcomeMessagesEnabled();
            if (adminWelcomeDisabled && !isAdmin) {
                commands.set("#WelcomeToggleLabel.Text", "Show Welcome Message (Disabled by Admin)");
                renderToggle(commands, "#WelcomeToggle", false);
            } else {
                commands.set("#WelcomeToggleLabel.Text", "Show Welcome Message");
                boolean welcome = preferences.isShowWelcomeMessage(viewerUuid);
                renderToggle(commands, "#WelcomeToggle", welcome);
            }


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


        if (activeTab == ActiveTab.ADMIN && adminSubTab != AdminSubTab.ORDER) {
            commands.set("#AdminFilterField.Value", adminFilter);


            commands.set("#AdminRequiredContent.Visible", adminSubTab == AdminSubTab.REQUIRED);
            commands.set("#AdminDisabledContent.Visible", adminSubTab == AdminSubTab.DISABLED);
            commands.set("#AdminSettingsContent.Visible", adminSubTab == AdminSubTab.SETTINGS);
            commands.set("#AdminBlacklistContent.Visible", adminSubTab == AdminSubTab.BLACKLIST);


            boolean reqActive = adminSubTab == AdminSubTab.REQUIRED;
            commands.set("#AdminSubTab0.Visible", !reqActive);
            commands.set("#AdminSubTab0Active.Visible", reqActive);

            boolean disActive = adminSubTab == AdminSubTab.DISABLED;
            commands.set("#AdminSubTab1.Visible", !disActive);
            commands.set("#AdminSubTab1Active.Visible", disActive);

            boolean setActive = adminSubTab == AdminSubTab.SETTINGS;
            commands.set("#AdminSubTab2.Visible", !setActive);
            commands.set("#AdminSubTab2Active.Visible", setActive);

            boolean blActive = adminSubTab == AdminSubTab.BLACKLIST;
            commands.set("#AdminSubTab3.Visible", !blActive);
            commands.set("#AdminSubTab3Active.Visible", blActive);

            if (adminSubTab == AdminSubTab.REQUIRED) {
                fillAdmin(commands);
            } else if (adminSubTab == AdminSubTab.DISABLED) {
                commands.set("#AdminDisFilterField.Value", adminDisFilter);
                fillAdminDisabled(commands);
            } else if (adminSubTab == AdminSubTab.SETTINGS) {
                commands.set("#AdminServerNameField.Value", adminServerName);
                renderToggle(commands, "#AdminWelcomeToggle", adminConfig.isWelcomeMessagesEnabled());
                renderToggle(commands, "#AdminMasterToggle", adminConfig.isMasterEnabled());
                renderToggle(commands, "#AdminPlayerChainToggle", adminConfig.isPlayerChainEnabled());
                renderToggle(commands, "#AdminNpcChainToggle", adminConfig.isNpcChainEnabled());
                fillAdminWorldSettings(commands);
            } else if (adminSubTab == AdminSubTab.BLACKLIST) {
                fillBlacklist(commands);
            }

            fillNpcPicker(commands);

            String saveMsgId = switch (adminSubTab) {
                case REQUIRED -> "#SaveMessageAdmin";
                case DISABLED -> "#SaveMessageAdminDis";
                case SETTINGS -> "#SaveMessageAdminSettings";
                case BLACKLIST -> "#SaveMessageBlacklist";
                case ORDER -> "#SaveMessageEditor";
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


        boolean showChainSubTabs = activeTab == ActiveTab.NPCS || activeTab == ActiveTab.PLAYERS
                || (activeTab == ActiveTab.ADMIN && adminSubTab == AdminSubTab.ORDER);
        boolean isChainTab = chainSubTab == ChainSubTab.CHAIN || !showChainSubTabs;
        boolean isSettingsTab = chainSubTab == ChainSubTab.SETTINGS && showChainSubTabs;

        commands.set("#EditorContent.Visible", isChainTab);
        commands.set("#ChainSettingsContent.Visible", isSettingsTab);

        commands.set("#SaveButtonSettings.Visible", isSettingsTab);
        commands.set("#SaveButton.Visible", isChainTab);

        if (showChainSubTabs) {
            updateSubTabStyles(commands);
        }

        if (isSettingsTab) {
            fillSettings(commands);

            commands.set("#SaveMessageSettings.Visible", saveMessage != null);
            if (saveMessage != null) {
                commands.set("#SaveMessageSettings.Text", saveMessage);
                commands.set("#SaveMessageSettings.Style.TextColor", saveMessageSuccess ? "#4ade80" : "#f87171");
            }
            return;
        }

        if (isAdminOrder) {
            commands.set("#EditorTitle.Text", adminOrderIsNpc ? "NPC ORDER (ADMIN)" : "PLAYER ORDER (ADMIN)");
        } else if (activeTab == ActiveTab.NPCS) {
            commands.set("#EditorTitle.Text", "YOUR NPC NAMEPLATE");
        } else {
            commands.set("#EditorTitle.Text", "YOUR PLAYER NAMEPLATE");
        }

        commands.set("#NonIntegratedInfo.Visible", false);

        boolean isNpcTab = activeTab == ActiveTab.NPCS || (activeTab == ActiveTab.ADMIN && adminOrderIsNpc);
        boolean adminChainDisabled = isNpcTab ? !adminConfig.isNpcChainEnabled() : !adminConfig.isPlayerChainEnabled();
        if (!isAdmin && adminChainDisabled) {
            commands.set("#AdminChainDisabledNotice.Visible", true);
            commands.set("#AdminChainDisabledNotice.Text",
                    isNpcTab ? "NPC Nameplates are disabled by the server admin."
                             : "Player Nameplates are disabled by the server admin.");
            commands.set("#ChainStrip.Visible", false);
            commands.set("#ChainEmpty.Visible", false);
            commands.set("#ChainPagination.Visible", false);
            commands.set("#PreviewContainer.Visible", false);
            commands.set("#ClearChainButton.Visible", false);
            commands.set("#FilterField.Visible", false);
            commands.set("#AvailRow1.Visible", false);
            commands.set("#AvailRow2.Visible", false);
            commands.set("#AvailEmpty.Visible", false);
            return;
        }
        commands.set("#AdminChainDisabledNotice.Visible", false);

        commands.set("#FilterField.Value", filter);

        List<SegmentView> chain = getChainViews();
        List<SegmentView> available = getAvailableViews();

        int totalAvailPages = Math.max(1, (int) Math.ceil(available.size() / (double) AVAIL_PAGE_SIZE));
        if (availPage >= totalAvailPages) availPage = totalAvailPages - 1;

        boolean isNpcContext = isAdminOrder ? adminOrderIsNpc : (activeTab == ActiveTab.NPCS);
        boolean chainLocked = isNpcContext ? adminConfig.isNpcChainLocked() : adminConfig.isPlayerChainLocked();

        commands.set("#LockChainBar.Visible", isAdminOrder);
        if (isAdminOrder) {
            renderToggle(commands, "#LockChain", chainLocked);
            commands.set("#LockChainHint.Text", chainLocked
                ? "Unlock Chain to make changes."
                : "When locked, all players see this exact chain.");
        }

        commands.set("#ChainLockedNotice.Visible", !isAdmin && chainLocked);

        fillChain(commands, chain, chainLocked);
        fillAvailable(commands, available, totalAvailPages, chainLocked);

        fillPreviewTargets(commands);
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
                        : preferences.getBarEmptyChar(chainViewerUuid(), tabEntityType(), editingVariantKey);

                for (int vi = 0; vi < MAX_VARIANT_OPTIONS; vi++) {
                    boolean vVisible = vi < variants.size();
                    commands.set("#Variant" + vi + ".Visible", vVisible);
                    if (vVisible) {
                        String variantName = getVariantName(variants, vi, barEmptyChar);
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
                        String pfx = preferences.getPrefix(chainViewerUuid(), tabEntityType(), editingVariantKey);
                        commands.set("#PrefixField.Value", pfx);
                    }
                    if (pendingSuffixInput == null) {
                        String sfx = preferences.getSuffix(chainViewerUuid(), tabEntityType(), editingVariantKey);
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
                            : preferences.getBarEmptyChar(chainViewerUuid(), tabEntityType(), editingVariantKey);
                    commands.set("#BarEmptyField.Value", barEmpty);
                }

            }
        }
    }

    private static String getVariantName(List<String> variants, int vi, String barEmptyChar) {
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
        return variantName;
    }

    private void updateSubTabStyles(UICommandBuilder commands) {
        boolean chainActive = chainSubTab == ChainSubTab.CHAIN;
        commands.set("#SubTabChain.Visible", !chainActive);
        commands.set("#SubTabChainActive.Visible", chainActive);

        boolean settingsActive = chainSubTab == ChainSubTab.SETTINGS;
        commands.set("#SubTabSettings.Visible", !settingsActive);
        commands.set("#SubTabSettingsActive.Visible", settingsActive);
    }

    private void fillSettings(UICommandBuilder commands) {
        plugin.discoverWorlds();

        boolean isNpcTab = activeTab == ActiveTab.NPCS || (activeTab == ActiveTab.ADMIN && adminOrderIsNpc);

        boolean adminChainDisabled = isNpcTab ? !adminConfig.isNpcChainEnabled() : !adminConfig.isPlayerChainEnabled();

        if (adminChainDisabled && !isAdmin) {
            String msg = isNpcTab
                    ? "NPC Nameplates are disabled by the server admin."
                    : "Player Nameplates are disabled by the server admin.";
            commands.set("#AdminDisabledNotice.Visible", true);
            commands.set("#AdminDisabledNotice.Text", msg);
            commands.set("#ChainEnabledOn.Visible", false);
            commands.set("#ChainEnabledOff.Visible", false);
            commands.set("#ModSectionSep.Visible", false);
            commands.set("#ModSectionHeader.Visible", false);
            commands.set("#ModSectionDesc.Visible", false);
            for (int i = 0; i < 8; i++) {
                commands.set("#ModRow" + i + ".Visible", false);
            }
            commands.set("#WorldSectionSep.Visible", false);
            commands.set("#WorldSectionHeader.Visible", false);
            commands.set("#WorldSectionDesc.Visible", false);
            commands.set("#WorldsContainer.Visible", false);
            commands.set("#WorldPagination.Visible", false);
            commands.set("#InstPagination.Visible", false);
            return;
        } else {
            commands.set("#AdminDisabledNotice.Visible", false);
        }

        commands.set("#ChainEnabledLabel.Text", isNpcTab ? "NPC Nameplates" : "Player Nameplates");
        commands.set("#ChainEnabledDesc.Text", isNpcTab
                ? "When disabled, NameplateBuilder will not apply or update NPC nameplates. Other mods may still display their own."
                : "When disabled, NameplateBuilder will not apply or update player nameplates. Other mods may still display their own.");

        boolean enabled = preferences.isChainEnabled(viewerUuid, tabEntityType());
        renderToggle(commands, "#ChainEnabled", enabled);

        if (!enabled) {
            commands.set("#ModSectionSep.Visible", false);
            commands.set("#ModSectionHeader.Visible", false);
            commands.set("#ModSectionDesc.Visible", false);
            for (int i = 0; i < 8; i++) {
                commands.set("#ModRow" + i + ".Visible", false);
            }
            commands.set("#WorldSectionSep.Visible", false);
            commands.set("#WorldSectionHeader.Visible", false);
            commands.set("#WorldSectionDesc.Visible", false);
            commands.set("#WorldsContainer.Visible", false);
            commands.set("#WorldPagination.Visible", false);
            commands.set("#InstPagination.Visible", false);
            return;
        }
        commands.set("#ModSectionSep.Visible", true);
        commands.set("#ModSectionHeader.Visible", true);
        commands.set("#ModSectionDesc.Visible", true);
        commands.set("#WorldSectionSep.Visible", true);
        commands.set("#WorldSectionHeader.Visible", true);
        commands.set("#WorldSectionDesc.Visible", true);
        commands.set("#WorldsContainer.Visible", true);


        List<String> mods = getSortedNamespaces();
        for (int i = 0; i < 8; i++) {
            String rowId = "#ModRow" + i;
            if (i < mods.size()) {
                String namespace = mods.get(i);
                String displayName = adminConfig.getNamespaceDisplayName(namespace);
                boolean adminModLocked = !adminConfig.isNamespaceEnabled(namespace);
                if (adminModLocked && !isAdmin) {
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", displayName + " (Disabled by Admin)");
                    renderToggle(commands, rowId, false);
                } else {
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", displayName);
                    renderToggle(commands, rowId, adminConfig.isNamespaceEnabled(namespace));
                }
            } else {
                commands.set(rowId + ".Visible", false);
            }
        }

        List<String> worldNames = getWorldNames();
        int totalWorldPages = Math.max(1, (int) Math.ceil(worldNames.size() / 5.0));
        if (worldPage >= totalWorldPages) worldPage = totalWorldPages - 1;
        int worldStart = worldPage * 5;
        int worldEnd = Math.min(worldNames.size(), worldStart + 5);
        for (int i = 0; i < 5; i++) {
            String rowId = "#WorldRow" + i;
            int index = worldStart + i;
            if (index < worldEnd) {
                String worldName = worldNames.get(index);
                boolean adminWorldLocked = !adminConfig.isWorldEnabled(worldName);
                if (adminWorldLocked && !isAdmin) {
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", worldName + " (Disabled by Admin)");
                    renderToggle(commands, rowId, false);
                } else {
                    boolean worldEnabled = isAdmin
                            ? adminConfig.isWorldEnabled(worldName)
                            : preferences.isWorldEnabled(viewerUuid, worldName);
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", worldName);
                    renderToggle(commands, rowId, worldEnabled);
                }
            } else {
                commands.set(rowId + ".Visible", false);
            }
        }
        boolean showWorldPagination = totalWorldPages > 1;
        commands.set("#WorldPagination.Visible", showWorldPagination);
        if (showWorldPagination) {
            commands.set("#WorldPageLabel.Text", paginationLabel(worldStart, 5, worldNames.size()));
        }

        List<String> instNames = getInstanceNames();
        int totalInstPages = Math.max(1, (int) Math.ceil(instNames.size() / 5.0));
        if (instPage >= totalInstPages) instPage = totalInstPages - 1;
        int instStart = instPage * 5;
        int instEnd = Math.min(instNames.size(), instStart + 5);
        for (int i = 0; i < 5; i++) {
            String rowId = "#InstRow" + i;
            int index = instStart + i;
            if (index < instEnd) {
                String instanceName = instNames.get(index);
                boolean adminInstLocked = !adminConfig.isWorldEnabled(instanceName);
                if (adminInstLocked && !isAdmin) {
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", instanceName + " (Disabled by Admin)");
                    renderToggle(commands, rowId, false);
                } else {
                    boolean instEnabled = isAdmin
                            ? adminConfig.isWorldEnabled(instanceName)
                            : preferences.isWorldEnabled(viewerUuid, instanceName);
                    commands.set(rowId + ".Visible", true);
                    commands.set(rowId + "Label.Text", instanceName);
                    renderToggle(commands, rowId, instEnabled);
                }
            } else {
                commands.set(rowId + ".Visible", false);
            }
        }
        boolean showInstPagination = totalInstPages > 1;
        commands.set("#InstPagination.Visible", showInstPagination);
        if (showInstPagination) {
            commands.set("#InstPageLabel.Text", paginationLabel(instStart, 5, instNames.size()));
        }
    }

    private void renderToggle(UICommandBuilder commands, String prefix, boolean value) {
        commands.set(prefix + "On.Visible", value);
        commands.set(prefix + "Off.Visible", !value);
    }

    private List<String> getWorldNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(plugin.getDiscoveredWorldNames());
        names.addAll(adminConfig.getWorldEnabled().keySet());
        names.addAll(preferences.getPlayerWorldEnabled(viewerUuid).keySet());
        return new ArrayList<>(names);
    }

    private List<String> getInstanceNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(plugin.getDiscoveredInstanceNames());
        return new ArrayList<>(names);
    }

    private void persistUiState() {
        UI_STATE.put(viewerUuid, new UiState(activeTab, filter, availPage, 0,
                adminLeftPage, adminRightPage, adminSubTab, adminDisLeftPage, adminDisRightPage, disabledPage,
                "", chainSubTab));
    }


    private static final Set<String> INTERNAL_NAMESPACES = Set.of(
            "nameplatebuilder", "npc", "_unknown", "unknown", ""
    );

    private List<String> getSortedNamespaces() {
        List<String> namespaces = new ArrayList<>(adminConfig.getSegmentsByNamespace().keySet());
        namespaces.removeIf(namespace -> namespace == null || INTERNAL_NAMESPACES.contains(namespace.toLowerCase(java.util.Locale.ROOT)));
        namespaces.sort(String.CASE_INSENSITIVE_ORDER);
        return namespaces;
    }


    private static void setSidebarNav2State(UICommandBuilder commands, String base, boolean active) {
        commands.set(base + ".Visible", !active);
        commands.set(base + "Active.Visible", active);
    }

    private void fillChain(UICommandBuilder commands, List<SegmentView> chain, boolean chainLocked) {
        boolean hasChain = !chain.isEmpty();
        boolean singleBlock = chain.size() <= 1;
        boolean editable = !chainLocked;
        commands.set("#ChainEmpty.Visible", !hasChain);
        commands.set("#ChainStrip.Visible", hasChain);

        int end = Math.min(chain.size(), MAX_CHAIN_BLOCKS);

        for (int i = 0; i < MAX_CHAIN_BLOCKS; i++) {
            int index = i;
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
                commands.set(prefix + ".Background.Color", (chainLocked || required) ? COLOR_REQUIRED : COLOR_CHAIN_ACTIVE);


                boolean hasVariants = chainSeg != null && chainSeg.variants().size() > 1;
                int selectedVariant = hasVariants
                        ? preferences.getSelectedVariant(chainViewerUuid(), tabEntityType(), view.key())
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
                        String emptyChar = preferences.getBarEmptyChar(chainViewerUuid(), tabEntityType(), view.key());
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


                commands.set(prefix + "Remove.Visible", editable && !required);


                commands.set(prefix + "Left.Visible", editable && !singleBlock);
                commands.set(prefix + "Right.Visible", editable && !singleBlock);


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
                    commands.set(prefix + "Format.Style.Pressed.Background", "#225530");
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


            if (i < MAX_CHAIN_BLOCKS - 1) {
                boolean sepVisible = visible && (i + 1) < end;
                String sepId = "#ChainSep" + i;
                commands.set(sepId + ".Visible", sepVisible);
                if (sepVisible) {
                    SegmentKey blockKey = chain.get(index).key();
                    String sep = preferences.getSeparatorAfter(chainViewerUuid(), tabEntityType(), blockKey);
                    String displaySep = sep.isEmpty() ? "." : sep;
                    commands.set(sepId + ".Text", displaySep);
                }
            }
        }


    }


    private void fillAvailable(UICommandBuilder commands, List<SegmentView> available, int totalPages, boolean chainLocked) {
        if (chainLocked) {
            commands.set("#ClearChainButton.Visible", false);
            commands.set("#FilterField.Visible", false);
            commands.set("#AvailRow1.Visible", false);
            commands.set("#AvailRow2.Visible", false);
            commands.set("#AvailEmpty.Visible", false);
            commands.set("#PrevAvail.Visible", false);
            commands.set("#AvailPaginationLabel.Visible", false);
            commands.set("#NextAvail.Visible", false);
            commands.set("#SaveButton.Visible", false);
            return;
        }
        commands.set("#ClearChainButton.Visible", true);
        commands.set("#FilterField.Visible", true);
        commands.set("#SaveButton.Visible", true);

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
            commands.set("#AvailPaginationLabel.Text", paginationLabel(start, AVAIL_PAGE_SIZE, available.size()));
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
            commands.set("#AdminLeftPageLabel.Text", paginationLabel(start, ADMIN_PAGE_SIZE, leftList.size()));
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
            commands.set("#AdminRightPageLabel.Text", paginationLabel(start, ADMIN_PAGE_SIZE, rightList.size()));
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
            commands.set("#AdminDisLeftPageLabel.Text", paginationLabel(start, ADMIN_PAGE_SIZE, leftList.size()));
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
            commands.set("#AdminDisRightPageLabel.Text", paginationLabel(start, ADMIN_PAGE_SIZE, rightList.size()));
        }
    }


    private void fillDisabledTab(UICommandBuilder commands) {

        List<SegmentView> disabledViews = new ArrayList<>();
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
            commands.set("#DisabledPageLabel.Text", paginationLabel(start, DISABLED_PAGE_SIZE, disabledViews.size()));
        }
    }


    private void addRow(int row) {
        SegmentView view = getAvailableRow(row);
        if (view == null) {
            return;
        }


        preferences.enable(chainViewerUuid(), tabEntityType(), view.key());
    }

    private void removeRow(int row) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }

        if (adminConfig.isRequired(view.key())) {
            return;
        }
        preferences.disable(chainViewerUuid(), tabEntityType(), view.key());
    }

    private void clearChain() {
        List<SegmentKey> filteredKeys = getFilteredKeys();
        preferences.disableAll(chainViewerUuid(), tabEntityType(), filteredKeys);

        for (SegmentKey key : filteredKeys) {
            if (adminConfig.isRequired(key)) {
                preferences.enable(chainViewerUuid(), tabEntityType(), key);
            }
        }
    }

    private void moveRow(int row, int delta) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }
        List<SegmentKey> filteredKeys = getFilteredKeys();
        preferences.move(chainViewerUuid(), tabEntityType(), view.key(), delta, filteredKeys, getDefaultComparator());
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
        int index = row;
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private SegmentView getChainRow(int row) {
        List<SegmentView> list = getChainViews();
        int index = row;
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


    private List<SegmentView> getAvailableViews() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        List<SegmentKey> filteredKeys = getFilteredKeys();
        List<SegmentKey> ordered = preferences.getAvailable(chainViewerUuid(), tabEntityType(), filteredKeys, getDefaultComparator());
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
        List<SegmentKey> ordered = preferences.getChain(chainViewerUuid(), tabEntityType(), filteredKeys, getDefaultComparator());


        boolean chainChanged = false;
        for (SegmentKey key : filteredKeys) {
            if (adminConfig.isRequired(key) && !ordered.contains(key)) {

                preferences.enable(chainViewerUuid(), tabEntityType(), key);
                chainChanged = true;
            }
        }
        if (chainChanged) {
            ordered = preferences.getChain(chainViewerUuid(), tabEntityType(), filteredKeys, getDefaultComparator());
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

    private static List<String> getRuntimeNpcIds() {
        if (cachedNpcIds == null) {
            try {
                var npcPlugin = NPCPlugin.get();
                if (npcPlugin != null) {
                    var names = npcPlugin.getRoleTemplateNames(false);
                    if (names != null) {
                        var filtered = new ArrayList<String>();
                        for (var name : names) {
                            if (name.startsWith("Template_") || name.startsWith("Component_") || name.startsWith("Test_")) continue;
                            filtered.add(name);
                        }
                        filtered.sort(String.CASE_INSENSITIVE_ORDER);
                        cachedNpcIds = filtered;
                    }
                }
            } catch (Exception _) {
            }
            if (cachedNpcIds == null) cachedNpcIds = List.of();
        }
        return cachedNpcIds;
    }

    private void rebuildNpcPickerFiltered() {
        npcPickerFiltered.clear();
        Set<String> alreadyBlacklisted = adminConfig.getBlacklistedNpcs();
        String lowerFilter = npcPickerFilter.toLowerCase(java.util.Locale.ROOT);
        for (String id : getRuntimeNpcIds()) {
            if (alreadyBlacklisted.contains(id)) continue;
            if (lowerFilter.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(lowerFilter)) {
                npcPickerFiltered.add(id);
            }
        }
    }

    private void openNpcPicker() {
        npcPickerOpen = true;
        npcPickerDirty = true;
        npcPickerFilter = "";
        npcPickerPage = 0;
        npcPickerSelectedItem = null;
        rebuildNpcPickerFiltered();
    }

    private void closeNpcPicker() {
        npcPickerOpen = false;
        npcPickerFilter = "";
        npcPickerPage = 0;
        npcPickerSelectedItem = null;
        npcPickerFiltered.clear();
    }

    private void fillAdminWorldSettings(UICommandBuilder commands) {
        plugin.discoverWorlds();

        List<String> worldNames = getWorldNames();
        int totalWorldPages = Math.max(1, (int) Math.ceil(worldNames.size() / 5.0));
        if (adminWorldPage >= totalWorldPages) adminWorldPage = totalWorldPages - 1;
        int worldStart = adminWorldPage * 5;

        for (int i = 0; i < 5; i++) {
            String rowId = "#AdminWorldRow" + i;
            int index = worldStart + i;
            if (index < worldNames.size()) {
                String name = worldNames.get(index);
                commands.set(rowId + ".Visible", true);
                commands.set(rowId + "Label.Text", name);
                renderToggle(commands, rowId, adminConfig.isWorldEnabled(name));
            } else {
                commands.set(rowId + ".Visible", false);
            }
        }

        boolean showWorldPag = totalWorldPages > 1;
        commands.set("#AdminWorldPagination.Visible", showWorldPag);
        if (showWorldPag) {
            commands.set("#AdminWorldPageLabel.Text", paginationLabel(worldStart, 5, worldNames.size()));
        }

        List<String> instNames = getInstanceNames();
        int totalInstPages = Math.max(1, (int) Math.ceil(instNames.size() / 5.0));
        if (adminInstPage >= totalInstPages) adminInstPage = totalInstPages - 1;
        int instStart = adminInstPage * 5;

        for (int i = 0; i < 5; i++) {
            String rowId = "#AdminInstRow" + i;
            int index = instStart + i;
            if (index < instNames.size()) {
                String name = instNames.get(index);
                commands.set(rowId + ".Visible", true);
                commands.set(rowId + "Label.Text", name);
                renderToggle(commands, rowId, adminConfig.isWorldEnabled(name));
            } else {
                commands.set(rowId + ".Visible", false);
            }
        }

        boolean showInstPag = totalInstPages > 1;
        commands.set("#AdminInstPagination.Visible", showInstPag);
        if (showInstPag) {
            commands.set("#AdminInstPageLabel.Text", paginationLabel(instStart, 5, instNames.size()));
        }

        commands.set("#AdminWorldEmpty.Visible", worldNames.isEmpty() && instNames.isEmpty());
    }

    private void fillBlacklist(UICommandBuilder commands) {
        List<String> allBlacklisted = new ArrayList<>(adminConfig.getBlacklistedNpcs());
        allBlacklisted.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> filtered;
        if (blacklistFilter.isEmpty()) {
            filtered = allBlacklisted;
        } else {
            String lowerFilter = blacklistFilter.toLowerCase(java.util.Locale.ROOT);
            filtered = new ArrayList<>();
            for (String npcId : allBlacklisted) {
                if (npcId.toLowerCase(java.util.Locale.ROOT).contains(lowerFilter)) {
                    filtered.add(npcId);
                }
            }
        }

        commands.set("#BlacklistFilterField.Value", blacklistFilter);
        commands.set("#BlacklistRemoveFiltered.Visible", !blacklistFilter.isEmpty() && !filtered.isEmpty());

        int start = blacklistPage * BLACKLIST_ROW_COUNT;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) BLACKLIST_ROW_COUNT));
        if (blacklistPage >= totalPages) blacklistPage = totalPages - 1;

        commands.set("#BlacklistEmpty.Visible", filtered.isEmpty());

        for (int i = 0; i < BLACKLIST_ROW_COUNT; i++) {
            int index = start + i;
            if (index < filtered.size()) {
                commands.set("#BlacklistRow" + i + ".Visible", true);
                commands.set("#BlacklistRow" + i + "Label.Text", filtered.get(index));
            } else {
                commands.set("#BlacklistRow" + i + ".Visible", false);
            }
        }

        boolean showPagination = totalPages > 1;
        commands.set("#BlacklistPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#BlacklistPageLabel.Text", paginationLabel(start, BLACKLIST_ROW_COUNT, filtered.size()));
        }
    }

    private void fillNpcPicker(UICommandBuilder commands) {
        commands.set("#NpcPickerPopup.Visible", npcPickerOpen);
        if (!npcPickerOpen) return;

        commands.set("#NpcPickerFilter.Value", npcPickerFilter);

        if (npcPickerDirty) {
            rebuildNpcPickerFiltered();
            npcPickerDirty = false;
        }

        int start = npcPickerPage * NPC_PICKER_ROW_COUNT;
        int totalPages = Math.max(1, (int) Math.ceil(npcPickerFiltered.size() / (double) NPC_PICKER_ROW_COUNT));

        commands.set("#NpcPickerEmpty.Visible", npcPickerFiltered.isEmpty());

        for (int i = 0; i < NPC_PICKER_ROW_COUNT; i++) {
            int index = start + i;
            if (index < npcPickerFiltered.size()) {
                String name = npcPickerFiltered.get(index);
                boolean selected = name.equals(npcPickerSelectedItem);
                commands.set("#NpcPickerRow" + i + ".Visible", true);
                commands.set("#NpcPickerRowBtn" + i + ".Text", (selected ? "> " : "  ") + name);
            } else {
                commands.set("#NpcPickerRow" + i + ".Visible", false);
            }
        }

        boolean showPagination = totalPages > 1;
        commands.set("#NpcPickerPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#NpcPickerPageInfo.Text", paginationLabel(start, NPC_PICKER_ROW_COUNT, npcPickerFiltered.size()));
        }

        commands.set("#NpcPickerSelectedLabel.Text", npcPickerSelectedItem != null ? npcPickerSelectedItem : "None");
        commands.set("#NpcPickerAdd.Visible", npcPickerSelectedItem != null);
        commands.set("#NpcPickerAddAllFiltered.Visible", !npcPickerFilter.isEmpty() && !npcPickerFiltered.isEmpty());
    }

    private void fillPreviewTargets(UICommandBuilder commands) {
        // Player tab only shows the default chain preview - no target selection needed
        if (activeTab == ActiveTab.PLAYERS) {
            commands.set("#PreviewTargetBar.Visible", false);
            selectedPreviewTarget = 0;
            return;
        }

        Map<String, Set<String>> profiles = adminConfig.getSegmentProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());

        boolean hasMultiple = profileNames.size() > 1;
        commands.set("#PreviewTargetBar.Visible", hasMultiple);

        if (!hasMultiple) {
            selectedPreviewTarget = 0;
            return;
        }

        for (int i = 0; i < MAX_PREVIEW_TARGETS; i++) {
            if (i < profileNames.size()) {
                commands.set("#PreviewTarget" + i + ".Visible", true);
                String name = profileNames.get(i);
                boolean active = (i == selectedPreviewTarget);
                commands.set("#PreviewTargetBtn" + i + ".Text", "  " + name + "  ");
                commands.set("#PreviewTargetBtn" + i + "Active.Text", "  " + name + "  ");
                setSidebarNav2State(commands, "#PreviewTargetBtn" + i, active);
            } else {
                commands.set("#PreviewTarget" + i + ".Visible", false);
            }
        }
    }

    private String buildPreview(List<SegmentView> views) {
        if (views.isEmpty()) {
            return "(no blocks enabled)";
        }

        // Player tab shows all segments unfiltered - no target selection
        if (activeTab == ActiveTab.PLAYERS) {
            return buildSinglePreviewLine(views);
        }

        Map<String, Set<String>> profiles = adminConfig.getSegmentProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());

        if (profiles.isEmpty() || profileNames.isEmpty()) {
            return buildSinglePreviewLine(views);
        }

        int targetIdx = Math.min(selectedPreviewTarget, profileNames.size() - 1);
        if (targetIdx < 0) targetIdx = 0;
        String targetName = profileNames.get(targetIdx);
        Set<String> targetSegments = profiles.get(targetName);

        if (targetSegments == null || profiles.size() <= 1) {
            return buildSinglePreviewLine(views);
        }

        List<SegmentView> filtered = new ArrayList<>();
        for (SegmentView view : views) {
            if (targetSegments.contains(view.key().segmentId())) {
                filtered.add(view);
            }
        }

        return buildSinglePreviewLine(filtered.isEmpty() ? views : filtered);
    }

    private static String paginationLabel(int start, int pageSize, int totalItems) {
        int lastShown = Math.min(start + pageSize, totalItems);
        return lastShown + "/" + totalItems;
    }

    private String buildSinglePreviewLine(List<SegmentView> views) {
        StringBuilder builder = new StringBuilder();
        SegmentView prevView = null;
        for (SegmentView view : views) {
            if (prevView != null) {
                builder.append(preferences.getSeparatorAfter(chainViewerUuid(), tabEntityType(), prevView.key()));
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
