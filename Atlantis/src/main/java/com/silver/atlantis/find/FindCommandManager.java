package com.silver.atlantis.find;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Debug command: runs the flat-area finder and prints the chosen center.
 */
public final class FindCommandManager {

    private final FlatAreaSearchService searchService;

    public FindCommandManager(FlatAreaSearchService searchService) {
        this.searchService = searchService;
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        return CommandManager.literal("findflat")
            .executes(context -> execute(context.getSource()));
    }

    private int execute(ServerCommandSource source) {
        FlatAreaSearchConfig config = FlatAreaSearchConfig.defaults();

        boolean started = searchService.start(source, config);
        if (!started) {
            source.sendFeedback(() -> Text.literal("A flat-area search is already running."), false);
            return 0;
        }

        String details = String.format(Locale.ROOT,
            "Started flat-area search: window=%dx%d, baseThreshold=%d (+1 every %d attempts), maxRadius=%d, minRadius=%d, plateauRadius=%d, plateauChance=%.2f, step=%d, maxAttempts=%s, tickBudget=%.2fms per tick. Progress updates will be posted. (Min distance ramp: +%d blocks every %d attempts.)",
            config.windowSizeBlocks(),
            config.windowSizeBlocks(),
            config.maxAvgAbsDeviation(),
            config.thresholdIncreaseEveryAttempts(),
            config.maxRadiusBlocks(),
            config.minRadiusBlocks(),
            config.plateauRadiusBlocks(),
            config.plateauChance(),
            config.stepBlocks(),
            config.maxAttempts() > 0 ? Integer.toString(config.maxAttempts()) : "unlimited",
            config.tickTimeBudgetNanos() / 1_000_000.0
            ,
            config.minRadiusIncreaseBlocks(),
            config.minRadiusIncreaseEveryAttempts()
        );
        source.sendFeedback(() -> Text.literal(details), false);
        return 1;
    }
}
