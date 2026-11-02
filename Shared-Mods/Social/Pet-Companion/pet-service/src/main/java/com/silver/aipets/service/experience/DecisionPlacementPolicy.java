package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.PlacedPlacement;
import java.util.Objects;

/** Source placement must still be the same materialized entity after model work. */
public final class DecisionPlacementPolicy {
    private DecisionPlacementPolicy() { }
    public static boolean matches(PlacedPlacement source, String state,
            String entityId, String backend, String dimension) {
        return source != null && source.isMaterialized()
                && "PLACED".equals(state)
                && Objects.equals(source.entityUuid().orElseThrow().toString(), entityId)
                && Objects.equals(source.backendId().value(), backend)
                && Objects.equals(source.dimensionId().toString(), dimension);
    }
}
