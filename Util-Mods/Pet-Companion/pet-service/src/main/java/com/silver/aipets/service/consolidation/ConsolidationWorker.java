package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialogueUsage;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Durable lease/retry consolidation worker; it deliberately has no sleep-state write access. */
public final class ConsolidationWorker {
    private final ConsolidationConfig config;
    private final ConsolidationRepository repository;
    private final SubscriptionAccess subscriptions;
    private final ConsolidationCandidateSelector selector;
    private final ConsolidationPromptBuilder prompts;
    private final ConsolidationModelClient model;
    private final ConsolidationOutputCodec codec;
    private final ConsolidationOutputValidator validator;
    private final Consumer<LongTermMemoryCard> embeddingEnqueuer;
    private final Clock clock;
    private final Supplier<UUID> jobIds;
    private final Supplier<UUID> requestIds;
    private final BooleanSupplier aiEnabled;

    public ConsolidationWorker(
            ConsolidationConfig config,
            ConsolidationRepository repository,
            SubscriptionAccess subscriptions,
            ConsolidationCandidateSelector selector,
            ConsolidationPromptBuilder prompts,
            ConsolidationModelClient model,
            ConsolidationOutputCodec codec,
            ConsolidationOutputValidator validator,
            Consumer<LongTermMemoryCard> embeddingEnqueuer,
            Clock clock,
            Supplier<UUID> jobIds,
            Supplier<UUID> requestIds) {
        this(
                config, repository, subscriptions, selector, prompts, model, codec, validator,
                embeddingEnqueuer, clock, jobIds, requestIds, () -> true);
    }

    public ConsolidationWorker(
            ConsolidationConfig config,
            ConsolidationRepository repository,
            SubscriptionAccess subscriptions,
            ConsolidationCandidateSelector selector,
            ConsolidationPromptBuilder prompts,
            ConsolidationModelClient model,
            ConsolidationOutputCodec codec,
            ConsolidationOutputValidator validator,
            Consumer<LongTermMemoryCard> embeddingEnqueuer,
            Clock clock,
            Supplier<UUID> jobIds,
            Supplier<UUID> requestIds,
            BooleanSupplier aiEnabled) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.model = Objects.requireNonNull(model, "model");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.embeddingEnqueuer = Objects.requireNonNull(embeddingEnqueuer, "embeddingEnqueuer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jobIds = Objects.requireNonNull(jobIds, "jobIds");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.aiEnabled = Objects.requireNonNull(aiEnabled, "aiEnabled");
    }

    public ConsolidationRepository.EnqueueResult enqueueAtSleepStart(
            UUID petId, UUID sleepCycleId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(sleepCycleId, "sleepCycleId");
        return repository.enqueue(ConsolidationJob.pending(
                Objects.requireNonNull(jobIds.get(), "jobIds returned null"),
                petId, sleepCycleId, clock.instant()));
    }

    public int processDue(String workerId, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        if (workerId.isBlank() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid consolidation worker request");
        }
        if (!aiEnabled.getAsBoolean()) return 0;
        Instant now = clock.instant();
        List<ConsolidationJob> claimed = repository.claimDue(
                workerId, now, config.leaseDuration(), limit);
        for (ConsolidationJob job : claimed) processOne(workerId, job, now);
        return claimed.size();
    }

    private void processOne(String workerId, ConsolidationJob job, Instant now) {
        Pet pet = repository.findPet(job.petId()).orElse(null);
        if (pet == null) {
            repository.failed(job, workerId, now, "PetNotFound");
            return;
        }
        // An inactive entitlement makes no provider call and preserves pending events.
        if (!subscriptions.canAdopt(pet.ownerUuid())) {
            repository.completeWithoutModel(job, workerId, List.of(), now);
            return;
        }
        CandidateSelection initial = selector.select(job.petId(), repository.pendingEvents(job.petId()));
        if (initial.selected().isEmpty()) {
            repository.completeWithoutModel(job, workerId, initial.discardedEventIds(), now);
            return;
        }
        ConsolidationPrompt prompt;
        try {
            prompt = prompts.build(pet, initial);
        } catch (RuntimeException failure) {
            reschedule(job, workerId, now, failure);
            return;
        }
        UUID requestId = Objects.requireNonNull(requestIds.get(), "requestIds returned null");
        DialogueModelResponse response;
        try {
            response = model.complete(
                    requestId, prompt.prompt(), ConsolidationStructuredSchema.JSON_SCHEMA,
                    config.modelTimeout());
        } catch (RuntimeException failure) {
            repository.recordUsage(failedUsage(requestId, pet, now, failure));
            reschedule(job, workerId, now, failure);
            return;
        }
        ValidatedConsolidationOutput output;
        try {
            if (response.inputTokens() > config.maximumInputTokens()) {
                throw new ConsolidationOutputException("Provider input usage exceeded the hard cap");
            }
            ConsolidationModelOutput decoded = codec.decode(response.structuredJson());
            output = validator.validate(decoded, prompt.selection());
        } catch (RuntimeException failure) {
            repository.recordUsage(responseUsage(
                    requestId, pet, response, now,
                    DialogueUsage.Status.REJECTED, Optional.of("INVALID_OUTPUT")));
            reschedule(job, workerId, now, failure);
            return;
        }
        DialogueUsage usage = responseUsage(
                requestId, pet, response, now, DialogueUsage.Status.SUCCEEDED, Optional.empty());
        try {
            ConsolidationCommitResult committed = repository.commit(
                    job, workerId, prompt.selection(), output, usage, now);
            for (LongTermMemoryCard card : committed.cards()) {
                try {
                    embeddingEnqueuer.accept(card);
                } catch (RuntimeException ignored) {
                    // SQL remains authoritative with embedding_status=PENDING; reindex can recover.
                }
            }
        } catch (RuntimeException failure) {
            repository.recordUsage(usage);
            reschedule(job, workerId, now, failure);
        }
    }

    private void reschedule(
            ConsolidationJob job, String workerId, Instant now, RuntimeException failure) {
        String category = sanitize(failure);
        if (job.attemptCount() >= config.maximumAttempts()) {
            repository.failed(job, workerId, now, category);
        } else {
            repository.retry(job, workerId, now,
                    now.plus(retryDelay(job.attemptCount())), category);
        }
    }

    private DialogueUsage failedUsage(
            UUID requestId, Pet pet, Instant at, RuntimeException failure) {
        return new DialogueUsage(
                requestId, requestId, pet.petId(), pet.ownerUuid(), model.model(),
                Optional.empty(), 0, 0, 0, BigDecimal.ZERO, 0,
                DialogueUsage.Status.FAILED, Optional.of(sanitize(failure)), at);
    }

    private DialogueUsage responseUsage(
            UUID requestId,
            Pet pet,
            DialogueModelResponse response,
            Instant at,
            DialogueUsage.Status status,
            Optional<String> category) {
        return new DialogueUsage(
                requestId, requestId, pet.petId(), pet.ownerUuid(), model.model(),
                response.providerId(), response.inputTokens(), response.cachedInputTokens(),
                response.outputTokens(), response.estimatedCost(), response.latencyMillis(),
                status, category, at);
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(0, attemptCount - 1), 10);
        return config.initialRetryDelay().multipliedBy(multiplier);
    }

    private static String sanitize(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        return type.isBlank() ? "RuntimeException" : type.substring(0, Math.min(type.length(), 128));
    }
}
