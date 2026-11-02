package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.Pet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A bounded, owner-reported unfinished activity eligible for one quiet follow-up. */
public interface OpenThreadRecall {
    OpenThreadRecall NONE = (pet, type, summary, now) -> Optional.empty();

    Optional<ThreadCue> find(Pet pet, String type, String summary, Instant now);

    record ThreadCue(UUID memoryId, String subject) { }

    default void followedUp(UUID petId, UUID memoryId, Instant now) { }
}
