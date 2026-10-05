package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.domain.*;
import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.dialogue.JTokkitDialogueTokenCounter;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.memory.MemoryProvenance;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ConsolidationGroundingTest {
    @Test void fallbackKeepsOnlyVerbatimHighWitnessedEpisode() {
        Instant at=Instant.parse("2026-09-27T12:00:00Z");
        UUID id=UUID.randomUUID();
        var appearance=PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.7,null,
                AppearanceRules.defaults());
        Pet pet=Pet.adopted(id,UUID.randomUUID(),"Mica",appearance,
                PetTraits.initial(70,40,40,40,70,at),PetMood.initial(50,10,10,20,at),at);
        ConsolidationEvent claim=new ConsolidationEvent(UUID.randomUUID(),id,
                DialogueImportance.HIGH,"Owner said: I crossed a bridge", "DIALOGUE",at,
                MemoryProvenance.OWNER_STATEMENT);
        ConsolidationEvent seen=new ConsolidationEvent(UUID.randomUUID(),id,
                DialogueImportance.HIGH,"We crossed a narrow spruce bridge together in the rain.",
                "ARRIVED_TOGETHER",at,MemoryProvenance.VERIFIED_OBSERVATION);
        var selection=new CandidateSelection(List.of(claim,seen),List.of(),30);
        var fallback=GroundedEpisodeFallback.from(pet,selection,
                new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                        new JTokkitDialogueTokenCounter())).orElseThrow();
        assertEquals(1,fallback.memoryCards().size());
        assertEquals(seen.summary(),fallback.memoryCards().getFirst().text());
        assertEquals(List.of(seen.eventId()),fallback.memoryCards().getFirst().sourceEventIds());
        assertTrue(GroundedEpisodeFallback.from(pet,
                new CandidateSelection(List.of(claim),List.of(),12),
                new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                        new JTokkitDialogueTokenCounter())).isEmpty());
    }
    @Test void episodeCanBeGroundedWithoutInventingRelationshipChange() {
        UUID pet=UUID.randomUUID();
        ConsolidationEvent episode=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"We crossed a narrow spruce bridge together in the rain.",
                "ARRIVED_TOGETHER",Instant.now());
        EnumMap<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for(TraitName name:TraitName.values()) deltas.put(name,0);
        ConsolidationModelOutput output=new ConsolidationModelOutput(List.of(
                new ConsolidationCardProposal("Crossed a narrow spruce bridge together in the rain.",
                        MemoryImportance.HIGH,List.of(episode.eventId()),Set.of(),Set.of(),Set.of())),
                "",deltas);
        var validated=new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter()).validate(output,
                        new CandidateSelection(List.of(episode),List.of(),30));
        assertEquals("",validated.relationshipSummary());
        assertEquals(1,validated.memoryCards().size());
    }

    @Test void completedActionCardRetainsServerEnumAndReasonVerbatim() {
        UUID pet=UUID.randomUUID();
        ConsolidationEvent action=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.MEDIUM,"Pet completed EXPLORE_NEARBY reason=CURIOSITY",
                "ACTION_COMPLETED",Instant.now(),MemoryProvenance.VERIFIED_OBSERVATION);
        EnumMap<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for(TraitName name:TraitName.values()) deltas.put(name,0);
        var output=new ConsolidationModelOutput(List.of(new ConsolidationCardProposal(
                "Pet completed EXPLORE_NEARBY reason=CURIOSITY",MemoryImportance.MEDIUM,
                List.of(action.eventId()),Set.of(),Set.of(),Set.of())),"",deltas);
        var validated=new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter()).validate(output,
                        new CandidateSelection(List.of(action),List.of(),20));
        assertEquals(List.of(output.memoryCards().getFirst()),validated.memoryCards());

        var appearance=PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.7,null,
                AppearanceRules.defaults());
        Pet mica=Pet.adopted(pet,UUID.randomUUID(),"Mica",appearance,
                PetTraits.initial(70,40,40,40,70,Instant.now()),
                PetMood.initial(50,10,10,20,Instant.now()),Instant.now());
        String prompt=new ConsolidationPromptBuilder(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter()).build(mica,
                        new CandidateSelection(List.of(action),List.of(),20)).prompt().text();
        assertTrue(prompt.contains("copy the full source summary verbatim"));
    }
    @Test void unsupportedQuestionSummaryDoesNotEraseGroundedWitnessedEpisode() {
        UUID pet=UUID.randomUUID();
        ConsolidationEvent witnessed=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"We crossed a narrow spruce bridge together in the rain.",
                "ARRIVED_TOGETHER",Instant.now());
        ConsolidationEvent question=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.MEDIUM,"Owner said: Would the hill path be better for our walk today?",
                "DIALOGUE",Instant.now());
        EnumMap<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for(TraitName name:TraitName.values()) deltas.put(name,0);
        ConsolidationCardProposal good=new ConsolidationCardProposal(
                "Crossed a narrow spruce bridge together in the rain.",
                MemoryImportance.HIGH,List.of(witnessed.eventId()),Set.of(),Set.of(),Set.of());
        ConsolidationCardProposal unsupported=new ConsolidationCardProposal(
                "Owner asked whether the hill path was better for their walk.",
                MemoryImportance.MEDIUM,List.of(question.eventId()),Set.of(),Set.of(),Set.of());
        var selection=new CandidateSelection(List.of(witnessed,question),List.of(),45);
        var validator=new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter());
        var result=validator.validate(new ConsolidationModelOutput(
                List.of(good,unsupported),"",deltas),selection);
        assertEquals(List.of(good),result.memoryCards());
    }
    @Test void multipleCitedEpisodesCanSupportAnExplicitRecurringPattern() {
        UUID pet=UUID.randomUUID();
        ConsolidationEvent built=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"Owner built bridge near river","OWNER_ACTION",Instant.now());
        ConsolidationEvent repaired=new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"Owner repaired bridge near river","OWNER_ACTION",Instant.now());
        var validator=new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter());
        EnumMap<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for (TraitName name:TraitName.values()) deltas.put(name,0);
        var selection=new CandidateSelection(List.of(built,repaired),List.of(),20);
        var grounded=new ConsolidationModelOutput(List.of(new ConsolidationCardProposal(
                "Owner often built bridge and repaired bridge near river",
                MemoryImportance.HIGH,List.of(built.eventId(),repaired.eventId()),
                Set.of(),Set.of(),Set.of())),"Shared work near river",deltas);
        assertEquals(1,validator.validate(grounded,selection).memoryCards().size());
        var invented=new ConsolidationModelOutput(List.of(new ConsolidationCardProposal(
                "Owner often built bridge and defeated dragon near river",
                MemoryImportance.HIGH,List.of(built.eventId(),repaired.eventId()),
                Set.of(),Set.of(),Set.of())),"Shared work near river",deltas);
        assertThrows(ConsolidationOutputException.class,
                () -> validator.validate(invented,selection));
    }

    @Test void oneSharedWordDoesNotValidateAnInventedAdventure() {
        UUID pet=UUID.randomUUID(), eventId=UUID.randomUUID();
        ConsolidationEvent source=new ConsolidationEvent(eventId,pet,DialogueImportance.HIGH,
                "Owner explored crimson forest with pet", "NEW_AREA",Instant.now());
        CandidateSelection selection=new CandidateSelection(List.of(source),List.of(),8);
        EnumMap<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for (TraitName name:TraitName.values()) deltas.put(name,0);
        ConsolidationOutputValidator validator=new ConsolidationOutputValidator(
                ConsolidationConfig.defaults(),new JTokkitDialogueTokenCounter());
        ConsolidationModelOutput invented=new ConsolidationModelOutput(List.of(
                new ConsolidationCardProposal("Owner defeated dragon in forest",
                        MemoryImportance.HIGH,List.of(eventId),Set.of(),Set.of(),Set.of())),
                "A trip together",deltas);
        assertThrows(ConsolidationOutputException.class,
                () -> validator.validate(invented,selection));
        ConsolidationModelOutput grounded=new ConsolidationModelOutput(List.of(
                new ConsolidationCardProposal("Owner explored crimson forest",
                        MemoryImportance.HIGH,List.of(eventId),Set.of(),Set.of(),Set.of())),
                "A trip together",deltas);
        assertEquals(1,validator.validate(grounded,selection).memoryCards().size());

        ConsolidationEvent preference = new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"Owner said: My favorite color is blue",
                "DIALOGUE",Instant.now());
        CandidateSelection preferenceSelection = new CandidateSelection(
                List.of(preference),List.of(),8);
        ConsolidationModelOutput contradicted = new ConsolidationModelOutput(List.of(
                new ConsolidationCardProposal("Owner's favorite color is red",
                        MemoryImportance.HIGH,List.of(preference.eventId()),
                        Set.of(),Set.of(),Set.of())),"A trip together",deltas);
        assertThrows(ConsolidationOutputException.class,
                () -> validator.validate(contradicted,preferenceSelection));

        ConsolidationEvent denied = new ConsolidationEvent(UUID.randomUUID(),pet,
                DialogueImportance.HIGH,"Owner said: I prefer cats not dogs",
                "DIALOGUE",Instant.now());
        ConsolidationModelOutput inverted = new ConsolidationModelOutput(List.of(
                new ConsolidationCardProposal("Owner prefers dogs",
                        MemoryImportance.HIGH,List.of(denied.eventId()),
                        Set.of(),Set.of(),Set.of())),"A trip together",deltas);
        assertThrows(ConsolidationOutputException.class,
                () -> validator.validate(inverted,
                        new CandidateSelection(List.of(denied),List.of(),8)));
    }
}
