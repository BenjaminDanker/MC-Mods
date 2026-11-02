package com.silver.aipets.service.dialogue;

import java.util.List;
import java.util.Objects;

/** Small cue-selected durable context; raw self-model JSON is never sent to Luna. */
public record OrganicPetContext(BehavioralAffect affect, List<String> selfModelCues,
        List<ThreadCue> openThreads, java.util.Optional<OwnerInterpretation> ownerInterpretation,
        java.util.Optional<IntentionCue> currentIntention) {
    public OrganicPetContext {
        Objects.requireNonNull(affect);
        selfModelCues = List.copyOf(selfModelCues);
        openThreads = List.copyOf(openThreads);
        Objects.requireNonNull(ownerInterpretation);
        Objects.requireNonNull(currentIntention);
    }
    public static OrganicPetContext empty(java.time.Instant at) {
        return new OrganicPetContext(BehavioralAffect.neutral(at), List.of(), List.of(),
                java.util.Optional.empty(),java.util.Optional.empty());
    }
    public record ThreadCue(String id, String summary, String state, String locationCue,
            long version, String sourceKind, java.util.Optional<java.util.UUID> sourceEventId,
            java.time.Instant createdAt, String locationServer, String locationDimension) {
        public ThreadCue(String id, String summary, String state, String locationCue, long version) {
            this(id, summary, state, locationCue, version, "UNKNOWN", java.util.Optional.empty(),
                    java.time.Instant.EPOCH, "", "");
        }
        public ThreadCue {
            java.util.Objects.requireNonNull(sourceKind);
            java.util.Objects.requireNonNull(sourceEventId);
            java.util.Objects.requireNonNull(createdAt);
        }
    }
    public record IntentionCue(String kind,String motivationMemoryId,java.time.Instant expiresAt,long version) { }
}
