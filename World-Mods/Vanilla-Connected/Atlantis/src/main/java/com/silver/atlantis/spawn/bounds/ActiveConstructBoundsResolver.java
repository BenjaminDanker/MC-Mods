package com.silver.atlantis.spawn.bounds;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.state.ConstructRunState;
import com.silver.atlantis.construct.state.ConstructRunStateIO;
import com.silver.atlantis.construct.state.ConstructStatePaths;
import com.silver.atlantis.cycle.CycleJsonIO;
import com.silver.atlantis.cycle.CyclePaths;
import com.silver.atlantis.cycle.CycleState;
import com.silver.atlantis.construct.undo.UndoPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the active construct run's bounds using the persisted construct state.
 */
public final class ActiveConstructBoundsResolver {

    private ActiveConstructBoundsResolver() {
    }

    private static final long RECHECK_MILLIS = 1_000L;
    private static ActiveConstructBounds cached;
    private static long checkedAtMillis;
    private static long cycleFileStamp = Long.MIN_VALUE;
    private static long stateFileStamp = Long.MIN_VALUE;

    /**
     * The latest run's bounds. Called every tick while mobs are seeded, so the result is cached: the two files are
     * stat'ed at most once a second and parsed again only when one of them changed.
     */
    public static synchronized ActiveConstructBounds tryResolveLatest() {
        long now = System.currentTimeMillis();
        if (now - checkedAtMillis < RECHECK_MILLIS) {
            return cached;
        }
        checkedAtMillis = now;
        long cycleStamp = stamp(CyclePaths.stateFile());
        CycleState peek = cycleStamp == cycleFileStamp && cached != null ? null : CycleJsonIO.tryRead(CyclePaths.stateFile());
        String runId = peek != null && peek.lastConstructRunId() != null ? peek.lastConstructRunId().trim()
            : cached != null ? cached.runId() : null;
        long runStamp = runId == null || runId.isBlank() ? Long.MIN_VALUE
            : stamp(ConstructStatePaths.stateFile(UndoPaths.runDir(runId)));
        if (cycleStamp == cycleFileStamp && runStamp == stateFileStamp && cycleFileStamp != Long.MIN_VALUE) {
            return cached;
        }
        cycleFileStamp = cycleStamp;
        stateFileStamp = runStamp;
        cached = resolveNow();
        return cached;
    }

    private static long stamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE;
        } catch (java.io.IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private static ActiveConstructBounds resolveNow() {
        try {
            CycleState cycle = CycleJsonIO.tryRead(CyclePaths.stateFile());
            if (cycle == null || cycle.lastConstructRunId() == null || cycle.lastConstructRunId().isBlank()) {
                return null;
            }

            String runId = cycle.lastConstructRunId().trim();

            Path runDir = UndoPaths.runDir(runId);
            Path stateFile = ConstructStatePaths.stateFile(runDir);
            if (!Files.exists(stateFile)) {
                return null;
            }

            ConstructRunState state = ConstructRunStateIO.read(stateFile);
            if (state.overallMinX() == null || state.overallMinY() == null || state.overallMinZ() == null
                || state.overallMaxX() == null || state.overallMaxY() == null || state.overallMaxZ() == null) {
                return null;
            }

            return new ActiveConstructBounds(
                state.runId(),
                state.dimension(),
                state.overallMinX(),
                state.overallMinY(),
                state.overallMinZ(),
                state.overallMaxX(),
                state.overallMaxY(),
                state.overallMaxZ()
            );
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to resolve latest construct bounds", e);
            return null;
        }
    }
}
