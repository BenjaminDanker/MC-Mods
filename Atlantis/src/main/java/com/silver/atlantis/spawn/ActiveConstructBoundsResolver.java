package com.silver.atlantis.spawn;

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

    public static ActiveConstructBounds tryResolveLatest() {
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
            AtlantisMod.LOGGER.warn("Failed to resolve latest construct bounds: {}", e.getMessage());
            return null;
        }
    }
}
