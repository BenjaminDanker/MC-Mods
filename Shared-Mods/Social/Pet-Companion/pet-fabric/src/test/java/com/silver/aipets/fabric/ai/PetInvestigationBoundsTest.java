package com.silver.aipets.fabric.ai;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetInvestigationBoundsTest {
    @Test void shortRouteAcrossChunkBorderFitsSimulationDistanceFour() {
        assertTrue(PetInvestigationBounds.contains(new BlockPos(15, 70, 15),
                new BlockPos(17, 70, 15), new BlockPos(20, 70, 18), 4));
        assertTrue(PetInvestigationBounds.contains(new BlockPos(0, 70, 0),
                new BlockPos(-2, 70, 0), new BlockPos(-5, 70, -3), 4));
        assertFalse(PetInvestigationBounds.contains(new BlockPos(15, 70, 15),
                new BlockPos(17, 70, 15), new BlockPos(39, 70, 18), 4));
        assertFalse(PetInvestigationBounds.contains(new BlockPos(15, 70, 15),
                new BlockPos(17, 70, 15), new BlockPos(20, 70, 18), 1));
    }
}
