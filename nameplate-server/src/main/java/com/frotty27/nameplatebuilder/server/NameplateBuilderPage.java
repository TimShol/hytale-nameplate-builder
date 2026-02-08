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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class NameplateBuilderPage extends InteractiveCustomUIPage<NameplateBuilderPage.SettingsData> {

    private static final int CHAIN_PAGE_SIZE = 4;
    private static final int AVAIL_PAGE_SIZE = 8;
    private static final int MOD_NAME_MAX_LENGTH = 24;
    private static final int PREVIEW_MAX_LENGTH = 120;
    private static final String ENTITY_TYPE = "*";

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final UUID viewerUuid;

    private String filter = "";
    private int availPage = 0;
    private int chainPage = 0;

    /** Index into the current chain page for per-block separator editing. -1 = editing default separator. */
    private int editingSepIndex = -1;

    private static final Map<UUID, UiState> UI_STATE = new ConcurrentHashMap<>();

    NameplateBuilderPage(PlayerRef playerRef,
                         UUID viewerUuid,
                         NameplateRegistry registry,
                         NameplatePreferenceStore preferences) {
        super(playerRef,
                com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime.CanDismiss,
                SettingsData.CODEC);
        this.viewerUuid = viewerUuid;
        this.registry = registry;
        this.preferences = preferences;

        UiState state = UI_STATE.get(viewerUuid);
        if (state != null) {
            this.filter = state.filter;
            this.availPage = state.availPage;
            this.chainPage = state.chainPage;
        }
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, @NonNull Store<EntityStore> store) {
        commands.append("Pages/NameplateBuilder_Editor.ui");
        commands.set("#FilterField.Value", filter);
        commands.set("#SeparatorField.Value", preferences.getSeparator(viewerUuid, ENTITY_TYPE));

        // Filter field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#FilterField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Filter", "#FilterField.Value"),
                false);

        // Separator field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#SeparatorField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Separator", "#SeparatorField.Value"),
                false);

        // Offset field binding
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged,
                "#OffsetField",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("@Offset", "#OffsetField.Value"),
                false);

        // Button bindings
        bindAction(events, "#SaveButton", "Save");
        bindAction(events, "#CloseButton", "Close");
        bindAction(events, "#PrevAvail", "PrevAvail");
        bindAction(events, "#NextAvail", "NextAvail");
        bindAction(events, "#PrevChain", "PrevChain");
        bindAction(events, "#NextChain", "NextChain");

        // Chain block buttons (4 blocks)
        for (int i = 0; i < CHAIN_PAGE_SIZE; i++) {
            bindAction(events, "#ChainBlock" + i + "Left", "Left_" + i);
            bindAction(events, "#ChainBlock" + i + "Right", "Right_" + i);
            bindAction(events, "#ChainBlock" + i + "Remove", "Remove_" + i);
        }

        // Chain separator buttons (clickable — enter per-block separator editing)
        for (int i = 0; i < CHAIN_PAGE_SIZE - 1; i++) {
            bindAction(events, "#ChainSep" + i, "EditSep_" + i);
        }

        // Available block buttons (8 blocks)
        for (int i = 0; i < AVAIL_PAGE_SIZE; i++) {
            bindAction(events, "#AvailBlock" + i + "Add", "Add_" + i);
        }

        // Clear chain
        bindAction(events, "#ClearChainButton", "ClearChain");

        // Look-at toggle
        bindAction(events, "#LookToggle", "ToggleLook");

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
        if (data.filter != null) {
            filter = data.filter.trim();
            availPage = 0;
        }

        if (data.separator != null) {
            if (editingSepIndex >= 0) {
                // Editing a per-block separator
                SegmentKey key = getChainKeyAtPageIndex(editingSepIndex);
                if (key != null) {
                    preferences.setSeparatorAfter(viewerUuid, ENTITY_TYPE, key, data.separator);
                }
            } else {
                // Editing the default separator
                preferences.setSeparator(viewerUuid, ENTITY_TYPE, data.separator);
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
            case "Close" -> {
                close();
                return;
            }
            case "Save" -> {
                preferences.save();
                persistUiState();
                close();
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
                editingSepIndex = -1;
                sendUpdate(buildUpdate());
                return;
            }
            case "NextChain" -> {
                chainPage = chainPage + 1;
                editingSepIndex = -1;
                sendUpdate(buildUpdate());
                return;
            }
            case "ToggleLook" -> {
                boolean current = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE);
                preferences.setOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE, !current);
                sendUpdate(buildUpdate());
                return;
            }
            case "ClearChain" -> {
                clearChain();
                chainPage = 0;
                editingSepIndex = -1;
                sendUpdate(buildUpdate());
                return;
            }
            case "EditSepDefault" -> {
                editingSepIndex = -1;
                sendUpdate(buildUpdate());
                return;
            }
        }

        if (data.action.startsWith("EditSep_")) {
            int row = parseRowIndex(data.action, "EditSep_");
            if (row >= 0 && row < CHAIN_PAGE_SIZE - 1) {
                // Toggle: click again to deselect, click different to switch
                editingSepIndex = (editingSepIndex == row) ? -1 : row;
            }
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
            // If we removed the block whose separator we were editing, reset
            editingSepIndex = -1;
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Left_")) {
            int row = parseRowIndex(data.action, "Left_");
            moveRow(row, -1);
            editingSepIndex = -1;
            sendUpdate(buildUpdate());
            return;
        }

        if (data.action.startsWith("Right_")) {
            int row = parseRowIndex(data.action, "Right_");
            moveRow(row, 1);
            editingSepIndex = -1;
            sendUpdate(buildUpdate());
        }
    }

    private void handleOffsetChange(String rawOffset) {
        try {
            double value = Double.parseDouble(rawOffset.replace(',', '.'));
            preferences.setOffset(viewerUuid, ENTITY_TYPE, value);
        } catch (NumberFormatException _) {
            // Ignore invalid input — field keeps its previous value
        }
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
        commands.set("#FilterField.Value", filter);

        List<SegmentView> chain = getChainViews();

        // Validate editingSepIndex — the block may have been removed or chain changed
        if (editingSepIndex >= 0) {
            int start = chainPage * CHAIN_PAGE_SIZE;
            int end = Math.min(chain.size(), start + CHAIN_PAGE_SIZE);
            int visibleCount = end - start;
            // Can only edit separator after block i if block i+1 also exists on this page
            if (editingSepIndex >= visibleCount - 1) {
                editingSepIndex = -1;
            }
        }

        // Separator field + context label
        if (editingSepIndex >= 0) {
            SegmentKey key = getChainKeyAtPageIndex(editingSepIndex, chain);
            if (key != null) {
                String sep = preferences.getSeparatorAfter(viewerUuid, ENTITY_TYPE, key);
                commands.set("#SeparatorField.Value", sep);
                SegmentView view = getChainViewAtPageIndex(editingSepIndex, chain);
                String blockName = view != null ? view.displayName() : "block";
                commands.set("#SeparatorContext.Text", "(after " + blockName + ") — click to edit default");
            } else {
                editingSepIndex = -1;
                commands.set("#SeparatorField.Value", preferences.getSeparator(viewerUuid, ENTITY_TYPE));
                commands.set("#SeparatorContext.Text", "(default for new blocks)");
            }
        } else {
            commands.set("#SeparatorField.Value", preferences.getSeparator(viewerUuid, ENTITY_TYPE));
            commands.set("#SeparatorContext.Text", "(default for new blocks)");
        }

        // Offset field
        double offset = preferences.getOffset(viewerUuid, ENTITY_TYPE);
        commands.set("#OffsetField.Value", offset == 0.0 ? "0.0" : String.valueOf(offset));

        List<SegmentView> available = getAvailableViews();

        int totalAvailPages = Math.max(1, (int) Math.ceil(available.size() / (double) AVAIL_PAGE_SIZE));
        int totalChainPages = Math.max(1, (int) Math.ceil(chain.size() / (double) CHAIN_PAGE_SIZE));
        if (availPage >= totalAvailPages) availPage = totalAvailPages - 1;
        if (chainPage >= totalChainPages) chainPage = totalChainPages - 1;

        fillChain(commands, chain, totalChainPages);
        fillAvailable(commands, available, totalAvailPages);

        commands.set("#PreviewText.Text", buildPreview(chain));

        boolean lookOnly = preferences.isOnlyShowWhenLooking(viewerUuid, ENTITY_TYPE);
        commands.set("#LookToggle.Text", lookOnly
                ? "[x] Only show when looking at entity"
                : "[ ] Only show when looking at entity");
    }

    private void persistUiState() {
        UI_STATE.put(viewerUuid, new UiState(filter, availPage, chainPage));
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
                commands.set(prefix + "Author.Text", "by " + view.author());
            }

            // Separator indicator between blocks — now shows per-block separator
            if (i < CHAIN_PAGE_SIZE - 1) {
                boolean sepVisible = visible && (start + i + 1) < end;
                String sepId = "#ChainSep" + i;
                commands.set(sepId + ".Visible", sepVisible);
                if (sepVisible) {
                    SegmentKey blockKey = chain.get(index).key();
                    String sep = preferences.getSeparatorAfter(viewerUuid, ENTITY_TYPE, blockKey);
                    commands.set(sepId + ".Text", sep.isEmpty() ? "." : sep);
                    // Highlight selected separator in green
                    boolean selected = editingSepIndex == i;
                    commands.set(sepId + ".Background.Color", selected ? "#2d6b3f" : "#1a2840");
                }
            }
        }

        // Pagination
        boolean showPagination = totalPages > 1;
        commands.set("#ChainPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#ChainPageLabel.Text", "Page " + (chainPage + 1) + "/" + totalPages);
        }
    }

    // ── Fill Available Blocks ──

    private void fillAvailable(UICommandBuilder commands, List<SegmentView> available, int totalPages) {
        boolean hasAvailable = !available.isEmpty();
        commands.set("#AvailEmpty.Visible", !hasAvailable);
        commands.set("#AvailRow1.Visible", hasAvailable);

        int start = availPage * AVAIL_PAGE_SIZE;
        int end = Math.min(available.size(), start + AVAIL_PAGE_SIZE);

        // Show second row only if we have more than 4 items on this page
        boolean showRow2 = (end - start) > 4;
        commands.set("#AvailRow2.Visible", showRow2);

        for (int i = 0; i < AVAIL_PAGE_SIZE; i++) {
            int index = start + i;
            boolean visible = index < end;
            String prefix = "#AvailBlock" + i;
            commands.set(prefix + ".Visible", visible);
            if (visible) {
                SegmentView view = available.get(index);
                commands.set(prefix + "Label.Text", view.displayName());
                commands.set(prefix + "Sub.Text", truncateModName(view.modName()));
                commands.set(prefix + "Author.Text", "by " + view.author());
            }
        }

        // Page label
        if (hasAvailable) {
            commands.set("#AvailPageLabel.Text", "Page " + (availPage + 1) + "/" + totalPages);
        } else {
            commands.set("#AvailPageLabel.Text", "");
        }

        // Pagination
        boolean showPagination = totalPages > 1;
        commands.set("#AvailPagination.Visible", showPagination);
        if (showPagination) {
            commands.set("#AvailPaginationLabel.Text", "Page " + (availPage + 1) + "/" + totalPages);
        }
    }

    // ── Actions ──

    private void addRow(int row) {
        SegmentView view = getAvailableRow(row);
        if (view == null) {
            return;
        }
        preferences.enable(viewerUuid, ENTITY_TYPE, view.key());
    }

    private void removeRow(int row) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }
        preferences.disable(viewerUuid, ENTITY_TYPE, view.key());
    }

    private void clearChain() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        List<SegmentKey> available = new ArrayList<>(segments.keySet());
        preferences.disableAll(viewerUuid, ENTITY_TYPE, available);
    }

    private void moveRow(int row, int delta) {
        SegmentView view = getChainRow(row);
        if (view == null) {
            return;
        }
        List<SegmentKey> chainKeys = getChainViews().stream().map(SegmentView::key).toList();
        preferences.move(viewerUuid, ENTITY_TYPE, view.key(), delta, chainKeys, getDefaultComparator());
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

    /**
     * Get the SegmentKey for a block at the given page-relative index in the current chain page.
     */
    private SegmentKey getChainKeyAtPageIndex(int pageIndex) {
        return getChainKeyAtPageIndex(pageIndex, getChainViews());
    }

    private SegmentKey getChainKeyAtPageIndex(int pageIndex, List<SegmentView> chain) {
        int start = chainPage * CHAIN_PAGE_SIZE;
        int index = start + pageIndex;
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return chain.get(index).key();
    }

    private SegmentView getChainViewAtPageIndex(int pageIndex, List<SegmentView> chain) {
        int start = chainPage * CHAIN_PAGE_SIZE;
        int index = start + pageIndex;
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return chain.get(index);
    }

    // ── Data Views ──

    private List<SegmentView> getAvailableViews() {
        Map<SegmentKey, NameplateRegistry.Segment> segments = registry.getSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        List<SegmentKey> allKeys = new ArrayList<>(segments.keySet());
        List<SegmentKey> ordered = preferences.getAvailable(viewerUuid, ENTITY_TYPE, allKeys, getDefaultComparator());
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
        List<SegmentKey> allKeys = new ArrayList<>(segments.keySet());
        List<SegmentKey> ordered = preferences.getChain(viewerUuid, ENTITY_TYPE, allKeys, getDefaultComparator());
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

    private static SegmentView toView(SegmentKey key, NameplateRegistry.Segment segment) {
        return new SegmentView(
                key,
                segment.displayName(),
                segment.pluginName(),
                formatAuthorWithTarget(segment));
    }

    private static boolean matchesFilter(SegmentView view, NameplateRegistry.Segment segment, String lowerFilter) {
        return view.displayName().toLowerCase().contains(lowerFilter)
                || view.modName().toLowerCase().contains(lowerFilter)
                || view.author().toLowerCase().contains(lowerFilter)
                || segment.target().getLabel().toLowerCase().contains(lowerFilter)
                || segment.pluginId().toLowerCase().contains(lowerFilter);
    }

    private static String formatAuthorWithTarget(NameplateRegistry.Segment segment) {
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

        // Build segments one at a time using per-block separators, stopping when the next one won't fit
        StringBuilder builder = new StringBuilder();
        SegmentView prevView = null;
        for (SegmentView view : views) {
            String name = view.displayName();
            String sep = "";
            if (prevView != null) {
                sep = preferences.getSeparatorAfter(viewerUuid, ENTITY_TYPE, prevView.key());
            }
            String candidate = builder.isEmpty()
                    ? name
                    : builder + sep + name;

            if (candidate.length() > PREVIEW_MAX_LENGTH) {
                // This segment won't fit — truncate with ellipsis after the last one that did
                if (builder.isEmpty()) {
                    // Even the first segment is too long — hard truncate it
                    return name.substring(0, PREVIEW_MAX_LENGTH - 2) + " ...";
                }
                return builder + " ...";
            }
            builder = new StringBuilder(candidate);
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

    private record SegmentView(SegmentKey key, String displayName, String modName, String author) {
    }

    private record UiState(String filter, int availPage, int chainPage) {
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
                .append(new KeyedCodec<>("@Separator", Codec.STRING),
                        (SettingsData data, String value) -> data.separator = value,
                        (SettingsData data) -> data.separator)
                .add()
                .append(new KeyedCodec<>("@Offset", Codec.STRING),
                        (SettingsData data, String value) -> data.offset = value,
                        (SettingsData data) -> data.offset)
                .add()
                .build();

        String filter;
        String action;
        String separator;
        String offset;
    }
}
