package com.silver.atlantis.construct;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.silver.atlantis.find.FlatAreaSearchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * Op-only command that starts constructing from the last /findflat result center.
 */
public final class ConstructCommandManager {

    private final FlatAreaSearchService searchService;
    private final ConstructService constructService;

    public ConstructCommandManager(FlatAreaSearchService searchService, ConstructService constructService) {
        this.searchService = searchService;
        this.constructService = constructService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
        // Note: permission gating is done at the /atlantis root.
        return Commands.literal("construct")
            .then(Commands.literal("undo")
                .executes(context -> executeUndo(context.getSource(), null))
                .then(Commands.argument("runId", StringArgumentType.word())
                    .executes(context -> executeUndo(context.getSource(), StringArgumentType.getString(context, "runId")))
                )
            )
            .executes(context -> execute(context.getSource(), null))
            .then(Commands.argument("yOffset", IntegerArgumentType.integer(-512, 512))
                .executes(context -> execute(context.getSource(), IntegerArgumentType.getInteger(context, "yOffset")))
            );
    }

    private int executeUndo(CommandSourceStack source, String runIdOrNull) {
        ConstructConfig config = ConstructConfig.defaults();
        boolean started = constructService.startUndo(source, config, source.getServer(), runIdOrNull);
        if (!started) {
            source.sendSuccess(() -> Component.literal("A construct/undo job is already running."), false);
            return 0;
        }

        if (runIdOrNull == null) {
            source.sendSuccess(() -> Component.literal("Undo job started (latest run)."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Undo job started for run: " + runIdOrNull), false);
        }
        return 1;
    }

    private int execute(CommandSourceStack source, Integer yOffsetOverride) {
        if (searchService.isRunning()) {
            source.sendSuccess(() -> Component.literal("A /findflat search is still running. Wait for it to finish before running /construct."), false);
            return 0;
        }

        ServerLevel world = source.getServer().getLevel(Level.OVERWORLD);
        if (world == null) {
            source.sendSuccess(() -> Component.literal("Overworld missing."), false);
            return 0;
        }

        BlockPos center = searchService.getLastResultCenterOrNull();
        if (center == null) {
            boolean resumed = constructService.resumeLatest(source, ConstructConfig.defaults(), world);
            if (!resumed) {
                source.sendSuccess(() -> Component.literal("No last /findflat result found, and no resumable construct run was found."), false);
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Resumed previous construct run."), false);
            return 1;
        }

        ConstructConfig defaults = ConstructConfig.defaults();
        ConstructConfig config = (yOffsetOverride == null)
            ? defaults
            : defaults.withYOffsetBlocks(yOffsetOverride);
        boolean started = constructService.start(source, config, world, center);
        if (!started) {
            source.sendSuccess(() -> Component.literal("A construct job is already running."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Construct job started. Using /findflat center: " + center + " (yOffset=" + config.yOffsetBlocks() + ")"), false);
        return 1;
    }
}
