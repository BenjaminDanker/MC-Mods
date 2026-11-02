package com.silver.atlantis.heightcap;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class HeightCapCommandManager {

    private final HeightCapService service;

    public HeightCapCommandManager(HeightCapService service) {
        this.service = service;
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
        return Commands.literal("heightcap")
            .executes(ctx -> status(ctx.getSource()))
            .then(Commands.literal("status")
                .executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("enable")
                .executes(ctx -> setEnabled(ctx.getSource(), true)))
            .then(Commands.literal("disable")
                .executes(ctx -> setEnabled(ctx.getSource(), false)));
    }

    private int status(CommandSourceStack source) {
        boolean enabled = service.isEnabled();
        source.sendSuccess(() -> Component.literal("Height cap is " + (enabled ? "ENABLED" : "DISABLED") + "."), false);
        source.sendSuccess(() -> Component.literal("Rule: players at/above Y=318 are teleported to Y=317."), false);
        return 1;
    }

    private int setEnabled(CommandSourceStack source, boolean enabled) {
        service.setEnabled(enabled);
        source.sendSuccess(() -> Component.literal("Height cap is now " + (enabled ? "ENABLED" : "DISABLED") + "."), true);
        return 1;
    }
}
