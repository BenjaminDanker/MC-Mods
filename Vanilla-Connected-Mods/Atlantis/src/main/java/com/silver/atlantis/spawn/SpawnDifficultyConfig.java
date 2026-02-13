package com.silver.atlantis.spawn;

/**
 * Hard-coded difficulty tuning for Atlantis mob spawning.
 */
public final class SpawnDifficultyConfig {

    private SpawnDifficultyConfig() {
    }

    /** Relative schematic bottom Y (used to resolve the world-space easy-Y). */
    public static final int SCHEMATIC_MIN_Y_REL = -6;

    /** Relative schematic top Y (used to clamp to bounds). */
    public static final int SCHEMATIC_MAX_Y_REL = 241;

    /** Relative Y where mobs are easiest unless close to center. */
    public static final int SCHEMATIC_EASIEST_Y_REL = 64;

    /** Difficulty weight from center density (x/z proximity). */
    public static final double CENTER_DIFFICULTY_WEIGHT = 2.5;

    /** Difficulty weight for height above the easy level. */
    public static final double HEIGHT_DIFFICULTY_WEIGHT = 5.0;

    /** Difficulty weight for depth below the easy level. */
    public static final double DEEP_DIFFICULTY_WEIGHT = 5.0;
}
