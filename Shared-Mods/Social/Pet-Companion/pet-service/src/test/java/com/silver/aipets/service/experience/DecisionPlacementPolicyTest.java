package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DecisionPlacementPolicyTest {
    @Test void transferPickupAndEntityReplacementInvalidateTheOldPlan() {
        UUID entity = UUID.randomUUID();
        var source = PlacedPlacement.materialized(new BackendId("sky-island"),
                DimensionId.parse("minecraft:overworld"),
                new WorldPosition(0, 64, 0), entity);
        assertTrue(DecisionPlacementPolicy.matches(source, "PLACED", entity.toString(),
                "sky-island", "minecraft:overworld"));
        assertFalse(DecisionPlacementPolicy.matches(source, "TRANSFERRING",
                entity.toString(), "sky-island", "minecraft:overworld"));
        assertFalse(DecisionPlacementPolicy.matches(source, "HELD",
                entity.toString(), "sky-island", "minecraft:overworld"));
        assertFalse(DecisionPlacementPolicy.matches(source, "PLACED",
                UUID.randomUUID().toString(), "sky-island", "minecraft:overworld"));
        assertFalse(DecisionPlacementPolicy.matches(source, "PLACED",
                entity.toString(), "ocean", "minecraft:overworld"));
        assertFalse(DecisionPlacementPolicy.matches(source, "PLACED",
                entity.toString(), "sky-island", "minecraft:the_nether"));
    }
}
