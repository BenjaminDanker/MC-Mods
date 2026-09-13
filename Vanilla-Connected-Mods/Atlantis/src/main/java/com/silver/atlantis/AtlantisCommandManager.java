package com.silver.atlantis;

import com.mojang.brigadier.CommandDispatcher;
import com.silver.atlantis.construct.ConstructCommandManager;
import com.silver.atlantis.construct.ConstructService;
import com.silver.atlantis.cycle.CycleCommandManager;
import com.silver.atlantis.find.FindCommandManager;
import com.silver.atlantis.heightcap.HeightCapCommandManager;
import com.silver.atlantis.leviathan.LeviathanCommandManager;
import com.silver.atlantis.spawn.command.ProximitySpawnCommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Root command for Atlantis admin actions.
 *
 * All supported commands are subcommands of /atlantis.
 */
public final class AtlantisCommandManager {

    private final FindCommandManager findCommandManager;
    private final ConstructCommandManager constructCommandManager;
    private final CycleCommandManager cycleCommandManager;
    private final ProximitySpawnCommandManager spawnCommandManager;
    private final ConstructService constructService;
    private final HeightCapCommandManager heightCapCommandManager;
    private final LeviathanCommandManager leviathanCommandManager;

    public AtlantisCommandManager(
        FindCommandManager findCommandManager,
        ConstructCommandManager constructCommandManager,
        CycleCommandManager cycleCommandManager,
        ProximitySpawnCommandManager spawnCommandManager,
        ConstructService constructService,
        HeightCapCommandManager heightCapCommandManager,
        LeviathanCommandManager leviathanCommandManager
    ) {
        this.findCommandManager = findCommandManager;
        this.constructCommandManager = constructCommandManager;
        this.cycleCommandManager = cycleCommandManager;
        this.spawnCommandManager = spawnCommandManager;
        this.constructService = constructService;
        this.heightCapCommandManager = heightCapCommandManager;
        this.leviathanCommandManager = leviathanCommandManager;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommand(dispatcher)
        );
    }

    private void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("atlantis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("pause")
                    .executes(context -> pause(context.getSource()))
                )
                .then(Commands.literal("resume")
                    .executes(context -> resume(context.getSource()))
                )
                .then(cycleCommandManager.buildSubcommand())
                .then(findCommandManager.buildSubcommand())
                .then(constructCommandManager.buildSubcommand())
                .then(spawnCommandManager.buildSubcommand())
                .then(heightCapCommandManager.buildSubcommand())
                .then(leviathanCommandManager.buildSubcommand())
        );
    }

    private int pause(CommandSourceStack source) {
        if (!constructService.isRunning()) {
            source.sendSuccess(() -> Component.literal("No construct/undo job is running."), false);
            return 0;
        }
        if (constructService.isPaused()) {
            source.sendSuccess(() -> Component.literal("Job is already paused: " + constructService.getActiveJobDescription()), false);
            return 0;
        }

        constructService.pause();
        source.sendSuccess(() -> Component.literal("Paused job: " + constructService.getActiveJobDescription()), false);
        return 1;
    }

    private int resume(CommandSourceStack source) {
        if (!constructService.isRunning()) {
            source.sendSuccess(() -> Component.literal("No construct/undo job is running."), false);
            return 0;
        }
        if (!constructService.isPaused()) {
            source.sendSuccess(() -> Component.literal("Job is not paused: " + constructService.getActiveJobDescription()), false);
            return 0;
        }

        constructService.resume();
        source.sendSuccess(() -> Component.literal("Resumed job: " + constructService.getActiveJobDescription()), false);
        return 1;
    }
}
