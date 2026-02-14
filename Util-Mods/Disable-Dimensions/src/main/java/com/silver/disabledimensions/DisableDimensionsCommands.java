package com.silver.disabledimensions;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

final class DisableDimensionsCommands {
    private static final Text NETHER_ENABLED = Text.literal("Nether has been enabled.");
    private static final Text END_ENABLED = Text.literal("The End has been enabled.");
    private static final Text ENABLE_NETHER_FAILED = Text.literal("Failed to update config while enabling Nether.");
    private static final Text ENABLE_END_FAILED = Text.literal("Failed to update config while enabling The End.");

    private DisableDimensionsCommands() {
    }

    static void register(DisableDimensionsManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, manager));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, DisableDimensionsManager manager) {
        dispatcher.register(CommandManager.literal("dimensions")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("enable")
                .then(CommandManager.literal("nether")
                    .executes(context -> enableNether(context.getSource(), manager)))
                .then(CommandManager.literal("end")
                    .executes(context -> enableEnd(context.getSource(), manager)))));

        dispatcher.register(CommandManager.literal("disabledimensions")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("enable")
                .then(CommandManager.literal("nether")
                    .executes(context -> enableNether(context.getSource(), manager)))
                .then(CommandManager.literal("end")
                    .executes(context -> enableEnd(context.getSource(), manager)))));
    }

    private static int enableNether(ServerCommandSource source, DisableDimensionsManager manager) {
        if (manager.enableNether()) {
            source.sendFeedback(() -> NETHER_ENABLED, true);
            return 1;
        }

        source.sendError(ENABLE_NETHER_FAILED);
        return 0;
    }

    private static int enableEnd(ServerCommandSource source, DisableDimensionsManager manager) {
        if (manager.enableEnd()) {
            source.sendFeedback(() -> END_ENABLED, true);
            return 1;
        }

        source.sendError(ENABLE_END_FAILED);
        return 0;
    }
}
