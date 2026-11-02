package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetSceneSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DialogueAttentionTest {
    private static final DialogueGameContext SCENE = new DialogueGameContext(
            "sky-island", "minecraft:overworld", "PLAYER_MESSAGE",
            new PetSceneSnapshot("minecraft:plains", "minecraft:plains", "NEAR",
                    true, "MOVING", "HEALTHY", "PAUSE", "CLEAR", false));

    @Test void ordinaryConversationOmitsBackgroundGeographyAndMovement() {
        String rendered = DialogueAttention.render("How are you?", SCENE);
        assertFalse(rendered.contains("sky"));
        assertFalse(rendered.contains("biome"));
        assertFalse(rendered.contains("MOVING"));
    }

    @Test void placeQuestionAndImmediateDangerSurfaceRelevantFacts() {
        String rendered = DialogueAttention.render("Where did we travel?", SCENE);
        assertTrue(rendered.contains("current_server="));
        assertTrue(rendered.contains("current_biome=minecraft:plains"));
        var danger = new DialogueGameContext("sky-island", "minecraft:overworld",
                "PLAYER_MESSAGE", new PetSceneSnapshot("unknown", "unknown", "NEAR",
                        true, "RECENTLY_HURT", "HURT", "PAUSE", "THUNDER", true));
        assertTrue(DialogueAttention.render("Hello", danger).contains("visible_threat=true"));
        assertTrue(DialogueAttention.render("Hello", danger).contains("owner_health=HURT"));
    }
}
