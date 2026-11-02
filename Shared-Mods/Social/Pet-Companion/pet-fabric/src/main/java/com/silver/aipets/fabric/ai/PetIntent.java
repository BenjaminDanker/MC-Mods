package com.silver.aipets.fabric.ai;

/** Safe, bounded physical intents. No command, combat, teleport or chunk loading intent exists. */
public enum PetIntent {
    APPROACH_OWNER, GREET_OWNER, LOOK_AT_OWNER, LOOK_AT_INTEREST, PAUSE,
    REST, SIT, STAND, EXPLORE_NEARBY, VOCALIZE, RETURN_TO_OWNER
}
