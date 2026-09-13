package com.silver.atlantis.spawn.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.spawn.service.ProximitySpawnService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Command facade for /structuremob operations.
 * Domain logic lives in {@link ProximitySpawnService}.
 */
public final class ProximitySpawnCommandManager {

    private final ProximitySpawnService spawnService = new ProximitySpawnService();

    public int runStructureMob(CommandSourceStack source, boolean dryRun) {
        return spawnService.runStructureMob(source, dryRun);
    }

    public boolean isStructureMobRunning() {
        return spawnService.isStructureMobRunning();
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
        return Commands.literal("structuremob")
            .executes(context -> spawnService.runStructureMob(context.getSource(), false))
            .then(Commands.literal("dryrun")
                .executes(context -> spawnService.runStructureMob(context.getSource(), true))
            )
            .then(Commands.literal("clear")
                .executes(context -> spawnService.clearStructureMob(context.getSource()))
            )
            .then(Commands.literal("pause")
                .then(Commands.literal("check")
                    .executes(context -> spawnService.checkSpawnPause(context.getSource()))
                )
                .then(Commands.literal("clear")
                    .executes(context -> spawnService.clearSpawnPause(context.getSource()))
                )
            );
    }
}
