package com.silver.atlantis.find;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Runs a flat-area search over multiple ticks so it can't trip the server watchdog.
 */
public final class FlatAreaSearchService {

    private FlatAreaSearchTask activeTask;
    private net.minecraft.util.math.BlockPos lastResultCenter;

    public net.minecraft.util.math.BlockPos getLastResultCenterOrNull() {
        return lastResultCenter;
    }

    public boolean isRunning() {
        return activeTask != null;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    public boolean start(ServerCommandSource source, FlatAreaSearchConfig config) {
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
