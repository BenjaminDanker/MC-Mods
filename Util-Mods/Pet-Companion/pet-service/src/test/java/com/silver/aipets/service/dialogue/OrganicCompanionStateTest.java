package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.PetDialogueAction;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OrganicCompanionStateTest {
    private static final Instant NOW=Instant.parse("2026-09-28T12:00:00Z");
    private static PetTraits traits(int security) { return new PetTraits(50,50,50,50,50,50,50,security,"",0,NOW); }

    @Test void behavioralAffectIsBoundedTemperedAndDecays() {
        BehavioralAffect quiet=BehavioralAffect.neutral(NOW);
        BehavioralAffect wary=quiet.apply(DialogueAffect.WARY,
                new RelationshipSignal(RelationshipSignal.Type.BOUNDARY,1),traits(35),.8,
                java.util.UUID.randomUUID(),NOW.plusSeconds(1));
        assertTrue(wary.valence()<0); assertTrue(wary.alertness()>0); assertTrue(wary.socialStance()<0);
        BehavioralAffect faded=wary.decay(NOW.plusSeconds(7*24*60*60));
        assertTrue(Math.abs(faded.valence())<Math.abs(wary.valence()));
        assertTrue(Math.abs(faded.socialStance())<Math.abs(wary.socialStance()));
        assertEquals(0,quiet.valence());
    }

    @Test void selfModelRequiresRepeatedConsolidatedEvidenceAndDeduplicates() {
        assertTrue(PetSelfModel.fromConsolidatedEvidence("Pet often returns to riverside paths.",3,1).isEmpty());
        assertTrue(PetSelfModel.fromConsolidatedEvidence("Pet often returns to riverside paths.",2,2).isEmpty());
        var evidence=PetSelfModel.fromConsolidatedEvidence("Pet often returns to riverside paths.",3,2).orElseThrow();
        String model=PetSelfModel.add(null,evidence);
        assertTrue(model.contains("riverside paths"));
        assertSame(model,PetSelfModel.add(model,evidence));
    }

    @Test void selfModelCandidateNeedsThreeDistinctInteractionsAcrossTwoDaysAndOwnerReportsStaySeparate() {
        UUID event1=UUID.randomUUID(),event2=UUID.randomUUID(),event3=UUID.randomUUID();
        var candidate1=new DialogueSelfModelCandidate(DialogueSelfModelCandidate.Kind.PREFERENCE,
                "quiet hill paths",.8,Set.of(event1));
        UUID first=UUID.randomUUID(), second=UUID.randomUUID(), third=UUID.randomUUID();
        String pending=PetSelfModel.recordCandidate(null,candidate1,first,NOW,false);
        pending=PetSelfModel.recordCandidate(pending,candidate1,UUID.randomUUID(),NOW,false);
        pending=PetSelfModel.promoteEligible(pending);
        assertFalse(pending.contains("\"likes\":[\"quiet hill paths\"]"));
        pending=PetSelfModel.recordCandidate(pending,new DialogueSelfModelCandidate(candidate1.kind(),candidate1.value(),
                candidate1.confidence(),Set.of(event2)),second,NOW.plusSeconds(60),false);
        pending=PetSelfModel.promoteEligible(pending);
        assertFalse(pending.contains("\"likes\":[\"quiet hill paths\"]"));
        pending=PetSelfModel.recordCandidate(pending,new DialogueSelfModelCandidate(candidate1.kind(),candidate1.value(),
                candidate1.confidence(),Set.of(event3)),third,NOW.plusSeconds(86400),false);
        String promoted=PetSelfModel.promoteEligible(pending);
        assertTrue(promoted.contains("quiet hill paths"));
        assertFalse(promoted.contains("interaction_ids"));
        assertEquals(pending,PetSelfModel.recordCandidate(pending,candidate1,UUID.randomUUID(),NOW,true));
    }

    @Test void selfModelCountsDistinctWitnessedActionsAndKeepsVoiceStable() {
        String prior="{\"likes\":[],\"dislikes\":[],\"interests\":[],\"activities\":[],"
                +"\"avoidances\":[],\"goals\":[],\"voice\":[\"reserved, dry humor\"]}";
        var candidate=new DialogueSelfModelCandidate(DialogueSelfModelCandidate.Kind.INTEREST,
                "nearby exploration",.72,Set.of());
        UUID event1=UUID.randomUUID(), event2=UUID.randomUUID(), event3=UUID.randomUUID();
        String one=PetSelfModel.recordCandidate(prior,new DialogueSelfModelCandidate(candidate.kind(),candidate.value(),
                candidate.confidence(),Set.of(event1)),UUID.randomUUID(),NOW,false);
        assertFalse(PetSelfModel.promoteEligible(one).contains("\"interests\":[\"nearby exploration\"]"));
        String duplicate=PetSelfModel.recordCandidate(one,new DialogueSelfModelCandidate(candidate.kind(),candidate.value(),
                candidate.confidence(),Set.of(event1)),UUID.randomUUID(),NOW.plusSeconds(86400),false);
        assertEquals(one,duplicate,"the same witnessed event cannot gain a second interaction/day vote");
        String two=PetSelfModel.recordCandidate(duplicate,new DialogueSelfModelCandidate(candidate.kind(),candidate.value(),
                candidate.confidence(),Set.of(event2)),UUID.randomUUID(),NOW.plusSeconds(86400),false);
        assertFalse(PetSelfModel.promoteEligible(two).contains("\"interests\":[\"nearby exploration\"]"));
        String three=PetSelfModel.recordCandidate(two,new DialogueSelfModelCandidate(candidate.kind(),candidate.value(),
                candidate.confidence(),Set.of(event3)),UUID.randomUUID(),NOW.plusSeconds(2*86400),false);
        String promoted=PetSelfModel.promoteEligible(three);
        assertTrue(promoted.contains("\"interests\":[\"nearby exploration\"]"));
        assertTrue(promoted.contains("reserved, dry humor"));
        assertFalse(promoted.contains("temperament"));
        assertEquals(prior,PetSelfModel.recordCandidate(prior,candidate,UUID.randomUUID(),NOW,false),
                "a candidate without cited action events is ignored");
        assertEquals(prior,PetSelfModel.recordCandidate(prior,new DialogueSelfModelCandidate(candidate.kind(),
                candidate.value(),candidate.confidence(),Set.of(event1)),UUID.randomUUID(),NOW,true),
                "owner reports are not evidence of a pet preference");
    }

    @Test void affectUsesIndependentHalfLivesBoundedAlertnessAndIdempotentEvents() {
        BehavioralAffect state=new BehavioralAffect(80,80,80,80,80,80,"TEST",null,NOW);
        BehavioralAffect afterTwoHours=state.decay(NOW.plusSeconds(2*60*60));
        assertEquals(76,afterTwoHours.valence());
        assertEquals(40,afterTwoHours.alertness());
        assertEquals(79,afterTwoHours.socialStance());
        BehavioralAffect afterDay=state.decay(NOW.plusSeconds(24*60*60));
        assertEquals(40,afterDay.valence());
        assertEquals(0,afterDay.alertness());
        assertEquals(72,afterDay.socialStance());

        BehavioralAffect bounded=new BehavioralAffect(100,100,100,100,100,100,"TEST",null,NOW);
        UUID event=UUID.randomUUID();
        BehavioralAffect applied=bounded.apply(DialogueAffect.BRIGHTER,2,
                RelationshipSignal.none(),traits(100),1,UUID.randomUUID(),event,NOW.plusSeconds(1));
        assertTrue(applied.valence()<=100); assertTrue(applied.alertness()<=100); assertTrue(applied.socialStance()<=100);
        assertEquals(applied,applied.apply(DialogueAffect.BRIGHTER,2,
                RelationshipSignal.none(),traits(100),1,UUID.randomUUID(),event,NOW.plusSeconds(2)));
        BehavioralAffect corrected=applied.apply(DialogueAffect.WARY,2,
                new RelationshipSignal(RelationshipSignal.Type.BOUNDARY,2),traits(20),1,
                UUID.randomUUID(),UUID.randomUUID(),NOW.plusSeconds(3));
        assertTrue(corrected.valence()<applied.valence());
        assertTrue(corrected.socialStance()<applied.socialStance());
    }

    @Test void openThreadTransitionsAreVersionedAndAllowPauseResumeCompleteForget() {
        var open=new OpenThreadStateMachine.ThreadState(OpenThreadStateMachine.State.OPEN,NOW,1,NOW);
        var active=OpenThreadStateMachine.transition(open,OpenThreadStateMachine.Event.CUE_FOUND,1,NOW.plusSeconds(60));
        assertEquals(OpenThreadStateMachine.State.ACTIVE,active.state());
        assertEquals(active,OpenThreadStateMachine.transition(active,OpenThreadStateMachine.Event.INTERRUPTED,1,NOW.plusSeconds(70)));
        var paused=OpenThreadStateMachine.transition(active,OpenThreadStateMachine.Event.INTERRUPTED,2,NOW.plusSeconds(70));
        assertEquals(OpenThreadStateMachine.State.PAUSED,paused.state());
        var resumed=OpenThreadStateMachine.transition(paused,OpenThreadStateMachine.Event.RESUMED,3,NOW.plusSeconds(80));
        assertEquals(OpenThreadStateMachine.State.ACTIVE,resumed.state());
        var completed=OpenThreadStateMachine.transition(resumed,OpenThreadStateMachine.Event.OWNER_COMPLETED,4,NOW.plusSeconds(90));
        assertEquals(OpenThreadStateMachine.State.COMPLETED,completed.state());
        assertEquals(completed,OpenThreadStateMachine.transition(completed,OpenThreadStateMachine.Event.CUE_FOUND,
                completed.version(),NOW.plusSeconds(95)),"completed work cannot be resurrected by a matching cue");
        assertEquals(OpenThreadStateMachine.State.FORGOTTEN,
                OpenThreadStateMachine.transition(open,OpenThreadStateMachine.Event.EXPIRED,1,NOW.plusSeconds(100)).state());
        assertEquals(OpenThreadStateMachine.State.FORGOTTEN,
                OpenThreadStateMachine.transition(open,OpenThreadStateMachine.Event.ABANDONED,1,NOW.plusSeconds(100)).state());
        assertEquals(OpenThreadStateMachine.State.ACTIVE,
                OpenThreadStateMachine.transition(active,OpenThreadStateMachine.Event.EXPIRED,2,NOW.plusSeconds(100)).state());
        assertEquals(OpenThreadStateMachine.State.PAUSED,
                OpenThreadStateMachine.transition(paused,OpenThreadStateMachine.Event.CUE_FOUND,3,NOW.plusSeconds(100)).state());
    }

    @Test void socialInterpretationRemainsTentativeAndCorrectionsClearIt() {
        var inferred=OwnerInterpretationMapper.infer("I'm worried about the bridge; could you look?",NOW);
        assertTrue(inferred.confidence()<.7);
        assertEquals(Optional.of("CONCERNED"),inferred.emotion());
        var corrected=OwnerInterpretationMapper.infer("No, I meant the other bridge.",NOW);
        assertTrue(corrected.intent().isEmpty()); assertTrue(corrected.emotion().isEmpty());
    }

    @Test void modelOwnerInterpretationIsTemporaryCorrectableAndNotPromotedToMemory() {
        String json="{\"reply\":\"Do you mean the spruce bridge?\",\"action\":\"NONE\","
                +"\"attention\":\"QUIET\",\"memory_candidate\":null,\"affect_delta\":\"NONE\","
                +"\"affect_strength\":0,\"relationship_event\":{\"type\":\"NONE\",\"strength\":0},"
                +"\"conversation_mode\":\"ASK\",\"self_model_candidate\":{\"kind\":\"NONE\","
                +"\"value\":null,\"confidence\":0},\"owner_interpretation\":{\"intent\":\"RETURN_TO_PLACE\","
                +"\"emotion\":\"UNCERTAIN\",\"confidence\":0.42}}";
        var decoded=new DialogueOutputCodec().decode(json);
        assertEquals(.42,decoded.ownerInterpretation().confidence());
        var output=new DialogueResponseMapper(new DialogueLimits(500,300,4000,250,1200,600,700,4,
                java.time.Duration.ofSeconds(5),10,8,java.time.Duration.ofSeconds(10),
                java.math.BigDecimal.TEN,java.math.BigDecimal.valueOf(100),5,java.time.Duration.ofMinutes(1)))
                .map(decoded,"I've been thinking about going back to that place.");
        assertTrue(output.ownerInterpretation().meaningful());
        assertTrue(output.memoryCandidate().isEmpty());
        assertFalse(output.selfModelCandidate().meaningful());
        var correction=new DialogueResponseMapper(new DialogueLimits(500,300,4000,250,1200,600,700,4,
                java.time.Duration.ofSeconds(5),10,8,java.time.Duration.ofSeconds(10),
                java.math.BigDecimal.TEN,java.math.BigDecimal.valueOf(100),5,java.time.Duration.ofMinutes(1)))
                .map(decoded,"No, I meant the other place.");
        assertFalse(correction.ownerInterpretation().meaningful());

        var stored=new OwnerInterpretation(output.ownerInterpretation().intent(),
                output.ownerInterpretation().emotion(),output.ownerInterpretation().confidence(),NOW);
        assertTrue(stored.activeAt(NOW.plusSeconds(23*3600),2));
        assertFalse(stored.activeAt(NOW.plusSeconds(23*3600),3));
        assertFalse(stored.activeAt(NOW.plusSeconds(24*3600),0));
    }

    private static java.util.Map<com.silver.aipets.common.domain.TraitName,Integer> zeroTraits() {
        var result=new java.util.EnumMap<com.silver.aipets.common.domain.TraitName,Integer>(com.silver.aipets.common.domain.TraitName.class);
        for(var trait:com.silver.aipets.common.domain.TraitName.values()) result.put(trait,0);
        return result;
    }
    private static java.util.Map<com.silver.aipets.common.domain.MoodDimension,Integer> zeroMood() {
        var result=new java.util.EnumMap<com.silver.aipets.common.domain.MoodDimension,Integer>(com.silver.aipets.common.domain.MoodDimension.class);
        for(var mood:com.silver.aipets.common.domain.MoodDimension.values()) result.put(mood,0);
        return result;
    }
}
