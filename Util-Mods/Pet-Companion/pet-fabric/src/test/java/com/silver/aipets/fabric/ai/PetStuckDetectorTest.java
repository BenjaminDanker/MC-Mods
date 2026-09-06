package com.silver.aipets.fabric.ai;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetStuckDetectorTest {
    @Test
    void triggersAtTimeoutAndRealProgressResetsTheWindow() {
        PetStuckDetector detector = new PetStuckDetector(60, 0.10);
        Vec3d origin = new Vec3d(1.0, 64.0, 1.0);
        detector.reset(origin);

        for (int sample = 0; sample < 5; sample++) {
            assertFalse(detector.sample(origin.add(0.01 * sample, 0.0, 0.0), 10));
        }
        assertFalse(detector.sample(origin.add(1.0, 0.0, 0.0), 10));
        for (int sample = 0; sample < 5; sample++) {
            assertFalse(detector.sample(origin.add(1.0, 0.0, 0.0), 10));
        }
        assertTrue(detector.sample(origin.add(1.0, 0.0, 0.0), 10));
        assertFalse(detector.sample(origin.add(1.0, 0.0, 0.0), 10));
    }
}
