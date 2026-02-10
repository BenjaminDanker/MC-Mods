package com.silver.villagerinterface.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.conversation.ConversationManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class VillagerInterfaceCommands {
    private static final SimpleCommandExceptionType PLAYER_ONLY =
        new SimpleCommandExceptionType(Text.literal("This command must be run by a player."));

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
        registerCommands(server.getCommandManager().getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("villagerinterface")
            .then(CommandManager.literal("devtest")
                .executes(context -> executeDevTest(context.getSource(), 4))
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> executeDevTest(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count")
                    )))));

        dispatcher.register(CommandManager.literal("vi")
            .then(CommandManager.literal("devtest")
                .executes(context -> executeDevTest(context.getSource(), 4))
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> executeDevTest(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count")
                    )))));
    }

    private static int executeDevTest(ServerCommandSource source, int count) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            throw PLAYER_ONLY.create();
        }

        ConversationManager manager = VillagerInterfaceMod.getConversationManager();
        if (manager == null) {
            throw new SimpleCommandExceptionType(Text.literal("Conversation system not initialized.")).create();
        }

        return manager.runDevOllamaTest(player, count);
    }
}
