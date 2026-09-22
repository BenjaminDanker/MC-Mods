package com.silver.skyislands.command;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import com.mojang.brigadier.CommandDispatcher;
import com.silver.skyislands.enderdragons.EnderDragonManager;
import com.silver.skyislands.giantmobs.GiantMobManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class SkyIslandsCommands {
    private SkyIslandsCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skyislands")
                .requires(AuthorizationChecks.requires(PermissionNodes.SKYISLANDS_INSPECT))
                .executes(ctx -> {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Usage: /skyislands dragons [all|virtual|loaded] | /skyislands giants [all|virtual|loaded|projectiles]"), false);
                    return 1;
                })
                .then(Commands.literal("dragons")
                        .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, true))
                        .then(Commands.literal("all")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, true)))
                        .then(Commands.literal("virtual")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), true, false)))
                        .then(Commands.literal("loaded")
                                .executes(ctx -> EnderDragonManager.dumpDragons(ctx.getSource(), false, true)))
                )
                .then(Commands.literal("giants")
                        .executes(ctx -> GiantMobManager.dumpGiants(ctx.getSource(), true, true, true))
                        .then(Commands.literal("all")
                                .executes(ctx -> GiantMobManager.dumpGiants(ctx.getSource(), true, true, true)))
                        .then(Commands.literal("virtual")
                                .executes(ctx -> GiantMobManager.dumpGiants(ctx.getSource(), true, false, false)))
                        .then(Commands.literal("loaded")
                                .executes(ctx -> GiantMobManager.dumpGiants(ctx.getSource(), false, true, true)))
                        .then(Commands.literal("projectiles")
                                .executes(ctx -> GiantMobManager.dumpGiants(ctx.getSource(), false, false, true)))
                ));
    }
}
