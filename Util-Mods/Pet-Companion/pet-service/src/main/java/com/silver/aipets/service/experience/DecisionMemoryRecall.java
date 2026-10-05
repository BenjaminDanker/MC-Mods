package com.silver.aipets.service.experience;

import java.util.Optional;
import java.util.UUID;

/** Bounded, pet-scoped memory lookup at a decision point, never on a game tick. */
public interface DecisionMemoryRecall {
    DecisionMemoryRecall NONE = (petId, type, summary) -> Optional.empty();
    Optional<Cue> find(UUID petId, String type, String summary);
    record Cue(UUID memoryId, String provenance, String text) { }
}
