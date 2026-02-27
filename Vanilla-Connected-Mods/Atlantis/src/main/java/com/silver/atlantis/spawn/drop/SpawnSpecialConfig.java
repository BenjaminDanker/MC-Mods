package com.silver.atlantis.spawn.drop;

/**
 * Hard-coded spawn-cycle special drop settings.
 *
 * Intentionally simple and code-editable (mirrors CycleConfig style).
 */
public final class SpawnSpecialConfig {

    private SpawnSpecialConfig() {
    }

    /** Scoreboard tag applied to all Atlantis structure-spawned mobs. */
    public static final String ATLANTIS_SPAWNED_MOB_TAG = "atlantis_spawned_mob";

    /** Command tag prefix storing special drop amount on a spawned mob. */
    public static final String SPECIAL_DROP_AMOUNT_TAG_PREFIX = "atlantis_special_drop_amount=";

    /** Compact fallback prefix for amount tags when long tags are rejected by the platform. */
    public static final String SPECIAL_DROP_AMOUNT_TAG_PREFIX_COMPACT = "asd=";

    /** What item special mobs should drop when they die. */
    public static final String SPECIAL_DROP_ITEM_ID = "minecraft:heart_of_the_sea";

    /** Display name for the special dropped item (null/blank to keep default item name). */
    public static final String SPECIAL_DROP_DISPLAY_NAME = "Special Heart of the Sea";

    /** How many of the item to drop. */
    public static final int SPECIAL_DROP_ITEM_COUNT = 1;

    /** Enables verbose logs for special-drop roll, tagging, and death-drop behavior. */
    public static final boolean SPECIAL_DROP_DEBUG_LOGS = true;

    /**
     * Custom data (SNBT compound) stored on the dropped item.
     * Example: {atlantis_special:1b,run:"cycle"}
     */
    public static final String SPECIAL_DROP_CUSTOM_DATA_SNBT = "{atlantis_special:1b}";

    /** How many special dropped items convert into 1 Special Sea Lantern. */
    public static final int SPECIAL_ITEMS_PER_SEA_LANTERN = 256;

    /** Target number of conversions achievable from a full dungeon clear (approximate). */
    public static final double TARGET_CONVERSIONS_PER_FULL_CLEAR = 3.0;

    /** Expected boss count per full clear used for drop scaling estimates. */
    public static final int EXPECTED_BOSS_MOBS_PER_RUN = 1;

    /** Extra scaling applied to boss special-drop amounts. */
    public static final double BOSS_SPECIAL_DROP_MULTIPLIER = 5.0;

    /** Minimum special-drop item count per spawned boss (applied during global allocation). */
    public static final int BOSS_SPECIAL_DROP_MIN = 30;

    /** Random variance applied to special-drop amount roll. */
    public static final double SPECIAL_DROP_RANDOMNESS_PERCENT = 20.0;

    /** Maximum amount for non-boss special drops at top difficulty. */
    public static final int SPECIAL_DROP_MAX_AMOUNT_NON_BOSS = 16;

    /** Maximum amount for boss special drops at top difficulty. */
    public static final int SPECIAL_DROP_MAX_AMOUNT_BOSS = 20;

    /** Output item id for conversion reward. */
    public static final String SPECIAL_SEA_LANTERN_ITEM_ID = "minecraft:sea_lantern";

    /** Display name for the converted output. */
    public static final String SPECIAL_SEA_LANTERN_DISPLAY_NAME = "Special Sea Lantern";

    /** Custom data applied to the converted output. */
    public static final String SPECIAL_SEA_LANTERN_CUSTOM_ID = "special_sea_lantern";

    /** Custom data applied to the converted output. */
    public static final String SPECIAL_SEA_LANTERN_CUSTOM_ITEM_TYPE = "Soulbound";
}
