package com.silver.enderfight.command;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.reset.EndResetManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers administrative commands for Ender Fight. Currently exposes a single command that forces
 * an immediate End reset without waiting for the scheduled countdown.
 */
public final class EnderFightCommands {
    private static final SimpleCommandExceptionType RESET_FAILED =
        new SimpleCommandExceptionType(Component.literal("Unable to reset The End; see server logs for details."));
    private static final SimpleCommandExceptionType NOT_READY =
        new SimpleCommandExceptionType(Component.literal("End reset system not initialised yet."));

    private EnderFightCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("enderfight")
            .requires(AuthorizationChecks.requires(PermissionNodes.ENDERFIGHT_RESET))
            .then(Commands.literal("resetend")
                .executes(context -> executeReset(context.getSource()))));
    }

    private static int executeReset(CommandSourceStack source) throws CommandSyntaxException {
        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (manager == null) {
            throw NOT_READY.create();
        }

        boolean success = manager.triggerManualReset(source.getServer());
        if (!success) {
            throw RESET_FAILED.create();
        }

        source.sendSuccess(() -> Component.literal("Manual End reset triggered."), true);
        return 1;
    }
}
