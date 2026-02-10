package com.silver.atlantis;

import com.mojang.brigadier.CommandDispatcher;
import com.silver.atlantis.construct.ConstructCommandManager;
import com.silver.atlantis.construct.ConstructService;
import com.silver.atlantis.cycle.CycleCommandManager;
import com.silver.atlantis.find.FindCommandManager;
import com.silver.atlantis.heightcap.HeightCapCommandManager;
import com.silver.atlantis.spawn.SpawnCommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Root command for Atlantis admin actions.
 *
 * All supported commands are subcommands of /atlantis.
 */
public final class AtlantisCommandManager {

    private final FindCommandManager findCommandManager;
    private final ConstructCommandManager constructCommandManager;
    private final CycleCommandManager cycleCommandManager;
    private final SpawnCommandManager spawnCommandManager;
    private final ConstructService constructService;
    private final HeightCapCommandManager heightCapCommandManager;

    public AtlantisCommandManager(
        FindCommandManager findCommandManager,
        ConstructCommandManager constructCommandManager,
        CycleCommandManager cycleCommandManager,
        SpawnCommandManager spawnCommandManager,
        ConstructService constructService,
        HeightCapCommandManager heightCapCommandManager
    ) {
        this.findCommandManager = findCommandManager;
        this.constructCommandManager = constructCommandManager;
        this.cycleCommandManager = cycleCommandManager;
        this.spawnCommandManager = spawnCommandManager;
        this.constructService = constructService;
        this.heightCapCommandManager = heightCapCommandManager;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommand(dispatcher)
        );
    }

    private void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("atlantis")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("pause")
                    .executes(context -> pause(context.getSource()))
                )
                .then(CommandManager.literal("resume")
                    .executes(context -> resume(context.getSource()))
                )
                .then(cycleCommandManager.buildSubcommand())
                .then(findCommandManager.buildSubcommand())
                .then(constructCommandManager.buildSubcommand())
                .then(spawnCommandManager.buildSubcommand())
                .then(heightCapCommandManager.buildSubcommand())
        );
    }

    private int pause(ServerCommandSource source) {
        if (!constructService.isRunning()) {
            source.sendFeedback(() -> Text.literal("No construct/undo job is running."), false);
            return 0;
        }
        if (constructService.isPaused()) {
            source.sendFeedback(() -> Text.literal("Job is already paused: " + constructService.getActiveJobDescription()), false);
            return 0;
        }

        constructService.pause();
        source.sendFeedback(() -> Text.literal("Paused job: " + constructService.getActiveJobDescription()), false);
        return 1;
    }

    private int resume(ServerCommandSource source) {
        if (!constructService.isRunning()) {
            source.sendFeedback(() -> Text.literal("No construct/undo job is running."), false);
            return 0;
        }
        if (!constructService.isPaused()) {
            source.sendFeedback(() -> Text.literal("Job is not paused: " + constructService.getActiveJobDescription()), false);
            return 0;
        }

        constructService.resume();
        source.sendFeedback(() -> Text.literal("Resumed job: " + constructService.getActiveJobDescription()), false);
        return 1;
    }
}
