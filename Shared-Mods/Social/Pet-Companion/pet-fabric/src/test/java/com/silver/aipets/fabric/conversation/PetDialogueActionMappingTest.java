package com.silver.aipets.fabric.conversation;

import com.silver.aipets.fabric.ai.PetIntent;
import com.silver.aipets.common.transport.PetDialogueAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetDialogueActionMappingTest {
    @Test void everyLunaActionMapsDirectlyWithoutInspectingDialogueText() {
        for (var action : PetDialogueAction.values()) {
            var mapped=PetDialogueActionMapping.intent(action);
            if (action==PetDialogueAction.NONE) assertTrue(mapped.isEmpty());
            else assertEquals(PetIntent.valueOf(action.name()),mapped.orElseThrow());
        }
    }
}
