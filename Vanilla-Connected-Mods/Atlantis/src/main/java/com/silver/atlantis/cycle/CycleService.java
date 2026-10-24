package com.silver.atlantis.cycle;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.ConstructConfig;
import com.silver.atlantis.construct.ConstructService;
import com.silver.atlantis.construct.PetEscort;
import com.silver.atlantis.construct.PlayerEjectTarget;
import com.silver.atlantis.find.FlatAreaSearchConfig;
import com.silver.atlantis.find.FlatAreaSearchService;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.bounds.ActiveConstructBoundsResolver;
import com.silver.atlantis.spawn.command.ProximitySpawnCommandManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.nio.file.Path;
import java.util.Set;

public final class CycleService {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long MAX_ELIGIBLE_TICK_GAP_MILLIS = 5_000L;

    private final FlatAreaSearchService searchService;
    private final ConstructService constructService;
    private final ProximitySpawnCommandManager spawnCommandManager;
    private static final int STRUCTUREMOB_MAX_ATTEMPTS = 12;
    private static final long STRUCTUREMOB_RETRY_MS = 5_000L;
    private int structureMobAttempts;
    private long nextStructureMobAttemptMs;

    private final Path stateFile;

    private CycleState state;
    private long lastUndoCountdownCheckMillis = -1L;
    private long lastUndoCountdownCheckNanos = -1L;

    public CycleService(FlatAreaSearchService searchService, ConstructService constructService, ProximitySpawnCommandManager spawnCommandManager) {
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

    /**
     * Returns whether the cycle needs the server thread to keep advancing.
     * Waiting periods remain idle so vanilla pause-when-empty behavior can
     * still take effect, but a due schedule wakes the server back up.
     */
    private static final int MAX_UNDO_ATTEMPTS_PER_BOOT = 3;
    private int undoAttemptsThisBoot;
    private long lastUndoListErrorLogMillis;

    public boolean requiresServerTicking() {
        if (searchService.isRunning() || constructService.isRunning() || spawnCommandManager.isStructureMobRunning()) {
            return true;
        }

        CycleState current = state;
        if (current == null || !current.enabled()) {
            return false;
        }

        CycleState.Stage stage = parseStage(current.stage());
        if (stage == CycleState.Stage.UNDO_FAILED) {
            return false;
        }
        if (stage == CycleState.Stage.IDLE || stage == CycleState.Stage.WAIT_BEFORE_UNDO) {
            return current.nextRunAtEpochMillis() <= 0L
                || System.currentTimeMillis() >= current.nextRunAtEpochMillis();
        }

        return true;
    }

    public void sendStatus(CommandSourceStack source) {
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

        source.sendSuccess(() -> Component.literal(
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
    public void stepOnceNow(CommandSourceStack source) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        if (!state.enabled()) {
            source.sendSuccess(() -> Component.literal("Atlantis cycle is disabled. Use /atlantis cycle to enable it."), false);
            return;
        }

        MinecraftServer server = source.getServer();

        CycleState.Stage before = parseStage(state.stage());

        // Safety: never "skip" a running undo; leaving the world half-restored (and protections in limbo)
        // is worse than being forced to wait.
        if (before == CycleState.Stage.WAIT_UNDO && constructService.isUndoRunning()) {
            source.sendSuccess(() -> Component.literal("Cycle step: undo is currently running; refusing to force-skip it."), false);
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

            // If we're at the wait before the undo, step should advance into undo immediately.
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
            source.sendSuccess(() -> Component.literal("Cycle step: no state change."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Cycle step: " + before.name() + " -> " + after.name()), false);
        }
    }

    public void toggle(CommandSourceStack source) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        if (searchService.isRunning() || constructService.isRunning() || spawnCommandManager.isStructureMobRunning()) {
            source.sendSuccess(() -> Component.literal("Cycle toggle refused: a cycle job is running. Wait for it to finish."), false);
            return;
        }

        boolean newEnabled = !state.enabled();
        long now = System.currentTimeMillis();
        CycleState.Stage stage = parseStage(state.stage());
        boolean fresh = stage == CycleState.Stage.IDLE;
        // Disabling pauses the cycle where it is; enabling resumes it there. Resetting it to IDLE would forget a
        // build that is still waiting for its undo and start a second one next to it.
        CycleState newState = new CycleState(
            CycleState.CURRENT_VERSION,
            newEnabled,
            newEnabled && fresh ? now : state.nextRunAtEpochMillis(),
            state.stage(),
            state.lastCenterX(),
            state.lastCenterY(),
            state.lastCenterZ(),
            state.lastConstructRunId()
        );
        state = newState;
        persistState();

        if (newEnabled) {
            String where = fresh ? "Will run now; undo will happen " + (CycleConfig.UNDO_DELAY_MILLIS / 3_600_000L) + "h after the mobs are seeded." : "Resuming at " + stage.name() + ".";
            source.sendSuccess(() -> Component.literal("Atlantis cycle enabled. " + where), false);
        } else {
            source.sendSuccess(() -> Component.literal("Atlantis cycle paused at " + stage.name() + "; /atlantis cycle resumes it there."), false);
        }
    }

    public void setStage(CommandSourceStack source, String stageNameRaw) {
        if (state == null) {
            state = CycleJsonIO.readOrCreateState(stateFile);
        }

        if (!state.enabled()) {
            source.sendSuccess(() -> Component.literal("Atlantis cycle is disabled. Use /atlantis cycle to enable it."), false);
            return;
        }

        if (searchService.isRunning() || constructService.isRunning() || spawnCommandManager.isStructureMobRunning()) {
            source.sendSuccess(() -> Component.literal("Cycle set: refused because a cycle job is currently running. Use /atlantis cycle step (which can cancel construct) or wait for it to finish."), false);
            return;
        }

        String raw = stageNameRaw != null ? stageNameRaw.trim() : "";
        if (raw.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Cycle set: missing stage."), false);
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
            source.sendSuccess(() -> Component.literal("Cycle set: unknown stage '" + raw + "'. Use a Stage enum value (e.g. RUN_STRUCTUREMOB, WAIT_BEFORE_UNDO, START_UNDO)."), false);
            return;
        }

        long now = System.currentTimeMillis();
        long nextAt;
        if (target == CycleState.Stage.IDLE) {
            nextAt = now;
        } else if (target == CycleState.Stage.WAIT_BEFORE_UNDO) {
            // Default to 'now' when explicitly set; user can use the normal delay by letting RUN_STRUCTUREMOB set it.
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

        source.sendSuccess(() -> Component.literal("Cycle set: " + before.name() + " -> " + target.name()), false);
    }

    public void runStageNow(CommandSourceStack source, String stageNameRaw) {
        setStage(source, stageNameRaw);
        // If setStage refused (e.g. job running), it will have messaged the user.
        // Only step if we're still enabled and not running jobs.
        if (state == null || !state.enabled() || searchService.isRunning() || constructService.isRunning() || spawnCommandManager.isStructureMobRunning()) {
            return;
        }
        stepOnceNow(source);
    }

    private void onEndTick(MinecraftServer server) {
        tickInternal(server, server.createCommandSourceStack(), false);
    }

    /** Returns true if the state machine advanced (state persisted). */
    private boolean tickInternal(MinecraftServer server, CommandSourceStack actor, boolean ignoreSchedule) {
        if (state == null) {
            return false;
        }
        if (!state.enabled()) {
            return false;
        }

        long now = System.currentTimeMillis();

        CycleState.Stage stage = parseStage(state.stage());
        if (stage == CycleState.Stage.IDLE) {
            resetUndoCountdownCheck();
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
            resetUndoCountdownCheck();
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
            resetUndoCountdownCheck();
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

            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return false;
            }

            ConstructConfig defaults = ConstructConfig.defaults();
            ConstructConfig runCfg = defaults.withYOffsetBlocks(CycleConfig.CONSTRUCT_Y_OFFSET_BLOCKS);

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
            resetUndoCountdownCheck();
            if (constructService.isRunning()) {
                return false;
            }

            // If the server restarted mid-construct, resume it.
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                boolean resumed = constructService.resumeLatest(actor, ConstructConfig.defaults(), overworld);
                if (resumed) {
                    return false;
                }
            }

            if (constructService.abandonedRunId() != null) {
                // The build kept failing: undo what it placed rather than seeding mobs into a half-built Atlantis.
                constructService.clearAbandonedRun();
                state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.START_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
                persistState();
                return true;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.RUN_STRUCTUREMOB.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.RUN_STRUCTUREMOB) {
            resetUndoCountdownCheck();
            enforceStructureMobKeepout(server);
            try {
                if (!spawnCommandManager.isStructureMobRunning()) {
                    // The construct's run state is persisted asynchronously, so the first attempt can run
                    // before the bounds are readable. Retry instead of silently leaving the build mob-less.
                    long nowMs = System.currentTimeMillis();
                    if (nowMs < nextStructureMobAttemptMs) {
                        return false;
                    }
                    int started = spawnCommandManager.runStructureMob(actor, false);
                    if (started <= 0 && !spawnCommandManager.isStructureMobRunning()) {
                        structureMobAttempts++;
                        if (structureMobAttempts < STRUCTUREMOB_MAX_ATTEMPTS) {
                            nextStructureMobAttemptMs = nowMs + STRUCTUREMOB_RETRY_MS;
                            AtlantisMod.LOGGER.warn("[cycle] structuremob could not start (attempt {}/{}); retrying in {}s",
                                structureMobAttempts, STRUCTUREMOB_MAX_ATTEMPTS, STRUCTUREMOB_RETRY_MS / 1000);
                            return false;
                        }
                        AtlantisMod.LOGGER.error("[cycle] ALERT structuremob never started after {} attempts; this build will have NO mobs. "
                            + "Fix and run /atlantis mobs seed.", STRUCTUREMOB_MAX_ATTEMPTS);
                    }
                }
            } catch (Exception e) {
                // A crash is retried like a failed start, so one bad moment doesn't leave the build without mobs.
                structureMobAttempts++;
                if (structureMobAttempts < STRUCTUREMOB_MAX_ATTEMPTS) {
                    nextStructureMobAttemptMs = System.currentTimeMillis() + STRUCTUREMOB_RETRY_MS;
                    AtlantisMod.LOGGER.error("[cycle] structuremob failed (attempt {}/{}); retrying in {}s",
                        structureMobAttempts, STRUCTUREMOB_MAX_ATTEMPTS, STRUCTUREMOB_RETRY_MS / 1000, e);
                    return false;
                }
                AtlantisMod.LOGGER.error("[cycle] ALERT structuremob failed {} times; this build will have NO mobs. "
                    + "Fix and run /atlantis mobs seed.", STRUCTUREMOB_MAX_ATTEMPTS, e);
            }
            structureMobAttempts = 0;
            nextStructureMobAttemptMs = 0L;

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_STRUCTUREMOB.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_STRUCTUREMOB) {
            resetUndoCountdownCheck();
            enforceStructureMobKeepout(server);
            if (spawnCommandManager.isStructureMobRunning()) {
                return false;
            }

            // Nothing is building any more, so no spawn pause may remain. Release leaked tokens loudly.
            var leaked = com.silver.atlantis.spawn.service.ProximityMobManager.getInstance().getActivePauseTokens();
            if (!leaked.isEmpty()) {
                AtlantisMod.LOGGER.warn("[cycle] ALERT releasing {} leaked spawn pause token(s): {}", leaked.size(), leaked);
                com.silver.atlantis.spawn.service.ProximityMobManager.getInstance().clearAllExternalPauses();
            }
            // Wait CycleConfig.UNDO_DELAY_MILLIS after structuremob before undo.
            long undoAt = now + CycleConfig.UNDO_DELAY_MILLIS;
            state = new CycleState(CycleState.CURRENT_VERSION, true, undoAt, CycleState.Stage.WAIT_BEFORE_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.WAIT_BEFORE_UNDO) {
            maybeBroadcastUndoCountdown(server, now);
            if (!ignoreSchedule && state.nextRunAtEpochMillis() > 0 && now < state.nextRunAtEpochMillis()) {
                return false;
            }

            state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.START_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
            persistState();
            return true;
        }

        if (stage == CycleState.Stage.START_UNDO) {
            resetUndoCountdownCheck();
            if (constructService.isRunning()) {
                state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.WAIT_UNDO.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
                persistState();
                return true;
            }

            Boolean history = undoHistoryOrNull();
            if (history == null) {
                return false;
            }
            if (!history) {
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

        if (stage == CycleState.Stage.UNDO_FAILED) {
            return false;
        }

        if (stage == CycleState.Stage.WAIT_UNDO) {
            resetUndoCountdownCheck();
            if (constructService.isRunning()) {
                return false;
            }

            // If server restarted mid-undo, restart undo of latest run; an undo that keeps failing stops the cycle.
            Boolean history = undoHistoryOrNull();
            if (history == null) {
                return false;
            }
            if (history) {
                if (undoAttemptsThisBoot >= MAX_UNDO_ATTEMPTS_PER_BOOT) {
                    AtlantisMod.LOGGER.error("[cycle] ALERT: the Atlantis undo failed {} times; the cycle is stopped at UNDO_FAILED. "
                        + "Check the [undo:...] lines above, then use /atlantis cycle set to continue.", undoAttemptsThisBoot);
                    for (var admin : server.getPlayerList().getPlayers()) {
                        if (com.silver.authorization.fabric.AuthorizationChecks.has(admin, com.silver.authorization.PermissionNodes.ATLANTIS_MANAGE)) {
                            admin.sendSystemMessage(Component.literal(
                                "[Atlantis] The undo failed repeatedly; the cycle is paused at UNDO_FAILED (see the server log)."));
                        }
                    }
                    state = new CycleState(CycleState.CURRENT_VERSION, true, state.nextRunAtEpochMillis(), CycleState.Stage.UNDO_FAILED.name(), state.lastCenterX(), state.lastCenterY(), state.lastCenterZ(), state.lastConstructRunId());
                    persistState();
                    return true;
                }
                undoAttemptsThisBoot++;
                boolean restarted = constructService.startUndo(actor, ConstructConfig.defaults(), server, null);
                if (restarted) {
                    return false;
                }
            }
            undoAttemptsThisBoot = 0;

            // Loop immediately back to findflat.
            state = new CycleState(CycleState.CURRENT_VERSION, true, now, CycleState.Stage.IDLE.name(), null, null, null, state.lastConstructRunId());
            persistState();
            return true;
        }

        return false;
    }

    private void enforceStructureMobKeepout(MinecraftServer server) {
        if (server == null || state == null) {
            return;
        }

        CycleState.Stage stage = parseStage(state.stage());
        if (stage != CycleState.Stage.RUN_STRUCTUREMOB && stage != CycleState.Stage.WAIT_STRUCTUREMOB) {
            return;
        }

        ActiveConstructBounds bounds = ActiveConstructBoundsResolver.tryResolveLatest();
        if (bounds == null) {
            return;
        }

        ServerLevel world = null;
        String dimensionId = bounds.dimensionId();
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().identifier().toString().equals(dimensionId)) {
                world = candidate;
                break;
            }
        }
        if (world == null) {
            return;
        }

        ConstructConfig config = ConstructConfig.defaults();
        int configuredMargin = Math.max(0, config.playerEjectMarginBlocks());
        int viewDistanceChunks = server.getPlayerList().getViewDistance();
        int simulationDistanceChunks = server.getPlayerList().getSimulationDistance();
        int chunkLoadRadiusBlocks = Math.max(viewDistanceChunks, simulationDistanceChunks) * 16;
        int margin = Math.max(configuredMargin, chunkLoadRadiusBlocks + 32);
        int offset = Math.max(64, config.playerEjectTeleportOffsetBlocks() + chunkLoadRadiusBlocks);

        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        AABB box = new AABB(
            minX - margin,
            minY - margin,
            minZ - margin,
            maxX + margin + 1,
            maxY + margin + 1,
            maxZ + margin + 1
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != world) {
                continue;
            }

            if (!box.contains(player.getX(), player.getY(), player.getZ())) {
                continue;
            }

            double px = player.getX();
            double pz = player.getZ();
            double dxLeft = Math.abs(px - minX);
            double dxRight = Math.abs(px - maxX);
            double dzFront = Math.abs(pz - minZ);
            double dzBack = Math.abs(pz - maxZ);

            double best = dxLeft;
            int tx = minX - margin - offset;
            int tz = (int) Math.floor(pz);

            if (dxRight < best) {
                best = dxRight;
                tx = maxX + margin + offset;
                tz = (int) Math.floor(pz);
            }
            if (dzFront < best) {
                best = dzFront;
                tx = (int) Math.floor(px);
                tz = minZ - margin - offset;
            }
            if (dzBack < best) {
                tx = (int) Math.floor(px);
                tz = maxZ + margin + offset;
            }

            BlockPos target = PlayerEjectTarget.aboveGround(world, tx, tz);
            player.teleportTo(
                world,
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                Set.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z),
                player.getYRot(),
                player.getXRot(),
                true
            );
            PetEscort.follow(player, world, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        }
    }

    /** @throws java.io.IOException when the undo folder can't be read: that is not the same as "nothing to undo". */
    private boolean hasUndoHistory() throws java.io.IOException {
        com.silver.atlantis.construct.undo.UndoPaths.pruneUnusableRunDirectories();
        String runId = com.silver.atlantis.construct.undo.UndoPaths.findLatestUsableRunId();
        return runId != null && !runId.isBlank();
    }

    /** Undo history, or null when the undo folder can't be read right now (logged at most once a minute). */
    private Boolean undoHistoryOrNull() {
        try {
            return hasUndoHistory();
        } catch (java.io.IOException e) {
            long now = System.currentTimeMillis();
            if (now - lastUndoListErrorLogMillis > 60_000L) {
                lastUndoListErrorLogMillis = now;
                AtlantisMod.LOGGER.error("[cycle] Cannot read the undo folder; the cycle waits instead of skipping the undo", e);
            }
            return null;
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

    private void resetUndoCountdownCheck() {
        lastUndoCountdownCheckMillis = -1L;
        lastUndoCountdownCheckNanos = -1L;
    }

    private void maybeBroadcastUndoCountdown(MinecraftServer server, long nowMillis) {
        long undoAt = state.nextRunAtEpochMillis();
        if (undoAt <= 0L) {
            lastUndoCountdownCheckMillis = nowMillis;
            lastUndoCountdownCheckNanos = System.nanoTime();
            return;
        }

        long nowNanos = System.nanoTime();
        long prevMillis = lastUndoCountdownCheckMillis;
        long prevNanos = lastUndoCountdownCheckNanos;

        lastUndoCountdownCheckMillis = nowMillis;
        lastUndoCountdownCheckNanos = nowNanos;

        if (prevMillis < 0L || prevNanos < 0L) {
            return;
        }

        long elapsedWallMillis = nowMillis - prevMillis;
        long elapsedNanos = nowNanos - prevNanos;
        if (elapsedWallMillis < 0L || elapsedNanos < 0L) {
            return;
        }

        if (elapsedWallMillis > MAX_ELIGIBLE_TICK_GAP_MILLIS) {
            return;
        }

        long elapsedFromNanoMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (Math.abs(elapsedFromNanoMillis - elapsedWallMillis) > 250L) {
            return;
        }

        long previousRemaining = undoAt - prevMillis;
        long currentRemaining = undoAt - nowMillis;
        if (previousRemaining <= 0L) {
            return;
        }

        if (currentRemaining <= 0L) {
            broadcastToAllPlayers(server, "Atlantis is moving");
            return;
        }

        long boundaryDays = currentRemaining / DAY_MILLIS;
        if (boundaryDays <= 0L) {
            return;
        }

        long boundaryRemaining = boundaryDays * DAY_MILLIS;
        if (previousRemaining > boundaryRemaining && currentRemaining <= boundaryRemaining) {
            if (boundaryDays == 1L) {
                broadcastToAllPlayers(server, "1 day until Atlantis moves");
            } else {
                broadcastToAllPlayers(server, boundaryDays + " days until Atlantis moves");
            }
        }
    }

    private void broadcastToAllPlayers(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        Component text = Component.literal(message);
        server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(text, false));
    }
}
