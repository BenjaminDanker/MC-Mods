package com.silver.atlantis.spawn;

/**
 * Hard-coded spawn-cycle special drop settings.
 *
 * Intentionally simple and code-editable (mirrors CycleConfig style).
 */
public final class SpawnSpecialConfig {

    private SpawnSpecialConfig() {
    }

    /** How many spawned mobs per /structuremob run get marked as special (0 disables). */
    public static final int SPECIAL_MOBS_PER_RUN = 6;

    /** Scoreboard tag used to mark special mobs. */
    public static final String SPECIAL_MOB_TAG = "atlantis_special_drop";

    /** What item special mobs should drop when they die. */
    public static final String SPECIAL_DROP_ITEM_ID = "minecraft:heart_of_the_sea";

    /** Display name for the special dropped item (null/blank to keep default item name). */
    public static final String SPECIAL_DROP_DISPLAY_NAME = "Special Heart of the Sea";

    /** How many of the item to drop. */
    public static final int SPECIAL_DROP_ITEM_COUNT = 1;

    /**
     * Custom data (SNBT compound) stored on the dropped item.
     * Example: {atlantis_special:1b,run:"cycle"}
     */
    public static final String SPECIAL_DROP_CUSTOM_DATA_SNBT = "{atlantis_special:1b}";
}
