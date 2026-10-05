package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DialoguePromptOrganicStateTest {
    @Test void promptUsesCompactBehavioralStateAndOnlySuppliedCueMatchedThreads() {
        Instant now=Instant.parse("2026-09-28T12:00:00Z");
        UUID owner=UUID.randomUUID();
        PetAppearance appearance=PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.7,42L,AppearanceRules.defaults());
        PetTraits traits=new PetTraits(60,30,35,30,80,25,35,45,"reserved",0,now);
        Pet pet=new Pet(UUID.randomUUID(),owner,"Mica",appearance,traits,PetMood.initial(50,30,10,5,now),
                PlacedPlacement.materialized(new BackendId("survival"),DimensionId.parse("minecraft:overworld"),
                        new WorldPosition(0,64,0),UUID.randomUUID()),0,now,now);
        var organic=new OrganicPetContext(new BehavioralAffect(-18,20,30,35,-22,24,"WARY",owner,now),
                List.of("interests:riverside paths"),
                List.of(new OrganicPetContext.ThreadCue("thread-1","Owner said they were building a bridge.","OPEN","forest",2)),
                Optional.of(new OwnerInterpretation(Optional.of("REQUEST"),Optional.of("CONCERNED"),.55,now)),
                Optional.empty());
        var context=new DialogueContext(pet,false,true,List.of(),List.of(),List.of(),
                new DialogueGameContext("survival","minecraft:overworld","dialogue"),now,List.of(),organic);
        DialogueTokenCounter tokens=new DialogueTokenCounter(){public int count(String text){return text.length()/4;}public String truncate(String text,int max){return text;}};
        var limits=new DialogueLimits(500,300,8000,250,1200,600,700,4,Duration.ofSeconds(5),5,8,
                Duration.ofSeconds(10),new BigDecimal("25"),new BigDecimal("500"),5,Duration.ofMinutes(1));
        var assembled=new DialoguePromptBuilder(limits,tokens).build(context,"I meant the bridge; what do you think?");
        String prompt=assembled.text();
        assertTrue(prompt.contains("alertness=30/100"));
        assertTrue(prompt.contains("SELF_MODEL_CUES"));
        assertTrue(prompt.contains("OPEN_THREADS"));
        assertTrue(prompt.contains("TENTATIVE_OWNER_INTERPRETATION"));
        assertTrue(prompt.contains("confidence=0.55"));
        assertTrue(prompt.contains("source-tagged unfinished activities"));
        assertEquals("I meant the bridge; what do you think?",assembled.currentOwnerMessage().orElseThrow());
        assertTrue(prompt.contains("Corrections override older claims"));
        assertTrue(prompt.contains("Resume only matching open threads"));
    }
}
