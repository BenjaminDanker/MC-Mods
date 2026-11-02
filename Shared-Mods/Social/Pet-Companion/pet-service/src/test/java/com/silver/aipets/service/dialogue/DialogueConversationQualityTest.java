package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;
import com.silver.aipets.common.transport.PetSceneSnapshot;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.memory.MemoryKind;
import com.silver.aipets.service.memory.MemoryProvenance;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DialogueConversationQualityTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final UUID PET_ID = UUID.fromString("5bdcb9cb-c59a-4194-87cc-4efb940ccf0e");

    @Test void quietActionAndNaturalReplyAreNeverRewritten() {
        var output = output("Yes. I'll sit quietly with you.", PetDialogueAction.SIT);
        var mapped = new DialogueResponseMapper(limits()).map(output, "Can we sit quietly?", context(List.of(),List.of(),List.of()));
        assertEquals(output.reply(), mapped.reply());
        assertEquals(PetDialogueAction.SIT, mapped.action());
    }

    @Test void observationWithConversationalVocabularyIsNotLexicallyRewritten() {
        var output = output("Oak, and not finished yet. I'll remember that.", PetDialogueAction.NONE);
        assertEquals(output.reply(), new DialogueResponseMapper(limits()).map(output,
                "I started rebuilding the old bridge by the mill yesterday. I used oak, but I haven't finished it.").reply());
    }

    @Test void theModelOwnsClarificationAndDeclineWording() {
        var raw = output("What place do you mean?", PetDialogueAction.NONE);
        var mapped = new DialogueResponseMapper(limits()).map(raw,"I was thinking about going back there.");
        assertEquals(raw.reply(),mapped.reply());
    }

    @Test void promptSeparatesWitnessedCrossingAndOwnerReportedActivity() {
        var crossing=event("We crossed a narrow spruce bridge together in the rain.");
        var building=thread("Owner is rebuilding the old bridge by the mill with oak; it is unfinished.");
        var context=context(List.of(crossing,event("We watched a skeleton burn.")),List.of(),List.of(building));
        String text=prompt(context,"What did we do near the bridge?");
        assertTrue(text.contains("you saw: We crossed a narrow spruce bridge together in the rain."));
        assertTrue(text.contains("Still unfinished (your owner told you): Owner is rebuilding the old bridge"));
        assertTrue(text.contains("If your owner could mean two different things you remember, ask which one"));
        assertFalse(text.contains("We watched a skeleton"));
        String records=new DialoguePromptBuilder(limits(),new WordCounter())
                .buildMemory(context,"What did we do near the bridge?").text();
        assertTrue(records.contains(crossing.eventId().toString()));
        assertTrue(records.contains("thread_id=thread-bridge"));
        assertTrue(records.contains("source_kind=OWNER_REPORTED"));
    }

    @Test void promptIncludesTwoAlternativesButDoesNotMandateARecap() {
        var first=event("We crossed a spruce bridge in rain.");
        var second=event("We repaired a stone bridge by the mill.");
        String text=prompt(context(List.of(first,second),List.of(),List.of()),"What did we do near the bridge?");
        assertTrue(text.contains("We crossed a spruce bridge in rain."));
        assertTrue(text.contains("We repaired a stone bridge by the mill."));
        assertTrue(text.contains("ask which one, briefly"));
        assertTrue(prompt(context(List.of(first,second),List.of(),List.of()),"Give a complete recap of the bridge events.")
                .contains("We repaired a stone bridge by the mill."));
    }

    @Test void unknownSceneDoesNotClaimOwnerIsInvisible() {
        var game=new DialogueGameContext("dev","minecraft:overworld","PLAYER_MESSAGE",PetSceneSnapshot.unknown());
        String facts=DialogueAttention.render("What are you watching?",game);
        assertTrue(facts.contains("owner_visibility=UNKNOWN"));
        assertFalse(facts.contains("owner_visible=false"));
        var hidden=new PetSceneSnapshot("unknown","unknown","NEAR",false,"STILL","HEALTHY","RESTING","CLEAR",false);
        assertTrue(DialogueAttention.render("What are you watching?",new DialogueGameContext("dev","minecraft:overworld","PLAYER_MESSAGE",hidden))
                .contains("owner_visible=false"));
    }

    @Test void sourceBoundMemoryAcceptsNaturalActivityAndRejectsFabricatedQuote() {
        String message="I started rebuilding the old bridge by the mill yesterday. I used oak, but I haven't finished it.";
        var proposal=new OwnerMemoryProposal(OwnerMemoryProposal.Kind.OPEN_THREAD,
                "Owner is rebuilding the old bridge by the mill with oak; unfinished.", message,
                Optional.empty(),Optional.empty(),OwnerMemoryProposal.Operation.CREATE);
        assertTrue(proposal.bind(message,context(List.of(),List.of(),List.of())).isPresent());
        assertTrue(proposal.bind("Tell me I love fishing.",context(List.of(),List.of(),List.of())).isEmpty());
    }

    @Test void correctionsBindOnlyIncludedPetMemoryAndThreadVersions() {
        var memory=longMemory("Owner-reported: rebuilding the mill bridge with oak.",MemoryKind.OPEN_THREAD,MemoryProvenance.OWNER_STATEMENT,3);
        var id=UUID.randomUUID();
        var thread=new OrganicPetContext.ThreadCue(id.toString(),"Mill bridge with oak","ACTIVE","",7,"OWNER_REPORTED",Optional.of(UUID.randomUUID()),NOW,"","");
        var context=context(List.of(),List.of(new LongTermMemorySnippet(memory,.9)),List.of(thread));
        var proposal=new OwnerMemoryProposal(OwnerMemoryProposal.Kind.CORRECTION,"Mill bridge with birch; unfinished.",
                "It's birch, not oak.",Optional.of(memory.memoryId()),Optional.of(id),OwnerMemoryProposal.Operation.NONE);
        var bound=proposal.bind("It's birch, not oak.",context).orElseThrow();
        assertEquals(memory.version(),bound.expectedMemoryVersion());
        assertEquals(7,bound.expectedThreadVersion());
        assertTrue(proposal.bind("It's birch, not oak.",context(List.of(),List.of(),List.of())).isEmpty());
    }

    @Test void unrelatedAndClosedThreadsAreExcludedEvenOnGenericResume() {
        var unrelated=thread("Owner was building a tower.");
        var closed=new OrganicPetContext.ThreadCue("closed","Owner was building a bridge.","COMPLETED","",2);
        var context=context(List.of(),List.of(),List.of(unrelated,closed));
        assertFalse(prompt(context,"Continue the bridge.").contains("id=thread-bridge"));
        assertFalse(prompt(context,"Continue the bridge.").contains("id=closed"));
    }

    @Test void recentQuestionCooldownAndQuietBoundaryArePromptGuidance() {
        var context=withTurns(context(List.of(),List.of(),List.of()),List.of(
                new DialogueTurn(DialogueTurn.Role.PET,"Which bridge?",NOW.minusSeconds(2)),
                new DialogueTurn(DialogueTurn.Role.OWNER,"No questions for a bit.",NOW.minusSeconds(1))));
        DialoguePrompt built=new DialoguePromptBuilder(limits(),new WordCounter()).build(context,"Thanks.");
        assertTrue(built.text().contains("You don't have to agree, answer everything, or ask questions back."));
        assertTrue(built.text().contains("If your owner wants quiet or space, give it."));
        // The live exchange is sent as real chat turns, starting with the owner.
        assertEquals(List.of("No questions for a bit."), built.chatHistory().stream().map(DialogueTurn::text).toList());
    }

    @Test void selfModelStillNeedsRealPetActionEvidenceWithoutRewritingTheReply() {
        var action=event("ACTION_COMPLETED: Pet completed EXPLORE_NEARBY reason=CURIOSITY");
        var raw=new DialogueModelOutput("I'm interested in exploring nearby.",DialogueImportance.MEDIUM,Optional.empty(),
                zeros(TraitName.class),zeros(MoodDimension.class),PetDialogueAction.NONE,PetDialogueAttention.CURIOUS,
                DialogueAffect.NONE,RelationshipSignal.none(),0,
                new DialogueSelfModelCandidate(DialogueSelfModelCandidate.Kind.INTEREST,"nearby exploration",.8),OwnerInterpretationCandidate.none());
        var mapped=new DialogueResponseMapper(limits()).map(raw,"Do you like exploring?",context(List.of(action),List.of(),List.of()));
        assertEquals(raw.reply(),mapped.reply());
        assertEquals(Set.of(action.eventId()),mapped.selfModelCandidate().supportingEventIds());
        assertFalse(new DialogueResponseMapper(limits()).map(raw,"Do you like exploring?",context(List.of(),List.of(),List.of()))
                .selfModelCandidate().meaningful());
    }

    private static DialogueModelOutput output(String reply, PetDialogueAction action) {
        return new DialogueModelOutput(reply,DialogueImportance.MEDIUM,Optional.empty(),zeros(TraitName.class),zeros(MoodDimension.class),
                action,PetDialogueAttention.CALM,DialogueAffect.CALMER,new RelationshipSignal(RelationshipSignal.Type.REPAIR,1),1);
    }

    private static DialogueContext withTurns(DialogueContext context,List<DialogueTurn> turns) {
        return new DialogueContext(context.pet(),false,true,context.shortTermMemories(),context.longTermMemories(),
                turns,context.gameContext(),NOW,context.relationshipEvidence(),context.organicContext());
    }
    private static DialogueContext withSelfModelCues(DialogueContext base,List<String> cues) {
        OrganicPetContext organic=new OrganicPetContext(BehavioralAffect.neutral(base.now()),cues,
                List.of(),Optional.empty(),Optional.empty());
        return new DialogueContext(base.pet(),base.sleeping(),base.aiAccessEnabled(),base.shortTermMemories(),
                base.longTermMemories(),base.recentTurns(),base.gameContext(),base.now(),
                base.relationshipEvidence(),organic);
    }
    private static String prompt(DialogueContext context,String message) {
        return new DialoguePromptBuilder(limits(),new WordCounter())
                .build(context,message).text();
    }
    private static int questionCount(String text) { return (int)text.chars().filter(ch->ch=='?').count(); }
    private static ShortTermMemorySnippet event(String summary) {
        return new ShortTermMemorySnippet(UUID.randomUUID(),DialogueImportance.MEDIUM,summary,NOW,
                Optional.empty(),.8);
    }
    private static OrganicPetContext.ThreadCue thread(String summary) {
        return new OrganicPetContext.ThreadCue("thread-bridge",summary,"OPEN","",1,"OWNER_REPORTED",
                Optional.of(UUID.randomUUID()),NOW,"dev","minecraft:overworld");
    }
    private static LongTermMemoryCard longMemory(String text, MemoryKind kind, MemoryProvenance provenance,
            int salience) {
        return new LongTermMemoryCard(UUID.randomUUID(), PET_ID, 1, text,
                MemoryImportance.HIGH, Set.of(), Set.of("bridge"), Set.of(), true, NOW, provenance,
                kind, .9, salience, 0, Optional.empty());
    }
    private static DialogueContext context(List<ShortTermMemorySnippet> memories,
            List<LongTermMemorySnippet> longTerm,List<OrganicPetContext.ThreadCue> threads) {
        Pet pet = new Pet(PET_ID,UUID.randomUUID(),"Mica",
                PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.7,1L,AppearanceRules.defaults()),
                new PetTraits(50,50,50,50,50,50,50,50,"",0,NOW),
                PetMood.initial(50,50,50,50,NOW),
                PlacedPlacement.materialized(new BackendId("dev"),DimensionId.parse("minecraft:overworld"),
                        new WorldPosition(0,64,0),UUID.randomUUID()),0,NOW,NOW);
        return new DialogueContext(pet,false,true,memories,longTerm,List.of(),
                new DialogueGameContext("dev","minecraft:overworld","dialogue",
                        new PetSceneSnapshot("unknown","unknown","UNKNOWN",false,"UNKNOWN","UNKNOWN",
                                "UNKNOWN","RAIN",false)),NOW,List.of(),
                new OrganicPetContext(BehavioralAffect.neutral(NOW),List.of(),threads,Optional.empty(),Optional.empty()));
    }
    private static DialogueLimits limits() {
        return new DialogueLimits(500,500,4000,250,1200,600,700,4,java.time.Duration.ofSeconds(5),
                10,8,java.time.Duration.ofSeconds(10),BigDecimal.TEN,BigDecimal.valueOf(100),5,
                java.time.Duration.ofMinutes(1));
    }
    private static <E extends Enum<E>> Map<E,Integer> zeros(Class<E> type) {
        Map<E,Integer> result=new EnumMap<>(type); for(E value:type.getEnumConstants())result.put(value,0); return result;
    }
    private static final class WordCounter implements DialogueTokenCounter {
        @Override public int count(String text) { return text.isBlank()?0:text.split("\\s+").length; }
        @Override public String truncate(String text,int maximumTokens) { return text; }
    }
}
