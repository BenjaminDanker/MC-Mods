package com.silver.elementchoice.skills;

/** What each ability level adds. Level 1 is the ability as Bending ships it (after the server-wide damage bump). */
public final class LevelScaling {
    private LevelScaling() {
    }

    private static int extra(int level) {
        return Math.max(0, level - 1);
    }

    public static double damage(int level) {
        return 1 + 0.10 * extra(level);
    }

    /** Multiplier for cooldowns: shorter at higher levels, never below 60%. */
    public static double cooldown(int level) {
        return Math.max(0.6, 1 - 0.05 * extra(level));
    }

    public static double range(int level) {
        return 1 + 0.05 * extra(level);
    }

    public static double radius(int level) {
        return 1 + 0.04 * extra(level);
    }

    public static double duration(int level) {
        return 1 + 0.05 * extra(level);
    }

    public static double speed(int level) {
        return 1 + 0.03 * extra(level);
    }

    /** Resilience: armor points and armor toughness for each level. */
    public static double resilienceArmor(int level) {
        return 0.75 * level;
    }

    public static double resilienceToughness(int level) {
        return 0.25 * level;
    }

    /** Ley Attunement: each level of an element adds 4% damage and trims 3% off every cooldown of that element's abilities. */
    public static double attuneDamage(int level) {
        return 1 + 0.04 * level;
    }

    public static double attuneCooldown(int level) {
        return Math.max(0.7, 1 - 0.03 * level);
    }
}
