package com.silver.atlantis.cycle;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CycleCommandManager {

    private final CycleService cycleService;

    public CycleCommandManager(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
        return Commands.literal("cycle")
            .executes(ctx -> {
                cycleService.toggle(ctx.getSource());
                return 1;
            })
            .then(Commands.literal("status")
                .executes(ctx -> {
                    cycleService.sendStatus(ctx.getSource());
                    return 1;
                }))
            .then(Commands.literal("step")
                .executes(ctx -> {
                    cycleService.stepOnceNow(ctx.getSource());
                    return 1;
                }))
            .then(Commands.literal("set")
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests(CycleCommandManager::suggestStages)
                    .executes(ctx -> {
                        String stage = StringArgumentType.getString(ctx, "stage");
                        cycleService.setStage(ctx.getSource(), stage);
                        return 1;
                    })))
            .then(Commands.literal("run")
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests(CycleCommandManager::suggestStages)
                    .executes(ctx -> {
                        String stage = StringArgumentType.getString(ctx, "stage");
                        cycleService.runStageNow(ctx.getSource(), stage);
                        return 1;
                    })));
    }

    private static CompletableFuture<Suggestions> suggestStages(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (CycleState.Stage s : CycleState.Stage.values()) {
            builder.suggest(s.name());
        }
        builder.suggest("structuremob");
        builder.suggest("construct");
        builder.suggest("undo");
        builder.suggest("findflat");
        return builder.buildFuture();
    }
}
