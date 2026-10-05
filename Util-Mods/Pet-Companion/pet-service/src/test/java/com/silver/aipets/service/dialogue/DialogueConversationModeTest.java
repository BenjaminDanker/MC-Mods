package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DialogueConversationModeTest {
    @Test void modeIsAnEnumNotAReplyClassifier() {
        assertEquals(DialogueConversationMode.ASK,DialogueConversationMode.validated("ask"));
        assertEquals(DialogueConversationMode.ANSWER,DialogueConversationMode.validated("INVALID"));
    }

    @Test void productionCodecReadsModeAndFallsBackToAnswerForInvalidMode() {
        String json = "{\"reply\":\"Which bridge?\",\"action\":\"NONE\",\"attention\":\"QUIET\","
                + "\"memory_candidate\":null,\"affect_delta\":\"NONE\",\"affect_strength\":0,"
                + "\"relationship_event\":{\"type\":\"NONE\",\"strength\":0},\"conversation_mode\":\"ASK\","
                + "\"self_model_candidate\":{\"kind\":\"NONE\",\"value\":null,\"confidence\":0},"
                + "\"owner_interpretation\":{\"intent\":null,\"emotion\":null,\"confidence\":0}}";
        var decoded = new DialogueOutputCodec().decode(json);
        assertEquals(DialogueConversationMode.ASK, decoded.conversationMode());
        String proposedState = json.replace("\"reply\":\"Which bridge?\"", "\"reply\":\"The spruce crossing.\"")
                .replace("\"action\":\"NONE\"", "\"action\":\"APPROACH_OWNER\"")
                .replace("\"memory_candidate\":null", "\"memory_candidate\":\"new memory\"")
                .replace("\"affect_delta\":\"NONE\"", "\"affect_delta\":\"BRIGHTER\"")
                .replace("\"relationship_event\":{\"type\":\"NONE\",\"strength\":0}",
                        "\"relationship_event\":{\"type\":\"WARMTH\",\"strength\":2}")
                .replace("\"confidence\":0}}", "\"confidence\":0.8}}")
                .replace("\"ASK\"", "\"MADE_UP\"");
        var rejected = new DialogueOutputCodec().decode(proposedState);
        assertEquals(DialogueConversationMode.ANSWER, rejected.conversationMode());
        assertEquals(com.silver.aipets.common.transport.PetDialogueAction.APPROACH_OWNER,rejected.action());
        assertEquals(DialogueAffect.BRIGHTER,rejected.affectDelta());
        assertEquals(RelationshipSignal.Type.WARMTH,rejected.relationshipSignal().type());
        assertEquals("new memory",rejected.memoryCandidate().orElseThrow());
    }

    @Test void providerDefaultStrengthOnNeutralRelationshipIsNormalizedToNoOp() {
        String json = "{\"reply\":\"The path is clear.\",\"action\":\"NONE\",\"attention\":\"QUIET\","
                + "\"memory_candidate\":null,\"affect_delta\":\"NONE\",\"affect_strength\":0,"
                + "\"relationship_event\":{\"type\":\"NONE\",\"strength\":1},\"conversation_mode\":\"ANSWER\","
                + "\"self_model_candidate\":{\"kind\":\"NONE\",\"value\":null,\"confidence\":0},"
                + "\"owner_interpretation\":{\"intent\":null,\"emotion\":null,\"confidence\":0}}";
        var decoded = new DialogueOutputCodec().decode(json);
        assertEquals(RelationshipSignal.none(), decoded.relationshipSignal());
    }

    @Test void productionPromptDescribesGroundedQuietModeChoices() {
        var limits = new DialogueLimits(500,120,4000,80,100,80,80,4,
                java.time.Duration.ofSeconds(5),10,2,java.time.Duration.ofSeconds(10),
                BigDecimal.TEN,BigDecimal.valueOf(100),4,java.time.Duration.ofMinutes(1));
        var pet = new com.silver.aipets.common.domain.Pet(
                java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"Mica",
                com.silver.aipets.common.domain.PetAppearance.create(com.silver.aipets.common.domain.PetSpecies.CAT,
                        "minecraft:tabby",.7,1L,com.silver.aipets.common.domain.AppearanceRules.defaults()),
                new com.silver.aipets.common.domain.PetTraits(50,50,50,50,50,50,50,50,"",0,java.time.Instant.EPOCH),
                com.silver.aipets.common.domain.PetMood.initial(50,50,50,50,java.time.Instant.EPOCH),
                com.silver.aipets.common.domain.PlacedPlacement.materialized(new com.silver.aipets.common.domain.BackendId("test"),
                        com.silver.aipets.common.domain.DimensionId.parse("minecraft:overworld"),
                        new com.silver.aipets.common.domain.WorldPosition(0,64,0),java.util.UUID.randomUUID()),0,
                java.time.Instant.EPOCH,java.time.Instant.EPOCH);
        var context=new DialogueContext(pet,false,true,java.util.List.of(),java.util.List.of(),java.util.List.of(),
                new DialogueGameContext("test","minecraft:overworld","dialogue"),java.time.Instant.EPOCH);
        String prompt=new DialoguePromptBuilder(limits,new WordCounter()).build(context,"hello").text();
        assertTrue(prompt.contains("code does not repair conversation"));
        assertTrue(prompt.contains("CLARIFY for a materially unresolved reference"));
        assertTrue(prompt.contains("PET replies are not evidence"));
        assertTrue(prompt.contains("Only an asked reason for your own behavior"));
        assertTrue(prompt.contains("source-bound update"));
        assertTrue(prompt.contains("social_stance <= -40"));
    }

    private static final class WordCounter implements DialogueTokenCounter {
        public int count(String text) { return text.isBlank()?0:text.strip().split("\\s+").length; }
        public String truncate(String text,int maximum) { return text; }
    }
}
