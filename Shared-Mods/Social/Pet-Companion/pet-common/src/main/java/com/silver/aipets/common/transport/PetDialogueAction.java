package com.silver.aipets.common.transport;

/** Bounded local actions a dialogue response may suggest. Fabric validates each again. */
public enum PetDialogueAction {
    NONE, APPROACH_OWNER, LOOK_AT_OWNER, LOOK_AT_INTEREST, SIT, REST, EXPLORE_NEARBY, VOCALIZE
}
