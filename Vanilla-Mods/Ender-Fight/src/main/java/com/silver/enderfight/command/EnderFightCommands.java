package com.silver.enderfight.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.reset.EndResetManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Registers administrative commands for Ender Fight. Currently exposes a single command that forces
 * an immediate End reset without waiting for the scheduled countdown.
 */
public final class EnderFightCommands {
    private static final SimpleCommandExceptionType RESET_FAILED =
        new SimpleCommandExceptionType(Text.literal("Unable to reset The End; see server logs for details."));
    private static final SimpleCommandExceptionType NOT_READY =
        new SimpleCommandExceptionType(Text.literal("End reset system not initialised yet."));

    private EnderFightCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("enderfight")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.literal("resetend")
                .executes(context -> executeReset(context.getSource()))));
    }

    private static int executeReset(ServerCommandSource source) throws CommandSyntaxException {
        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (manager == null) {
            throw NOT_READY.create();
        }

        boolean success = manager.triggerManualReset(source.getServer());
        if (!success) {
            throw RESET_FAILED.create();
        }

        source.sendFeedback(() -> Text.literal("Manual End reset triggered."), true);
        return 1;
    }
}
