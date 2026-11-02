package com.silver.disabledimensions;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class DisableDimensionsCommands {
    private static final Component NETHER_ENABLED = Component.literal("Nether has been enabled.");
    private static final Component END_ENABLED = Component.literal("The End has been enabled.");
    private static final Component ENABLE_NETHER_FAILED = Component.literal("Failed to update config while enabling Nether.");
    private static final Component ENABLE_END_FAILED = Component.literal("Failed to update config while enabling The End.");

    private DisableDimensionsCommands() {
    }

    static void register(DisableDimensionsManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, manager));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, DisableDimensionsManager manager) {
        dispatcher.register(Commands.literal("dimensions")
            .requires(AuthorizationChecks.requires(PermissionNodes.DIMENSIONS_MANAGE))
            .then(Commands.literal("enable")
                .then(Commands.literal("nether")
                    .executes(context -> enableNether(context.getSource(), manager)))
                .then(Commands.literal("end")
                    .executes(context -> enableEnd(context.getSource(), manager)))));

        dispatcher.register(Commands.literal("disabledimensions")
            .requires(AuthorizationChecks.requires(PermissionNodes.DIMENSIONS_MANAGE))
            .then(Commands.literal("enable")
                .then(Commands.literal("nether")
                    .executes(context -> enableNether(context.getSource(), manager)))
                .then(Commands.literal("end")
                    .executes(context -> enableEnd(context.getSource(), manager)))));
    }

    private static int enableNether(CommandSourceStack source, DisableDimensionsManager manager) {
        if (manager.enableNether()) {
            source.sendSuccess(() -> NETHER_ENABLED, true);
            return 1;
        }

        source.sendFailure(ENABLE_NETHER_FAILED);
        return 0;
    }

    private static int enableEnd(CommandSourceStack source, DisableDimensionsManager manager) {
        if (manager.enableEnd()) {
            source.sendSuccess(() -> END_ENABLED, true);
            return 1;
        }

        source.sendFailure(ENABLE_END_FAILED);
        return 0;
    }
}
