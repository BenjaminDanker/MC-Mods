package com.silver.atlantis;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
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
                .requires(AuthorizationChecks.requires(PermissionNodes.ATLANTIS_MANAGE))
                .then(Commands.literal("pause")
                    .executes(context -> pause(context.getSource()))
                )
                .then(Commands.literal("resume")
                    .executes(context -> resume(context.getSource()))
                )
                .then(Commands.literal("where")
                    .executes(context -> where(context.getSource()))
                )
                .then(cycleCommandManager.buildSubcommand())
                .then(findCommandManager.buildSubcommand())
                .then(constructCommandManager.buildSubcommand())
                .then(spawnCommandManager.buildSubcommand())
                .then(heightCapCommandManager.buildSubcommand())
                .then(leviathanCommandManager.buildSubcommand())
        );
    }

    private int where(CommandSourceStack source) {
        var cycle = com.silver.atlantis.cycle.CycleJsonIO.tryRead(com.silver.atlantis.cycle.CyclePaths.stateFile());
        var bounds = com.silver.atlantis.spawn.bounds.ActiveConstructBoundsResolver.tryResolveLatest();
        if (cycle == null || cycle.lastCenterX() == null || cycle.lastCenterZ() == null) {
            source.sendSuccess(() -> Component.literal("Atlantis has not been built yet (no recorded location)."), false);
            return 0;
        }
        int x = cycle.lastCenterX();
        int y = cycle.lastCenterY() == null ? 60 : cycle.lastCenterY();
        int z = cycle.lastCenterZ();
        String dimension = bounds == null ? "minecraft:overworld" : bounds.dimensionId();
        String size = bounds == null ? "" : String.format(java.util.Locale.ROOT,
            " | spans x %d..%d, y %d..%d, z %d..%d",
            bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ());
        String next = cycle.nextRunAtEpochMillis() > 0
            ? " | next cycle step at " + java.time.Instant.ofEpochMilli(cycle.nextRunAtEpochMillis()) : "";
        String message = String.format(java.util.Locale.ROOT, "Atlantis is at %d %d %d in %s%s | cycle stage %s",
            x, y, z, dimension, size, cycle.stage());
        source.sendSuccess(() -> Component.literal(message).append(Component.literal(" [teleport]")
            .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(
                new net.minecraft.network.chat.ClickEvent.SuggestCommand(
                    String.format(java.util.Locale.ROOT, "/execute in %s run tp @s %d %d %d", dimension, x, y, z))))), false);
        return 1;
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
