package com.silver.atlantis.leviathan;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.AtlantisMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class LeviathanCommandManager {

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
        return Commands.literal("leviathan")
            .then(Commands.literal("dump")
                .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, true))
                .then(Commands.literal("all")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, true)))
                .then(Commands.literal("virtual")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, false)))
                .then(Commands.literal("loaded")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), false, true))))
            .then(Commands.literal("config")
                .then(Commands.literal("show")
                    .executes(ctx -> showConfig(ctx.getSource())))
                .then(Commands.literal("reload")
                    .executes(ctx -> reloadConfig(ctx.getSource()))));
    }

    private int showConfig(CommandSourceStack source) {
        LeviathansConfig config = Leviathans.getConfig();
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config show by={} entityTypeId={} scale={} min={}",
            source.getTextName(),
            config.entityTypeId,
            config.entityScale,
            config.minimumLeviathans);
        source.sendSuccess(() -> Component.literal("Leviathan config loaded from " + LeviathansConfig.configPath()), false);
        source.sendSuccess(() -> Component.literal("entityTypeId=" + config.entityTypeId +
            " entityTypeIds=" + config.entityTypeIds +
            " entityScale=" + config.entityScale +
            " minimumLeviathans=" + config.minimumLeviathans), false);
        source.sendSuccess(() -> Component.literal("engageRadiusBlocks=" + config.engageRadiusBlocks +
            " engageVerticalRadiusBlocks=" + config.engageVerticalRadiusBlocks +
            " disengageRadiusBlocks=" + config.disengageRadiusBlocks +
            " disengageVerticalRadiusBlocks=" + config.disengageVerticalRadiusBlocks), false);
        source.sendSuccess(() -> Component.literal("depthScaleTopY=" + config.depthScaleTopY +
            " depthScaleBottomY=" + config.depthScaleBottomY +
            " depthScaleAtTop=" + config.depthScaleAtTop +
            " depthScaleAtBottom=" + config.depthScaleAtBottom +
            " depthDamageAtTop=" + config.depthDamageAtTop +
            " depthDamageAtBottom=" + config.depthDamageAtBottom +
            " depthHealthAtTop=" + config.depthHealthAtTop +
            " depthHealthAtBottom=" + config.depthHealthAtBottom), false);
        return 1;
    }

    private int reloadConfig(CommandSourceStack source) {
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config reload by={}", source.getTextName());
        Leviathans.ReloadResult result = Leviathans.reloadConfig();
        if (result.applied()) {
            LeviathansConfig config = result.config();
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config reload applied by={} path={}", source.getTextName(), result.path());
            source.sendSuccess(() -> Component.literal("Leviathan config reload applied: " + result.path()), false);
            source.sendSuccess(() -> Component.literal("entityTypeId=" + config.entityTypeId +
                " entityTypeIds=" + config.entityTypeIds +
                " entityScale=" + config.entityScale +
                " minimumLeviathans=" + config.minimumLeviathans), false);
            source.sendSuccess(() -> Component.literal("engageRadiusBlocks=" + config.engageRadiusBlocks +
                " engageVerticalRadiusBlocks=" + config.engageVerticalRadiusBlocks +
                " disengageRadiusBlocks=" + config.disengageRadiusBlocks +
                " disengageVerticalRadiusBlocks=" + config.disengageVerticalRadiusBlocks), false);
            source.sendSuccess(() -> Component.literal("depthScaleTopY=" + config.depthScaleTopY +
                " depthScaleBottomY=" + config.depthScaleBottomY +
                " depthScaleAtTop=" + config.depthScaleAtTop +
                " depthScaleAtBottom=" + config.depthScaleAtBottom +
                " depthDamageAtTop=" + config.depthDamageAtTop +
                " depthDamageAtBottom=" + config.depthDamageAtBottom +
                " depthHealthAtTop=" + config.depthHealthAtTop +
                " depthHealthAtBottom=" + config.depthHealthAtBottom), false);
            return 1;
        }

        AtlantisMod.LOGGER.warn("[Atlantis][leviathan] command config reload rejected by={} path={} errors={}",
            source.getTextName(),
            result.path(),
            result.errors());
        source.sendFailure(Component.literal("Leviathan config reload rejected; active config unchanged. path=" + result.path()));
        for (String error : result.errors()) {
            source.sendFailure(Component.literal(" - " + error));
        }
        return 0;
    }
}
