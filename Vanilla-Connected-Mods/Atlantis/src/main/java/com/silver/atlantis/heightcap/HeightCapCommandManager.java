package com.silver.atlantis.heightcap;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class HeightCapCommandManager {

    private final HeightCapService service;

    public HeightCapCommandManager(HeightCapService service) {
        this.service = service;
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        return CommandManager.literal("heightcap")
            .executes(ctx -> status(ctx.getSource()))
            .then(CommandManager.literal("status")
                .executes(ctx -> status(ctx.getSource())))
            .then(CommandManager.literal("enable")
                .executes(ctx -> setEnabled(ctx.getSource(), true)))
            .then(CommandManager.literal("disable")
                .executes(ctx -> setEnabled(ctx.getSource(), false)));
    }

    private int status(ServerCommandSource source) {
        boolean enabled = service.isEnabled();
        source.sendFeedback(() -> Text.literal("Height cap is " + (enabled ? "ENABLED" : "DISABLED") + "."), false);
        source.sendFeedback(() -> Text.literal("Rule: players at/above Y=318 are teleported to Y=317."), false);
        return 1;
    }

    private int setEnabled(ServerCommandSource source, boolean enabled) {
        service.setEnabled(enabled);
        source.sendFeedback(() -> Text.literal("Height cap is now " + (enabled ? "ENABLED" : "DISABLED") + "."), true);
        return 1;
    }
}
