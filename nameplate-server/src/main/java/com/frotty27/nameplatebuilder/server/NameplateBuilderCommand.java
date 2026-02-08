package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

final class NameplateBuilderCommand extends AbstractPlayerCommand {

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;

    NameplateBuilderCommand(NameplateRegistry registry, NameplatePreferenceStore preferences) {
        super("nameplatebuilder", "Open the Nameplate Builder UI");
        addAliases("npb", "nameplateui");
        this.registry = registry;
        this.preferences = preferences;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        if (playerRef == null || !playerRef.isValid()) {
            context.sendMessage(com.hypixel.hytale.server.core.Message.raw("Player reference is not available.")
                    .color("RED"));
            return;
        }

        CommandSender sender = context.sender();
        if (!(sender instanceof Player player)) {
            context.sendMessage(com.hypixel.hytale.server.core.Message.raw("This command can only be used by players.")
                    .color("RED"));
            return;
        }

        UUID viewerUuid = playerRef.getUuid();
        NameplateBuilderPage page = new NameplateBuilderPage(playerRef, viewerUuid, registry, preferences);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
