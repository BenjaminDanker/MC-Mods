package com.silver.atlantis.spawn.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.spawn.service.ProximitySpawnService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Command facade for /atlantis mobs. Domain logic lives in {@link ProximitySpawnService}.
 *
 *   /atlantis mobs                 status: seeded markers, alive mobs, whether spawning is allowed and why
 *   /atlantis mobs seed [dryrun]   (re)seed mob markers for the current build
 *   /atlantis mobs clear           remove the current build's mob markers
 *   /atlantis mobs unpause         release any stuck spawn-pause tokens
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
        return Commands.literal("mobs")
            .executes(context -> spawnService.mobStatus(context.getSource()))
            .then(Commands.literal("status")
                .executes(context -> spawnService.mobStatus(context.getSource()))
            )
            .then(Commands.literal("seed")
                .executes(context -> spawnService.runStructureMob(context.getSource(), false))
                .then(Commands.literal("dryrun")
                    .executes(context -> spawnService.runStructureMob(context.getSource(), true))
                )
            )
            .then(Commands.literal("clear")
                .executes(context -> spawnService.clearStructureMob(context.getSource()))
            )
            .then(Commands.literal("unpause")
                .executes(context -> spawnService.clearSpawnPause(context.getSource()))
            );
    }
}
