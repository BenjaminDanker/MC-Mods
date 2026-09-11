package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Synchronous service-core pipeline; callers must run it outside the Minecraft server thread. */
public final class DialogueService {
    private static final System.Logger LOGGER = System.getLogger(DialogueService.class.getName());
    private final DialogueLimits limits;
    private final DialogueSafety safety;
    private final DialoguePromptBuilder prompts;
    private final DialogueModelClient model;
    private final DialogueOutputCodec codec;
    private final DialogueOutputValidator outputValidator;
    private final DialogueAdmissionController admission;
    private final DialogueStateStore state;
    private final Clock clock;
    private final Supplier<UUID> callIds;
    private final Supplier<UUID> eventIds;
    private final BooleanSupplier aiEnabled;
    private final PetOperationalMetrics metrics;

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueOutputValidator outputValidator,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds) {
        this(
                limits, safety, prompts, model, codec, outputValidator, admission, state,
                clock, callIds, eventIds, () -> true, new PetOperationalMetrics());
    }

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueOutputValidator outputValidator,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds,
            BooleanSupplier aiEnabled) {
        this(limits, safety, prompts, model, codec, outputValidator, admission, state,
                clock, callIds, eventIds, aiEnabled, new PetOperationalMetrics());
    }

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueOutputValidator outputValidator,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds,
            BooleanSupplier aiEnabled,
            PetOperationalMetrics metrics) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.model = Objects.requireNonNull(model, "model");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator");
        this.admission = Objects.requireNonNull(admission, "admission");
        this.state = Objects.requireNonNull(state, "state");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.callIds = Objects.requireNonNull(callIds, "callIds");
        this.eventIds = Objects.requireNonNull(eventIds, "eventIds");
        this.aiEnabled = Objects.requireNonNull(aiEnabled, "aiEnabled");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public DialogueResult respond(
            UUID requestId, UUID ownerUuid, DialogueContext context, String playerInput) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(playerInput, "playerInput");
        if (!aiEnabled.getAsBoolean()) {
            return DialogueResult.denied(
                    DialogueResultStatus.NO_AI_ACCESS, "Your pet is quiet right now. Please try again later.");
        }
        if (!context.pet().ownerUuid().equals(ownerUuid)) {
            return DialogueResult.denied(
                    DialogueResultStatus.UNSAFE_INPUT, "That is not your pet.");
        }
        if (!context.aiAccessEnabled()) {
            return DialogueResult.denied(
                    DialogueResultStatus.NO_AI_ACCESS, "Your pet is quiet right now. Check your pet membership.");
        }
        if (context.sleeping()) {
            return DialogueResult.denied(
                    DialogueResultStatus.SLEEPING, "Your pet is sleeping.");
        }
        DialogueSafety.SafetyResult safe = safety.preprocess(
                playerInput, limits.maximumMessageCharacters());
        if (!safe.allowed()) {
            return DialogueResult.denied(
                    DialogueResultStatus.UNSAFE_INPUT, "That message cannot be sent.");
        }
        Instant startedAt = clock.instant();
        DialogueAdmissionController.Admission acquired = admission.acquire(
                context.pet().petId(), ownerUuid, startedAt);
        if (acquired.permit().isEmpty()) {
            String message = acquired.denial().orElse(null)
                    == DialogueAdmissionController.Denial.BUDGET_EXHAUSTED
                    ? "Your conversation balance is used for this billing period. It will refresh with your next payment."
                    : "Your pet is too distracted to reply right now.";
            return DialogueResult.denied(
                    DialogueResultStatus.LIMITED, message);
        }
        DialogueAdmissionController.Permit permit = acquired.permit().orElseThrow();
        DialoguePrompt prompt;
        try {
            prompt = prompts.build(context, safe.normalizedText());
        } catch (RuntimeException failure) {
            permit.rejected();
            return DialogueResult.denied(
                    DialogueResultStatus.PERSISTENCE_FAILURE, "Conversation context is temporarily unavailable.");
        }

        UUID callId = Objects.requireNonNull(callIds.get(), "callIds returned null");
        DialogueModelResponse response;
        try {
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_CALLS);
            response = model.complete(
                    requestId, prompt, DialogueStructuredSchema.JSON_SCHEMA, limits.modelTimeout());
        } catch (RuntimeException failure) {
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_FAILED);
            Instant failedAt = clock.instant();
            permit.providerFailed(failedAt);
            state.recordUsage(failedUsage(
                    callId, requestId, context, failedAt,
                    DialogueUsage.Status.FAILED, failure.getClass().getSimpleName()));
            logModelCall(requestId, context, null, "provider_failure", failure);
            return DialogueResult.denied(
                    DialogueResultStatus.PROVIDER_FAILURE, "Your pet cannot find the words right now.");
        }

        ValidatedDialogueOutput output;
        try {
            if (response.inputTokens() > limits.hardInputTokens()) {
                throw new DialogueOutputException("Provider input usage exceeded hard cap", null);
            }
            output = outputValidator.validate(
                    codec.decode(response.structuredJson()), safe.normalizedText());
        } catch (RuntimeException failure) {
            recordUsageMetrics(response);
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_FAILED);
            Instant failedAt = clock.instant();
            permit.providerFailed(response.estimatedCost(), failedAt);
            state.recordUsage(usage(
                    callId, requestId, context, response, failedAt,
                    DialogueUsage.Status.REJECTED, Optional.of("INVALID_OUTPUT"), prompt));
            logModelCall(requestId, context, response, "invalid_output", failure);
            return DialogueResult.denied(
                    DialogueResultStatus.INVALID_MODEL_OUTPUT, "Your pet's reply was unclear.");
        }

        Instant completedAt = clock.instant();
        DialogueUsage usage = usage(
                callId, requestId, context, response, completedAt,
                DialogueUsage.Status.SUCCEEDED, Optional.empty(), prompt);
        try {
            DialoguePersistResult persisted = state.commit(new DialoguePersistRequest(
                    Objects.requireNonNull(eventIds.get(), "eventIds returned null"),
                    context.pet(), safe.normalizedText(), output, usage,
                    context.gameContext(), completedAt));
            permit.succeeded(response.estimatedCost(), completedAt);
            recordUsageMetrics(response);
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_SUCCEEDED);
            logModelCall(requestId, context, response, "succeeded", null);
            return DialogueResult.succeeded(output, persisted);
        } catch (RuntimeException failure) {
            recordUsageMetrics(response);
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_FAILED);
            permit.rejected(response.estimatedCost(), completedAt);
            state.recordUsage(usage);
            logModelCall(requestId, context, response, "persistence_failure", failure);
            return DialogueResult.denied(
                    DialogueResultStatus.PERSISTENCE_FAILURE, "Your pet's reply could not be saved safely.");
        }
    }

    private void recordUsageMetrics(DialogueModelResponse response) {
        metrics.add(PetOperationalMetrics.Counter.INPUT_TOKENS, response.inputTokens());
        metrics.add(PetOperationalMetrics.Counter.CACHED_INPUT_TOKENS, response.cachedInputTokens());
        metrics.add(PetOperationalMetrics.Counter.OUTPUT_TOKENS, response.outputTokens());
        metrics.add(PetOperationalMetrics.Counter.ESTIMATED_COST_MICROS,
                Math.max(0L, response.estimatedCost().movePointRight(6).longValue()));
    }

    private DialogueUsage failedUsage(
            UUID callId,
            UUID requestId,
            DialogueContext context,
            Instant at,
            DialogueUsage.Status status,
            String category) {
        return new DialogueUsage(
                callId, requestId, context.pet().petId(), context.pet().ownerUuid(),
                model.model(), Optional.empty(), 0, 0, 0, BigDecimal.ZERO, 0,
                status, Optional.of(safeCategory(category)), at);
    }

    private DialogueUsage usage(
            UUID callId,
            UUID requestId,
            DialogueContext context,
            DialogueModelResponse response,
            Instant at,
            DialogueUsage.Status status,
            Optional<String> category,
            DialoguePrompt prompt) {
        return new DialogueUsage(
                callId, requestId, context.pet().petId(), context.pet().ownerUuid(),
                model.model(), response.providerId(), response.inputTokens(),
                response.cachedInputTokens(), response.outputTokens(), response.estimatedCost(),
                response.latencyMillis(), status, category.map(DialogueService::safeCategory), at,
                Optional.of(prompt.contextUsage()));
    }

    private static String safeCategory(String category) {
        String safe = category == null ? "RuntimeException"
                : category.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.substring(0, Math.min(safe.length(), 64));
    }

    private void logModelCall(
            UUID requestId,
            DialogueContext context,
            DialogueModelResponse response,
            String outcome,
            RuntimeException failure) {
        StructuredPetEvent event = StructuredPetEvent.operation("dialogue_model_call")
                .correlation(requestId)
                .pet(context.pet().petId())
                .owner(context.pet().ownerUuid())
                .outcome(outcome);
        if (response == null) {
            event.modelUsage(model.model(), 0, 0, 0);
        } else {
            event.modelUsage(
                    model.model(), response.inputTokens(), response.cachedInputTokens(),
                    response.outputTokens()).latencyMillis(response.latencyMillis());
        }
        if (failure != null) event.failure(failure);
        LOGGER.log(failure == null ? System.Logger.Level.INFO : System.Logger.Level.WARNING,
                event.toJson());
    }
}
