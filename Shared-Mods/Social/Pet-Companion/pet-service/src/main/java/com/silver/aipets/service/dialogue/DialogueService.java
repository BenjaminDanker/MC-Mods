package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.PlacedPlacement;
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
    private final DialogueResponseMapper responseMapper;
    private final DialogueAdmissionController admission;
    private final DialogueStateStore state;
    private final Clock clock;
    private final Supplier<UUID> callIds;
    private final Supplier<UUID> eventIds;
    private final BooleanSupplier aiEnabled;
    private final PetOperationalMetrics metrics;
    private final Optional<DialogueModelClient> memoryModel;
    /**
     * Model and moderation calls block on HTTP, so they run on virtual threads: concurrent
     * conversations never queue behind each other the way they would on the shared common pool
     * (3 threads on a 4-core host). Admission control still bounds the number in flight.
     */
    private static final java.util.concurrent.Executor MODEL_CALLS =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final com.silver.aipets.service.provider.AiFallbackReporter fallbackReporter;

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueResponseMapper responseMapper,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds) {
        this(
                limits, safety, prompts, model, codec, responseMapper, admission, state,
                clock, callIds, eventIds, () -> true, new PetOperationalMetrics());
    }

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueResponseMapper responseMapper,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds,
            BooleanSupplier aiEnabled) {
        this(limits, safety, prompts, model, codec, responseMapper, admission, state,
                clock, callIds, eventIds, aiEnabled, new PetOperationalMetrics());
    }

    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueResponseMapper responseMapper,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds,
            BooleanSupplier aiEnabled,
            PetOperationalMetrics metrics) {
        this(limits, safety, prompts, model, codec, responseMapper, admission, state, clock, callIds,
                eventIds, aiEnabled, metrics, Optional.empty());
    }

    /**
     * @param memoryModel optional structured model for the parallel memory bookkeeping call; without
     *        it the turn proposes no owner-report memory, self-model evidence, or owner reading
     */
    public DialogueService(
            DialogueLimits limits,
            DialogueSafety safety,
            DialoguePromptBuilder prompts,
            DialogueModelClient model,
            DialogueOutputCodec codec,
            DialogueResponseMapper responseMapper,
            DialogueAdmissionController admission,
            DialogueStateStore state,
            Clock clock,
            Supplier<UUID> callIds,
            Supplier<UUID> eventIds,
            BooleanSupplier aiEnabled,
            PetOperationalMetrics metrics,
            Optional<DialogueModelClient> memoryModel) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.model = Objects.requireNonNull(model, "model");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper");
        this.admission = Objects.requireNonNull(admission, "admission");
        this.state = Objects.requireNonNull(state, "state");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.callIds = Objects.requireNonNull(callIds, "callIds");
        this.eventIds = Objects.requireNonNull(eventIds, "eventIds");
        this.aiEnabled = Objects.requireNonNull(aiEnabled, "aiEnabled");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.memoryModel = Objects.requireNonNull(memoryModel, "memoryModel");
        this.fallbackReporter = new com.silver.aipets.service.provider.AiFallbackReporter(metrics);
    }

    public DialogueResult respond(
            UUID requestId, UUID ownerUuid, DialogueContext context, String playerInput) {
        DialogueSafety.SafetyResult safe = preprocessLocally(playerInput);
        if (!safe.allowed()) return deniedUnsafe();
        return respondPrepared(requestId, ownerUuid, context, safe,
                moderateAsync(safe), false);
    }

    /** Local gate called before any context or memory retrieval. */
    public DialogueSafety.SafetyResult preprocessLocally(String playerInput) {
        return safety.preprocessLocal(Objects.requireNonNull(playerInput), limits.maximumMessageCharacters());
    }

    /** Begins remote moderation without blocking; its result is not committed to pet state. */
    public java.util.concurrent.CompletableFuture<DialogueSafety.SafetyResult> moderateAsync(
            DialogueSafety.SafetyResult local) {
        Objects.requireNonNull(local, "local");
        if (!local.allowed()) return java.util.concurrent.CompletableFuture.completedFuture(local);
        return safety.moderateAsync(local.normalizedText(), MODEL_CALLS).toCompletableFuture();
    }

    /** Runs after speculative context retrieval. Model output stays private until moderation passes. */
    public DialogueResult respondPrepared(
            UUID requestId, UUID ownerUuid, DialogueContext context,
            DialogueSafety.SafetyResult safe,
            java.util.concurrent.CompletableFuture<DialogueSafety.SafetyResult> moderation) {
        return respondPrepared(requestId, ownerUuid, context, safe, moderation, false);
    }

    public DialogueResult respondPrepared(
            UUID requestId, UUID ownerUuid, DialogueContext context,
            DialogueSafety.SafetyResult safe,
            java.util.concurrent.CompletableFuture<DialogueSafety.SafetyResult> moderation,
            boolean alreadyQueued) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(moderation, "moderation");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(safe, "safe");
        if (!aiEnabled.getAsBoolean()) {
            return DialogueResult.denied(
                    DialogueResultStatus.NO_AI_ACCESS, "Your pet is quiet right now. Please try again later.");
        }
        if (!context.pet().ownerUuid().equals(ownerUuid)) {
            return DialogueResult.denied(
                    DialogueResultStatus.UNSAFE_INPUT, "That is not your pet.");
        }
        if (!(context.pet().placement() instanceof PlacedPlacement placed)
                || !placed.isMaterialized()) {
            return DialogueResult.denied(
                    DialogueResultStatus.NOT_PRESENT, "Your pet is not present right now.");
        }
        if (!context.aiAccessEnabled()) {
            return DialogueResult.denied(
                    DialogueResultStatus.NO_AI_ACCESS, "Your pet is quiet right now. Check your pet membership.");
        }
        if (context.sleeping()) {
            return DialogueResult.denied(
                    DialogueResultStatus.SLEEPING, "Your pet is sleeping.");
        }
        if (!safe.allowed()) {
            return deniedUnsafe();
        }
        Instant startedAt = clock.instant();
        long admissionStart=System.nanoTime();
        DialogueAdmissionController.Admission acquired = admission.acquire(
                context.pet().petId(), ownerUuid, startedAt, alreadyQueued);
        DialogueDiagnostics.elapsed("admission_no_queue",admissionStart);
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
        Optional<DialoguePrompt> memoryPrompt;
        try {
            long promptStart=System.nanoTime();
            prompt = prompts.build(context, safe.normalizedText());
            memoryPrompt = memoryModel.isPresent()
                    ? Optional.of(prompts.buildMemory(context, safe.normalizedText())) : Optional.empty();
            DialogueDiagnostics.elapsed("prompt_assembly",promptStart);
        } catch (RuntimeException failure) {
            permit.rejected();
            return DialogueResult.denied(
                    DialogueResultStatus.PERSISTENCE_FAILURE, "Conversation context is temporarily unavailable.");
        }

        UUID callId = Objects.requireNonNull(callIds.get(), "callIds returned null");
        long modelWaitStart = System.nanoTime();
        long deadline = modelWaitStart + limits.modelTimeout().toNanos();
        metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_CALLS);
        java.util.concurrent.CompletableFuture<DialogueModelResponse> memoryCall = memoryPrompt.map(memory ->
                memoryModel.orElseThrow().completeAsync(memoryRequestId(requestId), memory,
                        DialogueStructuredSchema.MEMORY_JSON_SCHEMA, limits.modelTimeout(),
                        MODEL_CALLS)).orElse(null);
        java.util.concurrent.CompletableFuture<DialogueModelResponse> voiceCall = model.completeAsync(
                requestId, prompt, DialogueStructuredSchema.VOICE_JSON_SCHEMA, limits.modelTimeout(),
                MODEL_CALLS);
        // Moderation that cannot run lets the message through (reported); it never fails the turn.
        java.util.concurrent.CompletableFuture<DialogueSafety.SafetyResult> moderationOrBypass = moderation.handle((result, failure) -> {
            if (failure == null && result != null) return result;
            fallbackReporter.moderationBypassed(failure == null ? "MODERATION_EMPTY" : failure.getClass().getSimpleName());
            return new DialogueSafety.SafetyResult(true, safe.normalizedText(), "MODERATION_BYPASSED");
        });
        DialogueModelResponse voice;
        try {
            java.util.concurrent.CompletableFuture.anyOf(voiceCall, moderationOrBypass)
                    .get(limits.modelTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (moderationOrBypass.isDone() && !moderationOrBypass.join().allowed()) {
                abandon(requestId, context, moderationOrBypass.join().category(), voiceCall, memoryCall);
                fallbackReporter.moderationBlocked();
                permit.rejected(BigDecimal.ZERO, clock.instant());
                return deniedUnsafe();
            }
            voice = voiceCall.get(limits.modelTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            DialogueDiagnostics.elapsed("voice_model_complete", modelWaitStart);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            abandon(requestId, context, "INTERRUPTED", voiceCall, memoryCall);
            permit.providerFailed(clock.instant());
            return DialogueResult.denied(DialogueResultStatus.PROVIDER_FAILURE,
                    "Your pet cannot find the words right now.");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                 | RuntimeException failure) {
            abandon(requestId, context, "PROVIDER_FAILURE", voiceCall, memoryCall);
            metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_FAILED);
            Instant failedAt = clock.instant();
            permit.providerFailed(failedAt);
            Throwable cause = failure instanceof java.util.concurrent.ExecutionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            state.recordUsage(failedUsage(callId, requestId, context, failedAt,
                    DialogueUsage.Status.FAILED, cause.getClass().getSimpleName()));
            logModelCall(requestId, context, null, "provider_failure",
                    cause instanceof RuntimeException runtime ? runtime : new RuntimeException(cause));
            return DialogueResult.denied(DialogueResultStatus.PROVIDER_FAILURE,
                    "Your pet cannot find the words right now.");
        }
        DialogueSafety.SafetyResult moderationResult;
        try {
            moderationResult = moderationOrBypass.get(limits.modelTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            moderationResult = new DialogueSafety.SafetyResult(false, "", "MODERATION_INTERRUPTED");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            fallbackReporter.moderationBypassed("MODERATION_TIMEOUT");
            moderationResult = new DialogueSafety.SafetyResult(true, safe.normalizedText(), "MODERATION_BYPASSED");
        }
        if (!moderationResult.allowed()) {
            // The voice call is recorded just below; the memory call may still be in flight.
            abandon(requestId, context, moderationResult.category(), null, memoryCall);
            fallbackReporter.moderationBlocked();
            DialogueDiagnostics.event("speculative_dialogue_discarded", requestId,
                    "moderation=" + moderationResult.category() + " result_always_discarded=true");
            Instant at = clock.instant();
            // The voice call was already paid for; keep its cost durable but commit nothing else.
            permit.rejected(voice.estimatedCost(), at);
            state.recordUsage(usage(callId, requestId, context, voice, at, DialogueUsage.Status.REJECTED,
                    Optional.of("MODERATION"), prompt));
            return deniedUnsafe();
        }
        Optional<DialogueModelResponse> memory = awaitMemory(requestId, context, memoryCall, deadline);
        DialogueModelResponse response = combine(voice, memory);
        DialogueDiagnostics.elapsed("model_request_to_complete_body", modelWaitStart);

        ValidatedDialogueOutput output;
        try {
            long validationStart=System.nanoTime();
            DialogueDiagnostics.event("dialogue_provider_completion", requestId,
                    "configured_completion_limit=" + response.configuredCompletionLimit()
                            + " finish_reason=" + safeCategory(response.finishReason())
                            + " completion_tokens=" + response.outputTokens()
                            + " token_limit_hit=" + response.finishReason().equals("length")
                            + " provider_request_id=" + response.providerRequestId().orElse("<absent>")
                            + " http_elapsed_ms=" + response.latencyMillis()
                            + " memory_call=" + (memory.isPresent() ? "used" : memoryCall == null ? "disabled" : "unavailable"));
            if (!response.finishReason().equals("stop")) {
                throw new DialogueOutputException(response.finishReason().equals("length")
                        ? "Provider response hit the configured completion token limit"
                        : "Provider response did not finish normally", null);
            }
            // Provider tokenizers differ from the local budget counter; reject only a clear overrun.
            if (voice.inputTokens() > limits.hardInputTokens() * 3 / 2) {
                throw new DialogueOutputException("Provider input usage exceeded hard cap", null);
            }
            output = responseMapper.map(
                    codec.decode(response.structuredJson()), safe.normalizedText(), context);
            DialogueDiagnostics.event("dialogue_schema_validation", requestId,
                    "result=passed configured_completion_limit=" + response.configuredCompletionLimit()
                            + " finish_reason=" + safeCategory(response.finishReason())
                            + " completion_tokens=" + response.outputTokens()
                            + " provider_request_id=" + response.providerRequestId().orElse("<absent>"));
            DialogueDiagnostics.elapsed("model_decode_and_validation",validationStart);
        } catch (RuntimeException failure) {
            DialogueDiagnostics.event("dialogue_schema_validation", requestId,
                    "result=failed configured_completion_limit=" + response.configuredCompletionLimit()
                            + " finish_reason=" + safeCategory(response.finishReason())
                            + " completion_tokens=" + response.outputTokens()
                            + " token_limit_hit=" + response.finishReason().equals("length")
                            + " provider_request_id=" + response.providerRequestId().orElse("<absent>"));
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
            long persistenceStart = System.nanoTime();
            DialoguePersistResult persisted = state.commit(new DialoguePersistRequest(
                    Objects.requireNonNull(eventIds.get(), "eventIds returned null"),
                    context.pet(), safe.normalizedText(), output, usage,
                    context.gameContext(), completedAt));
            DialogueDiagnostics.elapsed("mariadb_dialogue_commit", persistenceStart);
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

    /** The memory call never blocks a reply beyond the shared deadline and never fails the turn. */
    private Optional<DialogueModelResponse> awaitMemory(UUID requestId, DialogueContext context,
            java.util.concurrent.CompletableFuture<DialogueModelResponse> memoryCall, long deadlineNanos) {
        if (memoryCall == null) return Optional.empty();
        long waitStart = System.nanoTime();
        try {
            long remaining = Math.max(1L, deadlineNanos - waitStart);
            DialogueModelResponse response = memoryCall.get(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
            DialogueDiagnostics.elapsed("memory_model_wait_after_voice", waitStart);
            if (!response.completedNormally()) {
                DialogueDiagnostics.event("memory_bookkeeping_unavailable", requestId,
                        "finish_reason=" + safeCategory(response.finishReason()));
                return Optional.of(costOnly(response));
            }
            return Optional.of(response);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            abandon(requestId, context, "MEMORY_INTERRUPTED", null, memoryCall);
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                 | RuntimeException failure) {
            abandon(requestId, context, "MEMORY_UNAVAILABLE", null, memoryCall);
            DialogueDiagnostics.event("memory_bookkeeping_unavailable", requestId, failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static final java.util.Set<String> VOICE_FIELDS = java.util.Set.of(
            "reply", "action", "attention", "affect_delta", "affect_strength", "relationship_event");
    private static final java.util.Set<String> MEMORY_FIELDS = java.util.Set.of(
            "memory_candidate", "self_model_candidate", "owner_interpretation");

    /**
     * Joins the voice and memory results into the one structured response the codec validates, and
     * sums their usage so the turn is charged its full provider cost. A missing or malformed memory
     * result contributes neutral fields; the voice result is never altered.
     */
    static DialogueModelResponse combine(DialogueModelResponse voice, Optional<DialogueModelResponse> memory) {
        String json = voice.structuredJson();
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().keySet().equals(VOICE_FIELDS)) {
                com.google.gson.JsonObject merged = parsed.getAsJsonObject().deepCopy();
                com.google.gson.JsonObject records = memory.filter(DialogueModelResponse::completedNormally)
                        .flatMap(DialogueService::memoryFields).orElseGet(DialogueService::neutralMemoryFields);
                records.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                json = merged.toString();
            }
        } catch (RuntimeException malformed) {
            // Leave the voice content unchanged; strict decoding reports it as invalid output.
        }
        if (memory.isEmpty()) {
            return new DialogueModelResponse(json, voice.providerId(), voice.inputTokens(),
                    voice.cachedInputTokens(), voice.outputTokens(), voice.estimatedCost(), voice.latencyMillis(),
                    voice.finishReason(), voice.configuredCompletionLimit(), voice.providerRequestId(),
                    voice.provider());
        }
        DialogueModelResponse records = memory.orElseThrow();
        return new DialogueModelResponse(json, voice.providerId(),
                voice.inputTokens() + records.inputTokens(),
                voice.cachedInputTokens() + records.cachedInputTokens(),
                voice.outputTokens() + records.outputTokens(),
                voice.estimatedCost().add(records.estimatedCost()),
                Math.max(voice.latencyMillis(), records.latencyMillis()),
                voice.finishReason(), voice.configuredCompletionLimit(), voice.providerRequestId(),
                voice.servedByFallback() || records.servedByFallback()
                        ? DialogueModelResponse.FALLBACK : DialogueModelResponse.PRIMARY);
    }

    private static Optional<com.google.gson.JsonObject> memoryFields(DialogueModelResponse memory) {
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(memory.structuredJson());
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(MEMORY_FIELDS)) {
                return Optional.empty();
            }
            return Optional.of(parsed.getAsJsonObject());
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static com.google.gson.JsonObject neutralMemoryFields() {
        return com.google.gson.JsonParser.parseString("""
                {"memory_candidate":null,
                 "self_model_candidate":{"kind":"NONE","value":null,"confidence":0},
                 "owner_interpretation":{"intent":null,"emotion":null,"confidence":0}}
                """).getAsJsonObject();
    }

    /** A truncated memory result still cost money; keep its usage but none of its content. */
    private static DialogueModelResponse costOnly(DialogueModelResponse response) {
        return new DialogueModelResponse("{}", response.providerId(), response.inputTokens(),
                response.cachedInputTokens(), response.outputTokens(), response.estimatedCost(),
                response.latencyMillis(), response.finishReason(), response.configuredCompletionLimit(),
                response.providerRequestId(), response.provider());
    }

    private static UUID memoryRequestId(UUID requestId) {
        return UUID.nameUUIDFromBytes((requestId + ":memory").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static DialogueResult deniedUnsafe() {
        return DialogueResult.denied(DialogueResultStatus.UNSAFE_INPUT, "That message cannot be sent.");
    }

    /**
     * Stops waiting for model calls the turn no longer needs (moderation blocked it, the reply failed,
     * or the memory call timed out). An HTTP call cannot be recalled, so the provider may still finish
     * and bill it: when it does, its real cost is recorded as a REJECTED {@code ai_usage} row with the
     * reason, so the owner's budget and our ledger never miss spend. Failed calls report no usage.
     */
    private void abandon(UUID requestId, DialogueContext context, String reason,
            java.util.concurrent.CompletableFuture<DialogueModelResponse> voiceCall,
            java.util.concurrent.CompletableFuture<DialogueModelResponse> memoryCall) {
        DialogueDiagnostics.event("model_calls_abandoned", requestId, "reason=" + safeCategory(reason)
                + " voice=" + (voiceCall != null) + " memory=" + (memoryCall != null));
        billWhenItCompletes(requestId, context, reason, "voice", model.model(), voiceCall);
        billWhenItCompletes(requestId, context, reason, "memory",
                memoryModel.map(DialogueModelClient::model).orElse(model.model()), memoryCall);
    }

    private void billWhenItCompletes(UUID requestId, DialogueContext context, String reason, String part,
            String modelName, java.util.concurrent.CompletableFuture<DialogueModelResponse> call) {
        if (call == null) return;
        UUID usageRequestId = UUID.nameUUIDFromBytes(
                (requestId + ":abandoned:" + part).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        call.whenComplete((response, failure) -> {
            if (response == null) return;
            try {
                recordUsageMetrics(response);
                state.recordUsage(new DialogueUsage(UUID.randomUUID(), usageRequestId, context.pet().petId(),
                        context.pet().ownerUuid(), response.usageModel(modelName), response.providerId(),
                        response.inputTokens(), response.cachedInputTokens(), response.outputTokens(),
                        response.estimatedCost(), response.latencyMillis(), DialogueUsage.Status.REJECTED,
                        Optional.of(safeCategory(reason + "_" + part)), clock.instant()));
            } catch (RuntimeException unrecorded) {
                LOGGER.log(System.Logger.Level.ERROR, "ALERT AI_USAGE_UNRECORDED {\"part\":\"" + part
                        + "\",\"request_id\":\"" + requestId + "\",\"reason\":\"" + safeCategory(reason) + "\"}",
                        unrecorded);
            }
        });
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
                response.usageModel(usageModel()), response.providerId(), response.inputTokens(),
                response.cachedInputTokens(), response.outputTokens(), response.estimatedCost(),
                response.latencyMillis(), status, category.map(DialogueService::safeCategory), at,
                Optional.of(prompt.contextUsage()));
    }

    private String usageModel() {
        String voice = model.model();
        String name = memoryModel.map(DialogueModelClient::model).filter(memory -> !memory.equals(voice))
                .map(memory -> voice + "+" + memory).orElse(voice);
        return name.length() <= 191 ? name : voice;
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
