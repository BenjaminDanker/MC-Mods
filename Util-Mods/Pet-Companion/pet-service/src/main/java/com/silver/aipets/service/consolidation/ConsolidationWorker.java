package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialogueUsage;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.billing.AiBudgetService;
import com.silver.aipets.service.billing.AiPricing;

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
    private final java.util.function.BiConsumer<UUID,UUID> vectorDeleter;
    private final Clock clock;
    private final Supplier<UUID> jobIds;
    private final Supplier<UUID> requestIds;
    private final BooleanSupplier aiEnabled;
    private final AiBudgetService budget;
    private final AiPricing pricing;

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
        this(config, repository, subscriptions, selector, prompts, model, codec, validator,
                embeddingEnqueuer, clock, jobIds, requestIds, aiEnabled, AiBudgetService.UNLIMITED);
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
            BooleanSupplier aiEnabled,
            AiBudgetService budget) {
        this(config, repository, subscriptions, selector, prompts, model, codec, validator,
                embeddingEnqueuer, clock, jobIds, requestIds, aiEnabled, budget,
                (petId,memoryId) -> { });
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
            BooleanSupplier aiEnabled,
            AiBudgetService budget,
            java.util.function.BiConsumer<UUID,UUID> vectorDeleter) {
        this(config, repository, subscriptions, selector, prompts, model, codec, validator,
                embeddingEnqueuer, clock, jobIds, requestIds, aiEnabled, budget,
                vectorDeleter, AiPricing.defaults());
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
            BooleanSupplier aiEnabled,
            AiBudgetService budget,
            java.util.function.BiConsumer<UUID,UUID> vectorDeleter,
            AiPricing pricing) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.model = Objects.requireNonNull(model, "model");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.embeddingEnqueuer = Objects.requireNonNull(embeddingEnqueuer, "embeddingEnqueuer");
        this.vectorDeleter = Objects.requireNonNull(vectorDeleter, "vectorDeleter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jobIds = Objects.requireNonNull(jobIds, "jobIds");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.aiEnabled = Objects.requireNonNull(aiEnabled, "aiEnabled");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
    }

    public ConsolidationRepository.EnqueueResult enqueueAtSleepStart(
            UUID petId, UUID sleepCycleId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(sleepCycleId, "sleepCycleId");
        return repository.enqueue(ConsolidationJob.pending(
                Objects.requireNonNull(jobIds.get(), "jobIds returned null"),
                petId, sleepCycleId, clock.instant()));
    }

    public boolean hasMeaningfulPendingEvents(UUID petId) {
        return !selector.select(petId, repository.pendingEvents(petId)).selected().isEmpty();
    }

    public int processDue(String workerId, int limit) {
        return processDue(java.util.Optional.empty(), workerId, limit);
    }

    /** Operator diagnostics can use the real worker without claiming unrelated test jobs. */
    public int processDueForPet(UUID petId, String workerId, int limit) {
        return processDue(java.util.Optional.of(Objects.requireNonNull(petId, "petId")), workerId, limit);
    }

    private int processDue(java.util.Optional<UUID> petId, String workerId, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        if (workerId.isBlank() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid consolidation worker request");
        }
        if (!aiEnabled.getAsBoolean()) return 0;
        Instant now = clock.instant();
        List<ConsolidationJob> claimed = petId.isPresent()
                ? repository.claimDueForPet(petId.orElseThrow(), workerId, now, config.leaseDuration(), limit)
                : repository.claimDue(workerId, now, config.leaseDuration(), limit);
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
        AiBudgetService.Reservation reservation = budget.tryReserve(
                pet.ownerUuid(), pricing.maximumDialogueCost(
                        config.maximumInputTokens(), 800), now).orElse(null);
        if (reservation == null) {
            repository.retry(job, workerId, now, now.plus(Duration.ofHours(1)),
                    "AI_BUDGET_EXHAUSTED");
            return;
        }
        UUID requestId;
        try {
            requestId = Objects.requireNonNull(requestIds.get(), "requestIds returned null");
        } catch (RuntimeException failure) {
            budget.settle(reservation, BigDecimal.ZERO);
            reschedule(job, workerId, now, failure);
            return;
        }
        DialogueModelResponse response;
        try {
            response = model.complete(
                    requestId, prompt.prompt(), ConsolidationStructuredSchema.JSON_SCHEMA,
                    config.modelTimeout());
        } catch (RuntimeException failure) {
            String validation = "result=failed configured_completion_limit=2000 finish_reason=unavailable"
                    + " token_limit_hit=false provider_request_id=<unavailable> http_elapsed_ms=unavailable"
                    + " failure_category=" + failure.getClass().getSimpleName();
            com.silver.aipets.service.dialogue.DialogueProviderDiagnostics.emit(
                    "Consolidation schema validation: " + validation);
            com.silver.aipets.service.dialogue.DialogueDiagnostics.event(
                    "consolidation_schema_validation", requestId, validation);
            repository.recordUsage(failedUsage(requestId, pet, now, failure));
            reschedule(job, workerId, now, failure);
            budget.settle(reservation, BigDecimal.ZERO);
            return;
        }
        if (!response.completedNormally()) {
            String validation = providerValidation(response, "failed")
                    + " failure_category=INCOMPLETE_RESPONSE";
            com.silver.aipets.service.dialogue.DialogueProviderDiagnostics.emit(
                    "Consolidation schema validation: " + validation);
            com.silver.aipets.service.dialogue.DialogueDiagnostics.event(
                    "consolidation_schema_validation", requestId,
                    validation);
            repository.recordUsage(responseUsage(requestId, pet, response, now,
                    DialogueUsage.Status.REJECTED, Optional.of("INCOMPLETE_RESPONSE")));
            reschedule(job, workerId, now,
                    new ConsolidationOutputException("Provider did not complete the consolidation response"));
            budget.settle(reservation, response.estimatedCost());
            return;
        }
        ValidatedConsolidationOutput output;
        try {
            if (response.inputTokens() > config.maximumInputTokens()) {
                throw new ConsolidationOutputException("Provider input usage exceeded the hard cap");
            }
            ConsolidationModelOutput decoded = codec.decode(response.structuredJson());
            output = validator.validate(decoded, prompt.selection());
            String validation = providerValidation(response, "passed");
            com.silver.aipets.service.dialogue.DialogueProviderDiagnostics.emit(
                    "Consolidation schema validation: " + validation);
            com.silver.aipets.service.dialogue.DialogueDiagnostics.event(
                    "consolidation_schema_validation", requestId,
                    validation);
        } catch (RuntimeException failure) {
            String validation = providerValidation(response, "failed")
                    + " failure_category=" + failure.getClass().getSimpleName();
            com.silver.aipets.service.dialogue.DialogueProviderDiagnostics.emit(
                    "Consolidation schema validation: " + validation);
            com.silver.aipets.service.dialogue.DialogueDiagnostics.event(
                    "consolidation_schema_validation", requestId,
                    validation);
            if (failure instanceof ConsolidationOutputException) {
                output = GroundedEpisodeFallback.from(pet, prompt.selection(), validator)
                        .orElse(null);
                if (output != null) {
                    ConsolidationDiagnostics.note("All generated cards failed grounding; "
                            + "preserving one verbatim witnessed HIGH episode.");
                } else {
                    repository.recordUsage(responseUsage(
                            requestId, pet, response, now,
                            DialogueUsage.Status.REJECTED, Optional.of("INVALID_OUTPUT")));
                    reschedule(job, workerId, now, failure);
                    budget.settle(reservation, response.estimatedCost());
                    return;
                }
            } else {
            repository.recordUsage(responseUsage(
                    requestId, pet, response, now,
                    DialogueUsage.Status.REJECTED, Optional.of("INVALID_OUTPUT")));
            reschedule(job, workerId, now, failure);
            budget.settle(reservation, response.estimatedCost());
            return;
            }
        }
        DialogueUsage usage = responseUsage(
                requestId, pet, response, now, DialogueUsage.Status.SUCCEEDED, Optional.empty());
        try {
            ConsolidationCommitResult committed = repository.commit(
                    job, workerId, prompt.selection(), output, usage, now);
            for (UUID memoryId : committed.retiredMemoryIds()) {
                try { vectorDeleter.accept(job.petId(), memoryId); }
                catch (RuntimeException ignored) {
                    // SQL already hides the old card; a later vector cleanup can retry.
                }
            }
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
            budget.settle(reservation, usage.estimatedCost());
            return;
        }
        budget.settle(reservation, usage.estimatedCost());
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

    private static String providerValidation(DialogueModelResponse response, String result) {
        return "result=" + result + " configured_completion_limit="
                + response.configuredCompletionLimit() + " finish_reason=" + response.finishReason()
                + " completion_tokens=" + response.outputTokens()
                + " token_limit_hit=" + response.finishReason().equals("length")
                + " provider_request_id=" + response.providerRequestId().orElse("<absent>")
                + " http_elapsed_ms=" + response.latencyMillis();
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
