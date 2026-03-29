package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

final class NameplateBuilderCommand extends AbstractPlayerCommand {


    static final String PERMISSION_ADMIN = "nameplatebuilder.admin";

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;
    private final NameplateBuilderPlugin plugin;

    NameplateBuilderCommand(NameplateRegistry registry, NameplatePreferenceStore preferences, AdminConfigStore adminConfig, NameplateBuilderPlugin plugin) {
        super("nameplatebuilder", "Open the Nameplate Builder UI");
        addAliases("npb", "nameplateui");
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
        this.plugin = plugin;
    }


    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        if (!playerRef.isValid()) {
            context.sendMessage(Message.raw("Player reference is not available.")
                    .color("RED"));
            return;
        }

        CommandSender sender = context.sender();
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players.")
                    .color("RED"));
            return;
        }

        UUID viewerUuid = playerRef.getUuid();
        boolean isAdmin = context.sender().hasPermission(PERMISSION_ADMIN);
        NameplateBuilderPage page = new NameplateBuilderPage(playerRef, viewerUuid, registry, preferences, adminConfig, isAdmin, plugin);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
