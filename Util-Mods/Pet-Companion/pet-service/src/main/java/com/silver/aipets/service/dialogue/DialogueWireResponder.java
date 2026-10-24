package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.service.http.PetDialogueHttpHandler;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.UUID;

/** Authenticated dialogue path with speculative moderation and a bounded FIFO per pet. */
public final class DialogueWireResponder implements PetDialogueHttpHandler.Responder,
        PetDialogueHttpHandler.AsyncResponder, AutoCloseable {
    private final DialogueContextLoader contexts;
    private final DialogueService dialogue;
    private final Clock clock;
    private final Executor executor;
    private final DialoguePostResponseWork postResponseWork;
    private final PetInteractionQueue<PetDialogueWireRequest, PetDialogueWireResult> queues;

    public DialogueWireResponder(DialogueContextLoader contexts, DialogueService dialogue, Clock clock) {
        this(contexts, dialogue, clock, Runnable::run);
    }

    public DialogueWireResponder(DialogueContextLoader contexts, DialogueService dialogue,
            Clock clock, Executor executor) {
        this(contexts, dialogue, clock, executor, DialoguePostResponseWork.none());
    }

    public DialogueWireResponder(DialogueContextLoader contexts, DialogueService dialogue,
            Clock clock, Executor executor, DialoguePostResponseWork postResponseWork) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.postResponseWork = Objects.requireNonNull(postResponseWork, "postResponseWork");
        this.queues = new PetInteractionQueue<>(3, java.time.Duration.ofSeconds(45), executor);
    }

    @Override
    public PetDialogueWireResult respond(PetDialogueWireRequest request) {
        return process(Objects.requireNonNull(request));
    }

    @Override
    public CompletionStage<PetDialogueWireResult> respondAsync(PetDialogueWireRequest request) {
        Objects.requireNonNull(request, "request");
        PetInteractionQueue.Submission<PetDialogueWireResult> submitted = queues.submitWithQueueStatus(
                request.petId(), request, this::processQueued);
        if (!submitted.accepted()) {
            return CompletableFuture.completedFuture(denied(
                    request, "Your pet is still processing. Please try again shortly."));
        }
        return submitted.response();
    }

    public int waiting(UUID petId) { return queues.waiting(petId); }

    private CompletionStage<PetInteractionQueue.Interaction<PetDialogueWireResult>> processQueued(
            PetDialogueWireRequest request, boolean queued) {
        PetDialogueWireResult result = process(request, queued);
        CompletionStage<Void> finalized;
        try {
            finalized = result.status() == PetDialogueWireResult.Status.SUCCEEDED
                    ? Objects.requireNonNull(postResponseWork.afterCommit(request, result),
                            "post-response work returned null")
                    : CompletableFuture.completedFuture(null);
        } catch (RuntimeException failure) {
            finalized = CompletableFuture.failedFuture(failure);
        }
        // The dialogue response is durable before the post-commit stage begins. The queue sends
        // it immediately but does not start this pet's next prompt until finalized completes.
        return CompletableFuture.completedFuture(new PetInteractionQueue.Interaction<>(
                CompletableFuture.completedFuture(result), finalized));
    }

    private PetDialogueWireResult process(PetDialogueWireRequest request) {
        return process(request, false);
    }

    private PetDialogueWireResult process(PetDialogueWireRequest request, boolean queued) {
        DialogueSafety.SafetyResult local = dialogue.preprocessLocally(request.message());
        if (!local.allowed()) return denied(request, "That message cannot be sent.");
        // Start moderation before context SQL/vector retrieval. Its result is kept private.
        CompletableFuture<DialogueSafety.SafetyResult> moderation = dialogue.moderateAsync(local);
        Optional<DialogueContext> context;
        try {
            context = contexts.loadSpeculatively(request.ownerUuid(), request.petId(),
                    request.backendId().value(), request.dimensionId(), clock.instant(),
                    local.normalizedText(), request.scene());
        } catch (RuntimeException failure) {
            return denied(request, "Conversation context is temporarily unavailable.");
        }
        if (context.isEmpty()) return denied(request, "That pet is not available to this owner.");
        DialogueResult result = dialogue.respondPrepared(request.requestId(), request.ownerUuid(),
                context.orElseThrow(), local, moderation, queued);
        String message = result.playerMessage().strip();
        if (message.isEmpty()) message = "Your pet could not reply right now.";
        return result.status() == DialogueResultStatus.SUCCEEDED
                ? new PetDialogueWireResult(request.requestId(), request.sessionId(), request.petId(),
                        PetDialogueWireResult.Status.SUCCEEDED, message,
                        result.output().orElseThrow().action(), result.output().orElseThrow().attention())
                : denied(request, message);
    }

    private static PetDialogueWireResult denied(PetDialogueWireRequest request, String message) {
        return new PetDialogueWireResult(request.requestId(), request.sessionId(), request.petId(),
                PetDialogueWireResult.Status.DENIED, message);
    }

    @Override public void close() { queues.close(); }
}
