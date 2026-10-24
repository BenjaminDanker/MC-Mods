package com.silver.atlantis.find;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * Runs a flat-area search over multiple ticks so it can't trip the server watchdog.
 */
public final class FlatAreaSearchService {

    private FlatAreaSearchTask activeTask;
    private net.minecraft.core.BlockPos lastResultCenter;

    public net.minecraft.core.BlockPos getLastResultCenterOrNull() {
        return lastResultCenter;
    }

    public boolean isRunning() {
        return activeTask != null;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    public boolean start(CommandSourceStack source, FlatAreaSearchConfig config) {
        if (activeTask != null) {
            return false;
        }
        activeTask = new FlatAreaSearchTask(source, config, lastResultCenter);
        return true;
    }

    public void cancel() {
        activeTask = null;
    }

    private void onEndTick(MinecraftServer server) {
        if (activeTask == null) {
            return;
        }

        boolean done;
        boolean failed = false;
        try {
            done = activeTask.tick(server);
        } catch (Exception e) {
            // Fail closed: a crashed search has no result (its partial best is not a real answer).
            com.silver.atlantis.AtlantisMod.LOGGER.error("[findflat] Flat-area search failed; treating it as no result", e);
            done = true;
            failed = true;
        }

        if (done) {
            lastResultCenter = failed ? null : activeTask.getResultCenterOrNull();
            activeTask = null;
        }
    }
}
