package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.dialogue.DialogueUsage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable job/event/state boundary; commit must be one transaction. */
public interface ConsolidationRepository {
    EnqueueResult enqueue(ConsolidationJob proposed);

    List<ConsolidationJob> claimDue(String workerId, Instant now, Duration lease, int limit);

    Optional<Pet> findPet(UUID petId);

    List<ConsolidationEvent> pendingEvents(UUID petId);

    void completeWithoutModel(
            ConsolidationJob job, String workerId, List<UUID> discardedEventIds, Instant at);

    ConsolidationCommitResult commit(
            ConsolidationJob job,
            String workerId,
            CandidateSelection selection,
            ValidatedConsolidationOutput output,
            DialogueUsage usage,
            Instant at);

    void recordUsage(DialogueUsage usage);

    void retry(
            ConsolidationJob job, String workerId, Instant attemptedAt,
            Instant notBefore, String errorCategory);

    void failed(
            ConsolidationJob job, String workerId, Instant at, String errorCategory);

    Optional<ConsolidationJob> findByKey(String idempotencyKey);

    record EnqueueResult(ConsolidationJob job, boolean created) {
        public EnqueueResult {
            java.util.Objects.requireNonNull(job, "job");
        }
    }
}
