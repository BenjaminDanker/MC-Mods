package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Runs schematic slice construction over many ticks.
 */
public final class ConstructService {

    private final Executor ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atlantis-Construct-IO");
        t.setDaemon(true);
        return t;
    });

    private ConstructJob activeJob;
    private boolean paused;

    public boolean isRunning() {
        return activeJob != null;
    }

    public boolean isPaused() {
        return paused && activeJob != null;
    }

    public boolean isConstructRunning() {
        return activeJob instanceof ConstructTask;
    }

    public boolean isUndoRunning() {
        return activeJob instanceof ConstructUndoTask;
    }

    public String getActiveJobDescription() {
        if (activeJob instanceof ConstructTask task) {
            return "construct (runId=" + task.getRunId() + ")";
        }
        if (activeJob instanceof ConstructUndoTask task) {
            return "undo (runId=" + task.getRunId() + ")";
        }
        if (activeJob != null) {
            return activeJob.getClass().getSimpleName();
        }
        return "none";
    }

    public boolean pause() {
        if (activeJob == null || paused) {
            return false;
        }
        paused = true;
        return true;
    }

    public boolean resume() {
        if (activeJob == null || !paused) {
            return false;
        }
        paused = false;
        return true;
    }

    public String getActiveRunIdOrNull() {
        if (activeJob instanceof ConstructTask task) {
            return task.getRunId();
        }
        return null;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    private boolean ensureWorldEditAvailable(ServerCommandSource source) {
        // Construct depends on WorldEdit (and its embedded lin-bus classes). If these are missing,
        // starting the construct job can crash the entire server tick loop.
        return ensureClassPresent(source, "com.sk89q.worldedit.WorldEdit", "WorldEdit")
            && ensureClassPresent(source, "org.enginehub.linbus.stream.LinStreamable", "lin-bus (LinStreamable)");
    }

    private boolean ensureClassPresent(ServerCommandSource source, String className, String displayName) {
        try {
            Class.forName(className, false, ConstructService.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            AtlantisMod.LOGGER.error("Missing required construct dependency: {} ({})", displayName, className, t);
            source.sendFeedback(
                () -> Text.literal("Cannot start /construct: missing required dependency: " + displayName + ". Install the Fabric WorldEdit mod (EngineHub) and restart the server."),
                false
            );
            return false;
        }
    }

    public boolean start(ServerCommandSource source, ConstructConfig config, ServerWorld world, BlockPos center) {
        if (activeJob != null) {
            return false;
        }

        if (!ensureWorldEditAvailable(source)) {
            return false;
        }

        paused = false;
        try {
            activeJob = new ConstructTask(source, config, world, center, ioExecutor);
        } catch (NoClassDefFoundError err) {
            AtlantisMod.LOGGER.error("Failed to start construct due to missing classes.", err);
            source.sendFeedback(
                () -> Text.literal("Cannot start /construct: missing runtime class: " + err.getMessage() + ". Ensure the correct WorldEdit build (and dependencies) is installed."),
                false
            );
            activeJob = null;
            paused = false;
            return false;
        }
        return true;
    }

    public boolean resumeLatest(ServerCommandSource source, ConstructConfig config, ServerWorld world) {
        if (activeJob != null) {
            return false;
        }

        if (!ensureWorldEditAvailable(source)) {
            return false;
        }

        var resumeState = ConstructTask.tryLoadLatestResumableState(world);
        if (resumeState == null) {
            return false;
        }

        paused = false;
        try {
            activeJob = new ConstructTask(source, config, world, resumeState, ioExecutor);
        } catch (NoClassDefFoundError err) {
            AtlantisMod.LOGGER.error("Failed to resume construct due to missing classes.", err);
            source.sendFeedback(
                () -> Text.literal("Cannot resume /construct: missing runtime class: " + err.getMessage() + ". Ensure the correct WorldEdit build (and dependencies) is installed."),
                false
            );
            activeJob = null;
            paused = false;
            return false;
        }
        return true;
    }

    public boolean startUndo(ServerCommandSource source, ConstructConfig config, MinecraftServer server, String runIdOrNull) {
        if (activeJob != null) {
            return false;
        }

        if (!ensureWorldEditAvailable(source)) {
            return false;
        }

        paused = false;
        try {
            activeJob = new ConstructUndoTask(source, config, server, runIdOrNull, ioExecutor);
        } catch (NoClassDefFoundError err) {
            AtlantisMod.LOGGER.error("Failed to start construct undo due to missing classes.", err);
            source.sendFeedback(
                () -> Text.literal("Cannot start /construct undo: missing runtime class: " + err.getMessage() + ". Ensure the correct WorldEdit build (and dependencies) is installed."),
                false
            );
            activeJob = null;
            paused = false;
            return false;
        }
        return true;
    }

    public void cancel() {
        cancel(null, null);
    }

    public void cancel(MinecraftServer server, String reason) {
        if (activeJob == null) {
            return;
        }

        try {
            activeJob.onCancel(server, reason);
        } catch (Throwable t) {
            AtlantisMod.LOGGER.warn("Construct job cancel cleanup failed; cancelling anyway.", t);
        } finally {
            activeJob = null;
            paused = false;
        }
    }

    private void onEndTick(MinecraftServer server) {
        if (activeJob == null) {
            return;
        }

        if (paused) {
            return;
        }

        boolean done;
        try {
            done = activeJob.tick(server);
        } catch (Throwable t) {
            try {
                activeJob.onError(server, t);
            } catch (Throwable ignored) {
                // Never let crash reporting crash the tick loop.
            }

            AtlantisMod.LOGGER.error("Construct job crashed; cancelling.", t);
            done = true;
        }

        if (done) {
            activeJob = null;
            paused = false;
        }
    }
}
