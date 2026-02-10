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
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Command handler for {@code /nameplatebuilder} (aliases: {@code /npb}, {@code /nameplateui}).
 *
 * <p>Opens the {@link NameplateBuilderPage} UI for the executing player.
 * The command checks the {@value #PERMISSION_ADMIN} permission node to
 * determine whether the player sees the admin settings tab.</p>
 */
final class NameplateBuilderCommand extends AbstractPlayerCommand {

    /** Permission node that grants access to the admin "Required Segments" tab. */
    static final String PERMISSION_ADMIN = "nameplatebuilder.admin";

    private final NameplateRegistry registry;
    private final NameplatePreferenceStore preferences;
    private final AdminConfigStore adminConfig;

    NameplateBuilderCommand(NameplateRegistry registry, NameplatePreferenceStore preferences, AdminConfigStore adminConfig) {
        super("nameplatebuilder", "Open the Nameplate Builder UI");
        addAliases("npb", "nameplateui");
        this.registry = registry;
        this.preferences = preferences;
        this.adminConfig = adminConfig;
    }

    /**
     * Disable auto-generated permission so all players can use the command.
     * Admin features are gated at runtime via {@link #PERMISSION_ADMIN} instead.
     */
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        if (!playerRef.isValid()) {
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
        boolean isAdmin = context.sender().hasPermission(PERMISSION_ADMIN);
        NameplateBuilderPage page = new NameplateBuilderPage(playerRef, viewerUuid, registry, preferences, adminConfig, isAdmin);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
