package com.silver.atlantis.spawn.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.spawn.service.ProximitySpawnService;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command facade for /structuremob operations.
 * Domain logic lives in {@link ProximitySpawnService}.
 */
public final class ProximitySpawnCommandManager {

    private final ProximitySpawnService spawnService = new ProximitySpawnService();

    public int runStructureMob(ServerCommandSource source, boolean dryRun) {
        return spawnService.runStructureMob(source, dryRun);
    }

    public boolean isStructureMobRunning() {
        return spawnService.isStructureMobRunning();
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        return CommandManager.literal("structuremob")
            .executes(context -> spawnService.runStructureMob(context.getSource(), false))
            .then(CommandManager.literal("dryrun")
                .executes(context -> spawnService.runStructureMob(context.getSource(), true))
            )
            .then(CommandManager.literal("clear")
                .executes(context -> spawnService.clearStructureMob(context.getSource()))
            )
            .then(CommandManager.literal("pause")
                .then(CommandManager.literal("check")
                    .executes(context -> spawnService.checkSpawnPause(context.getSource()))
                )
                .then(CommandManager.literal("clear")
                    .executes(context -> spawnService.clearSpawnPause(context.getSource()))
                )
            );
    }
}
