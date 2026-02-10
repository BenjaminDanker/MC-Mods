package com.silver.atlantis.cycle;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.ConstructConfig;
import com.silver.atlantis.construct.ConstructService;
import com.silver.atlantis.find.FlatAreaSearchConfig;
import com.silver.atlantis.find.FlatAreaSearchService;
import com.silver.atlantis.spawn.SpawnCommandManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.file.Path;

public final class CycleService {

    private final FlatAreaSearchService searchService;
    private final ConstructService constructService;
    private final SpawnCommandManager spawnCommandManager;

    private final Path stateFile;

    private CycleState state;

    public CycleService(FlatAreaSearchService searchService, ConstructService constructService, SpawnCommandManager spawnCommandManager) {
        this.searchService = searchService;
        this.constructService = constructService;
        this.spawnCommandManager = spawnCommandManager;
        this.stateFile = CyclePaths.stateFile();
    }

    public void register() {
        state = CycleJsonIO.readOrCreateState(stateFile);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);

        if (state.enabled()) {
            AtlantisMod.LOGGER.info("Atlantis cycle enabled. Next run at epochMillis={} stage={}", state.nextRunAtEpochMillis(), state.stage());
        }
    }

    public boolean isEnabled() {
        return state != null && state.enabled();
    }

    public void sendStatus(ServerCommandSource source) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        long now = System.currentTimeMillis();
        CycleState.Stage stage = parseStage(state.stage());

        long nextAt = state.nextRunAtEpochMillis();
        String nextText;
        if (!state.enabled()) {
            nextText = "disabled";
        } else if (nextAt <= 0) {
            nextText = "now";
        } else {
            long remaining = Math.max(0L, nextAt - now);
            nextText = String.format(java.util.Locale.ROOT, "%dms", remaining);
        }

        String center = (state.lastCenterX() != null && state.lastCenterY() != null && state.lastCenterZ() != null)
            ? String.format(java.util.Locale.ROOT, "%d,%d,%d", state.lastCenterX(), state.lastCenterY(), state.lastCenterZ())
            : "(none)";

        String runId = (state.lastConstructRunId() != null && !state.lastConstructRunId().isBlank())
            ? state.lastConstructRunId()
            : "(none)";

        source.sendFeedback(() -> Text.literal(
            "Cycle status: enabled=" + state.enabled()
                + " stage=" + stage.name()
                + " next=" + nextText
                + " lastCenter=" + center
                + " lastRunId=" + runId
        ), false);
    }

    /**
     * Forces the cycle to evaluate immediately once (admin helper).
     * This does NOT directly run /construct or /structuremob; it runs the cycle state machine.
     */
    public void stepOnceNow(ServerCommandSource source) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        if (!state.enabled()) {
            source.sendFeedback(() -> Text.literal("Atlantis cycle is disabled. Use /atlantis cycle to enable it."), false);
            return;
        }

        MinecraftServer server = source.getServer();

        CycleState.Stage before = parseStage(state.stage());

        // Safety: never "skip" a running undo; leaving the world half-restored (and protections in limbo)
        // is worse than being forced to wait.
        if (before == CycleState.Stage.WAIT_UNDO && constructService.isUndoRunning()) {
            source.sendFeedback(() -> Text.literal("Cycle step: undo is currently running; refusing to force-skip it."), false);
            return;
        }

        // We treat this command as "advance to the next checkpoint".
        // - If we're before WAIT_BEFORE_UNDO: fast-forward until we reach WAIT_BEFORE_UNDO.
        // - If we're at WAIT_BEFORE_UNDO: fast-forward to starting undo (WAIT_UNDO).
        CycleState.Stage goal = (before == CycleState.Stage.WAIT_BEFORE_UNDO)
            ? CycleState.Stage.WAIT_UNDO
            : CycleState.Stage.WAIT_BEFORE_UNDO;

        boolean changed = false;
        int iterations = 0;
        while (iterations++ < 12) {
            CycleState.Stage stage = parseStage(state.stage());
            if (stage == goal) {
                break;
            }

            // If we're waiting on findflat and it's still running, cancelling it is safe.
            // We restart the cycle (IDLE) rather than using a potentially stale lastResultCenter.
            if (stage == CycleState.Stage.WAIT_FIND_FLAT && searchService.isRunning()) {
                searchService.cancel();
                long now = System.currentTimeMillis();
                state = new CycleState(
                    CycleState.CURRENT_VERSION,
                    true,
                    now,
                    CycleState.Stage.IDLE.name(),
                    null,
                    null,
                    null,
                    state.lastConstructRunId()
                );
                persistState();
                changed = true;
                continue;
            }

            // If we're in the construct portion of the cycle and a construct is running, allow skipping it.
            if ((stage == CycleState.Stage.START_CONSTRUCT || stage == CycleState.Stage.WAIT_CONSTRUCT) && constructService.isConstructRunning()) {
                constructService.cancel(server, "cycle step: skipping remaining construct");
            }

            // If we're at the 48h wait, step should advance into undo immediately.
            if (stage == CycleState.Stage.WAIT_BEFORE_UNDO) {
                state = new CycleState(
                    CycleState.CURRENT_VERSION,
                    true,
                    0L,
                    CycleState.Stage.START_UNDO.name(),
                    state.lastCenterX(),
                    state.lastCenterY(),
                    state.lastCenterZ(),
                    state.lastConstructRunId()
                );
                persistState();
                changed = true;
                continue;
            }

            boolean progressed = tickInternal(server, source, true);
            if (!progressed) {
                break;
            }
            changed = true;
        }

        CycleState.Stage after = parseStage(state.stage());
        if (!changed || after == before) {
            source.sendFeedback(() -> Text.literal("Cycle step: no state change."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Cycle step: " + before.name() + " -> " + after.name()), false);
        }
    }

    public void toggle(ServerCommandSource source) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        boolean newEnabled = !state.enabled();
        long now = System.currentTimeMillis();
        CycleState newState = new CycleState(
            CycleState.CURRENT_VERSION,
            newEnabled,
            newEnabled ? now : 0L,
            CycleState.Stage.IDLE.name(),
            null,
            null,
            null,
            null
        );
        state = newState;
        persistState();

        if (newEnabled) {
            source.sendFeedback(() -> Text.literal("Atlantis cycle enabled. Will run now; undo will happen 48h after /structuremob."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Atlantis cycle disabled."), false);
        }
    }

    public void setStage(ServerCommandSource source, String stageNameRaw) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        if (!state.enabled()) {
            source.sendFeedback(() -> Text.literal("Atlantis cycle is disabled. Use /atlantis cycle to enable it."), false);
            return;
        }

        if (searchService.isRunning() || constructService.isRunning()) {
            source.sendFeedback(() -> Text.literal("Cycle set: refused because a cycle job is currently running. Use /atlantis cycle step (which can cancel construct) or wait for it to finish."), false);
            return;
        }

        String raw = stageNameRaw != null ? stageNameRaw.trim() : "";
        if (raw.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Cycle set: missing stage."), false);
            return;
        }

        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        // Friendly aliases.
        if (upper.equals("STRUCTUREMOB")) {
            upper = CycleState.Stage.RUN_STRUCTUREMOB.name();
        } else if (upper.equals("FIND") || upper.equals("FINDFLAT")) {
            upper = CycleState.Stage.IDLE.name();
        } else if (upper.equals("CONSTRUCT")) {
            upper = CycleState.Stage.START_CONSTRUCT.name();
        } else if (upper.equals("UNDO")) {
            upper = CycleState.Stage.START_UNDO.name();
        }

        CycleState.Stage target;
        try {
            target = CycleState.Stage.valueOf(upper);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Cycle set: unknown stage '" + raw + "'. Use a Stage enum value (e.g. RUN_STRUCTUREMOB, WAIT_BEFORE_UNDO, START_UNDO)."), false);
            return;
        }

        long now = System.currentTimeMillis();
        long nextAt;
        if (target == CycleState.Stage.IDLE) {
            nextAt = now;
        } else if (target == CycleState.Stage.WAIT_BEFORE_UNDO) {
            // Default to 'now' when explicitly set; user can use the normal 48h delay by letting RUN_STRUCTUREMOB set it.
            nextAt = 0L;
        } else {
            nextAt = state.nextRunAtEpochMillis();
        }

        CycleState.Stage before = parseStage(state.stage());
        state = new CycleState(
            CycleState.CURRENT_VERSION,
            true,
            nextAt,
            target.name(),
            state.lastCenterX(),
            state.lastCenterY(),
            state.lastCenterZ(),
            state.lastConstructRunId()
        );
        persistState();

        source.sendFeedback(() -> Text.literal("Cycle set: " + before.name() + " -> " + target.name()), false);
    }

    public void runStageNow(ServerCommandSource source, String stageNameRaw) {
        setStage(source, stageNameRaw);
        // If setStage refused (e.g. job running), it will have messaged the user.
        // Only step if we're still enabled and not running jobs.
        if (state == null || !state.enabled() || searchService.isRunning() || constructService.isRunning()) {
            return;
        }
        stepOnceNow(source);
    }

    private void onEndTick(MinecraftServer server) {
        tickInternal(server, server.getCommandSource(), false);
    }

    /** Returns true if the state machine advanced (state persisted). */
    private boolean tickInternal(MinecraftServer server, ServerCommandSource actor, boolean ignoreSchedule) {
        if (state == null) {
            return false;
        }
        if (!state.enabled()) {
            return false;
        }

        long now = System.currentTimeMillis();

        CycleState.Stage stage = parseStage(state.stage());
        if (stage == CycleState.Stage.IDLE) {
            if (!ignoreSchedule && state.nextRunAtEpochMillis() > 0 && now < state.nextRunAtEpochMillis()) {
                return false;
            }

            // Kick off findflat.
            boolean started = searchService.start(actor, FlatAreaSearchConfig.defaults());
            if (!started && !searchService.isRunning()) {
                // Could not start and no active task; try again next tick.
                return false;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_FIND_FLAT.name(), null, null, null, state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_FIND_FLAT) {
            if (searchService.isRunning()) {
                return false;
            }

            BlockPos center = searchService.getLastResultCenterOrNull();
            if (center == null) {
                // Retry findflat.
                state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
                persistState();
                return true;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.START_CONSTRUCT.name(), center.getX(), center.getY(), center.getZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.START_CONSTRUCT) {
            if (constructService.isRunning()) {
                state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_CONSTRUCT.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
                persistState();
                return true;
            }

            Integer cx = state.lastCenterX();
            Integer cy = state.lastCenterY();
            Integer cz = state.lastCenterZ();
            if (cx == null || cy == null || cz == null) {
                // No center persisted; restart cycle.
                state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
                persistState();
                return true;
            }

            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            if (overworld == null) {
                return false;
            }

            ConstructConfig defaults = ConstructConfig.defaults();
            ConstructConfig runCfg = new ConstructConfig(
                defaults.delayBetweenStagesTicks(),
                defaults.delayBetweenSlicesTicks(),
                defaults.maxChunksToLoadPerTick(),
                CycleConfig.CONSTRUCT_Y_OFFSET_BLOCKS,
                defaults.maxEntitiesToProcessPerTick(),
                defaults.playerEjectMarginBlocks(),
                defaults.pasteFlushEveryBlocks(),
                defaults.tickTimeBudgetNanos(),
                defaults.undoTickTimeBudgetNanos(),
                defaults.undoFlushEveryBlocks(),
                defaults.maxFluidNeighborUpdatesPerTick()
            );

            boolean started = constructService.start(actor, runCfg, overworld, new BlockPos(cx, cy, cz));
            if (!started) {
                // Might be resumable after restart.
                boolean resumed = constructService.resumeLatest(actor, runCfg, overworld);
                if (!resumed && !constructService.isRunning()) {
                    // Nothing to run; restart cycle.
                    state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
                    persistState();
                    return true;
                }
            }

            String activeRunId = constructService.getActiveRunIdOrNull();
            if (activeRunId == null) {
                // Should not happen, but keep state consistent.
                activeRunId = state.lastConstructRunId();
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_CONSTRUCT.name(), cx, cy, cz, activeRunId);
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_CONSTRUCT) {
            if (constructService.isRunning()) {
                return false;
            }

            // If the server restarted mid-construct, resume it.
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            if (overworld != null) {
                boolean resumed = constructService.resumeLatest(actor, ConstructConfig.defaults(), overworld);
                if (resumed) {
                    return false;
                }
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.RUN_STRUCTUREMOB.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.RUN_STRUCTUREMOB) {
            try {
                spawnCommandManager.runStructureMob(actor, false);
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("structuremob failed during cycle: {}", e.getMessage());
            }

            // Wait 48 hours after structuremob before undo.
            long undoAt = now + CycleConfig.UNDO_DELAY_MILLIS;
            state = new CycleState(CycleState.CURRENT_VERSION, true, undoAt, CycleState.Stage.WAIT_BEFORE_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_BEFORE_UNDO) {
            if (!ignoreSchedule && state.nextRunAtEpochMillis() > 0 && now < state.nextRunAtEpochMillis()) {
                return false;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.START_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.START_UNDO) {
            if (constructService.isRunning()) {
                state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
                persistState();
                return true;
            }

            if (!hasUndoHistory()) {
                // Nothing to undo; restart the loop.
                state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
                persistState();
                return true;
            }

            boolean started = constructService.startUndo(actor, ConstructConfig.defaults(), server, null);
            if (!started && !constructService.isRunning()) {
                // Nothing to undo yet; try again next tick.
                return false;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_UNDO) {
            if (constructService.isRunning()) {
                return false;
            }

            // If server restarted mid-undo, restart undo of latest run.
            if (hasUndoHistory()) {
                boolean restarted = constructService.startUndo(actor, ConstructConfig.defaults(), server, null);
                if (restarted) {
                    return false;
                }
            }

            // Loop immediately back to findflat.
            state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
            persistState();
            return true;
        }

        return false;
    }

    private boolean hasUndoHistory() {
        String runId = com.silver.atlantis.construct.undo.UndoPaths.findLatestRunIdOrNull();
        if (runId == null || runId.isBlank()) {
            return false;
        }
        try {
            java.nio.file.Path runDir = com.silver.atlantis.construct.undo.UndoPaths.runDir(runId);
            java.nio.file.Path meta = com.silver.atlantis.construct.undo.UndoPaths.metadataFile(runDir);
            return java.nio.file.Files.exists(meta);
        } catch (Exception ignored) {
            return false;
        }
    }

    private CycleState.Stage parseStage(String raw) {
        if (raw == null) {
            return CycleState.Stage.IDLE;
        }
        try {
            return CycleState.Stage.valueOf(raw);
        } catch (Exception ignored) {
            return CycleState.Stage.IDLE;
        }
    }

    private void persistState() {
        try {
            CycleJsonIO.write(stateFile, state);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to persist cycle state {}: {}", stateFile, e.getMessage());
        }
    }
}
