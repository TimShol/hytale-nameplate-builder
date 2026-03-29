package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

final class NameplateDebugCommand extends AbstractPlayerCommand {

    NameplateDebugCommand() {
        super("npbdebug", "Toggle NameplateBuilder debug logging");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        if (!context.sender().hasPermission(NameplateBuilderCommand.PERMISSION_ADMIN)) {
            context.sendMessage(Message.raw("No permission.").color("RED"));
            return;
        }

        boolean newState = !DefaultSegmentSystem.isDebugEnabled();
        DefaultSegmentSystem.setDebugEnabled(newState);

        if (context.sender() instanceof Player player) {
            player.sendMessage(Message.raw("Debug logging " + (newState ? "ENABLED" : "DISABLED")
                    + ". Check server console.").color(newState ? "#55FF55" : "#FF5555"));
        }
    }
}
