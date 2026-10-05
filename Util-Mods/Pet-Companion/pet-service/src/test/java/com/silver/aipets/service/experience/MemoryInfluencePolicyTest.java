package com.silver.aipets.service.experience;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MemoryInfluencePolicyTest {
    @Test void distinctiveWitnessedCueInfluencesSilentAttention() {
        assertTrue(MemoryInfluencePolicy.relevant("OWNER_ACTION",
                "Owner broke minecraft:oak_log nearby in biome=minecraft:plains",
                "We gathered oak logs by the river"));
        assertFalse(MemoryInfluencePolicy.relevant("OWNER_ACTION",
                "Owner broke minecraft:oak_log nearby in biome=minecraft:plains",
                "Owner mentioned a bridge in the plains"));
        assertFalse(MemoryInfluencePolicy.relevant("NEW_AREA",
                "Owner and pet visited area plains", "We gathered oak logs"));
    }
}
