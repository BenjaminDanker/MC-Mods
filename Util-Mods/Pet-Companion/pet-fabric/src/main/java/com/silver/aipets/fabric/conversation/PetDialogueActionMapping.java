package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.transport.PetDialogueAction;
import java.util.Optional;

/** Direct translation of Luna's action; local execution still checks entity/scene safety. */
public final class PetDialogueActionMapping {
    private PetDialogueActionMapping() { }
    public static Optional<com.silver.aipets.fabric.ai.PetIntent> intent(PetDialogueAction action) {
        return switch (action) {
            case NONE -> Optional.empty();
            case APPROACH_OWNER -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.APPROACH_OWNER);
            case LOOK_AT_OWNER -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.LOOK_AT_OWNER);
            case LOOK_AT_INTEREST -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.LOOK_AT_INTEREST);
            case SIT -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.SIT);
            case REST -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.REST);
            case EXPLORE_NEARBY -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.EXPLORE_NEARBY);
            case VOCALIZE -> Optional.of(com.silver.aipets.fabric.ai.PetIntent.VOCALIZE);
        };
    }

}
