package com.silver.aipets.fabric.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PetPhysicalConfigTest {
    @Test
    void choosesAdaptiveSpeedByDistanceBand() {
        PetPhysicalConfig config = PetPhysicalConfig.defaults();

        assertEquals(config.nearSpeed(), config.speedForSquaredDistance(8.99 * 8.99));
        assertEquals(config.mediumSpeed(), config.speedForSquaredDistance(9.0 * 9.0));
        assertEquals(config.farSpeed(), config.speedForSquaredDistance(16.0 * 16.0));
    }

    @Test
    void rejectsInvalidDistanceAndSpeedRelationships() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PetPhysicalConfig(2, 5, 10, 1, 1.2, 1.4, 9, 16, 60, 0.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PetPhysicalConfig(5, 2, 10, 1.4, 1.2, 1.0, 9, 16, 60, 0.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PetPhysicalConfig.defaults().speedForSquaredDistance(Double.NaN));
    }
}
