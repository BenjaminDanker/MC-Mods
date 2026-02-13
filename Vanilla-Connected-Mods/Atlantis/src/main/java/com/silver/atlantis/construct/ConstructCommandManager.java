package com.silver.atlantis.construct;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.silver.atlantis.find.FlatAreaSearchService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        // Note: permission gating is done at the /atlantis root.
        return CommandManager.literal("construct")
            .then(CommandManager.literal("undo")
                .executes(context -> executeUndo(context.getSource(), null))
                .then(CommandManager.argument("runId", StringArgumentType.word())
                    .executes(context -> executeUndo(context.getSource(), StringArgumentType.getString(context, "runId")))
                )
            )
            .executes(context -> execute(context.getSource(), null))
            .then(CommandManager.argument("yOffset", IntegerArgumentType.integer(-512, 512))
                .executes(context -> execute(context.getSource(), IntegerArgumentType.getInteger(context, "yOffset")))
            );
    }

    private int executeUndo(ServerCommandSource source, String runIdOrNull) {
        ConstructConfig config = ConstructConfig.defaults();
        boolean started = constructService.startUndo(source, config, source.getServer(), runIdOrNull);
        if (!started) {
            source.sendFeedback(() -> Text.literal("A construct/undo job is already running."), false);
            return 0;
        }

        if (runIdOrNull == null) {
            source.sendFeedback(() -> Text.literal("Undo job started (latest run)."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Undo job started for run: " + runIdOrNull), false);
        }
        return 1;
    }

    private int execute(ServerCommandSource source, Integer yOffsetOverride) {
        if (searchService.isRunning()) {
            source.sendFeedback(() -> Text.literal("A /findflat search is still running. Wait for it to finish before running /construct."), false);
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(World.OVERWORLD);
        if (world == null) {
            source.sendFeedback(() -> Text.literal("Overworld missing."), false);
            return 0;
        }

        BlockPos center = searchService.getLastResultCenterOrNull();
        if (center == null) {
            boolean resumed = constructService.resumeLatest(source, ConstructConfig.defaults(), world);
            if (!resumed) {
                source.sendFeedback(() -> Text.literal("No last /findflat result found, and no resumable construct run was found."), false);
                return 0;
            }

            source.sendFeedback(() -> Text.literal("Resumed previous construct run."), false);
            return 1;
        }

        ConstructConfig defaults = ConstructConfig.defaults();
        ConstructConfig config = (yOffsetOverride == null)
            ? defaults
            : new ConstructConfig(
                defaults.delayBetweenStagesTicks(),
                defaults.delayBetweenSlicesTicks(),
                defaults.maxChunksToLoadPerTick(),
                yOffsetOverride,
                defaults.maxEntitiesToProcessPerTick(),
                defaults.playerEjectMarginBlocks(),
                defaults.playerEjectTeleportOffsetBlocks(),
                defaults.pasteFlushEveryBlocks(),
                defaults.tickTimeBudgetNanos(),
                defaults.undoTickTimeBudgetNanos(),
                defaults.undoFlushEveryBlocks(),
                defaults.maxFluidNeighborUpdatesPerTick(),
                defaults.expectedTickNanos(),
                defaults.adaptiveScaleSmoothing(),
                defaults.adaptiveScaleMin(),
                defaults.adaptiveScaleMax()
            );
        boolean started = constructService.start(source, config, world, center);
        if (!started) {
            source.sendFeedback(() -> Text.literal("A construct job is already running."), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Construct job started. Using /findflat center: " + center + " (yOffset=" + config.yOffsetBlocks() + ")"), false);
        return 1;
    }
}
