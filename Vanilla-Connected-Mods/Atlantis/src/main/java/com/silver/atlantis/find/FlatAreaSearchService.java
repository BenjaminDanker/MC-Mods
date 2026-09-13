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

        boolean done = false;
        try {
            done = activeTask.tick(server);
        } catch (Exception ignored) {
            // If something goes wrong, fail closed rather than freezing the server.
            done = true;
        }

        if (done) {
            lastResultCenter = activeTask.getResultCenterOrNull();
            activeTask = null;
        }
    }
}
