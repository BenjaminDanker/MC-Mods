package com.silver.villagerinterface.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.conversation.ConversationManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.network.chat.Component;

public final class VillagerInterfaceCommands {
    private static final SimpleCommandExceptionType PLAYER_ONLY =
        new SimpleCommandExceptionType(Component.literal("This command must be run by a player."));

    private VillagerInterfaceCommands() {
    }

    public static void register() {
        VillagerInterfaceMod.LOGGER.info("Hooking Villager Interface command registration callback");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VillagerInterfaceMod.LOGGER.info("Registering Villager Interface commands (env={})", environment);
            registerCommands(dispatcher);
        });
    }

    public static void registerNow(MinecraftServer server) {
        VillagerInterfaceMod.LOGGER.info("Registering Villager Interface commands (server started)");
        registerCommands(server.getCommands().getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("villagerinterface")
            .requires(source -> source.permissions() instanceof LevelBasedPermissionSet level
                && level.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("devtest")
                .executes(context -> executeDevTest(context.getSource(), 4))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> executeDevTest(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count")
                    )))));

        dispatcher.register(Commands.literal("vi")
            .requires(source -> source.permissions() instanceof LevelBasedPermissionSet level
                && level.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("devtest")
                .executes(context -> executeDevTest(context.getSource(), 4))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> executeDevTest(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count")
                    )))));
    }

    private static int executeDevTest(CommandSourceStack source, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw PLAYER_ONLY.create();
        }

        ConversationManager manager = VillagerInterfaceMod.getConversationManager();
        if (manager == null) {
            throw new SimpleCommandExceptionType(Component.literal("Conversation system not initialized.")).create();
        }

        return manager.runDevProviderTest(player, count);
    }
}
