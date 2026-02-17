package com.silver.atlantis.leviathan;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.AtlantisMod;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class LeviathanCommandManager {

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        return CommandManager.literal("leviathan")
            .then(CommandManager.literal("dump")
                .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, true))
                .then(CommandManager.literal("all")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, true)))
                .then(CommandManager.literal("virtual")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), true, false)))
                .then(CommandManager.literal("loaded")
                    .executes(ctx -> LeviathanManager.dump(ctx.getSource(), false, true))))
            .then(CommandManager.literal("config")
                .then(CommandManager.literal("show")
                    .executes(ctx -> showConfig(ctx.getSource())))
                .then(CommandManager.literal("reload")
                    .executes(ctx -> reloadConfig(ctx.getSource()))));
    }

    private int showConfig(ServerCommandSource source) {
        LeviathansConfig config = Leviathans.getConfig();
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config show by={} entityTypeId={} scale={} min={}",
            source.getName(),
            config.entityTypeId,
            config.entityScale,
            config.minimumLeviathans);
        source.sendFeedback(() -> Text.literal("Leviathan config loaded from " + LeviathansConfig.configPath()), false);
        source.sendFeedback(() -> Text.literal("entityTypeId=" + config.entityTypeId +
            " entityTypeIds=" + config.entityTypeIds +
            " entityScale=" + config.entityScale +
            " minimumLeviathans=" + config.minimumLeviathans), false);
        source.sendFeedback(() -> Text.literal("engageRadiusBlocks=" + config.engageRadiusBlocks +
            " engageVerticalRadiusBlocks=" + config.engageVerticalRadiusBlocks +
            " disengageRadiusBlocks=" + config.disengageRadiusBlocks +
            " disengageVerticalRadiusBlocks=" + config.disengageVerticalRadiusBlocks), false);
        source.sendFeedback(() -> Text.literal("depthScaleTopY=" + config.depthScaleTopY +
            " depthScaleBottomY=" + config.depthScaleBottomY +
            " depthScaleAtTop=" + config.depthScaleAtTop +
            " depthScaleAtBottom=" + config.depthScaleAtBottom +
            " depthDamageAtTop=" + config.depthDamageAtTop +
            " depthDamageAtBottom=" + config.depthDamageAtBottom +
            " depthHealthAtTop=" + config.depthHealthAtTop +
            " depthHealthAtBottom=" + config.depthHealthAtBottom), false);
        return 1;
    }

    private int reloadConfig(ServerCommandSource source) {
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config reload by={}", source.getName());
        Leviathans.ReloadResult result = Leviathans.reloadConfig();
        if (result.applied()) {
            LeviathansConfig config = result.config();
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] command config reload applied by={} path={}", source.getName(), result.path());
            source.sendFeedback(() -> Text.literal("Leviathan config reload applied: " + result.path()), false);
            source.sendFeedback(() -> Text.literal("entityTypeId=" + config.entityTypeId +
                " entityTypeIds=" + config.entityTypeIds +
                " entityScale=" + config.entityScale +
                " minimumLeviathans=" + config.minimumLeviathans), false);
            source.sendFeedback(() -> Text.literal("engageRadiusBlocks=" + config.engageRadiusBlocks +
                " engageVerticalRadiusBlocks=" + config.engageVerticalRadiusBlocks +
                " disengageRadiusBlocks=" + config.disengageRadiusBlocks +
                " disengageVerticalRadiusBlocks=" + config.disengageVerticalRadiusBlocks), false);
            source.sendFeedback(() -> Text.literal("depthScaleTopY=" + config.depthScaleTopY +
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
            source.getName(),
            result.path(),
            result.errors());
        source.sendError(Text.literal("Leviathan config reload rejected; active config unchanged. path=" + result.path()));
        for (String error : result.errors()) {
            source.sendError(Text.literal(" - " + error));
        }
        return 0;
    }
}
