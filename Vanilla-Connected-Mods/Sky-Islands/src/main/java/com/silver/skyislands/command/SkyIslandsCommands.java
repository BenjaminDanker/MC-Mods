package com.silver.skyislands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SkyIslandsCommands {
    private SkyIslandsCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("skyislands")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("Usage: /skyislands dragons [all|virtual|loaded]"), false);
                    return 1;
                })
                .then(CommandManager.literal("dragons")
                        .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, true))
                        .then(CommandManager.literal("all")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, true)))
                        .then(CommandManager.literal("virtual")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, false)))
                        .then(CommandManager.literal("loaded")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), false, true)))
                ));
    }
}
