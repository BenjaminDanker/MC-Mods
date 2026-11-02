package com.silver.aipets.fabric.conversation;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Enforces off-tick gateway invocation and a total timeout even for a blocking adapter. */
public final class AsyncPetDialogueDispatcher {
    private final PetDialogueGateway gateway;
    private final Duration timeout;
    private final Executor executor;

    public AsyncPetDialogueDispatcher(
            PetDialogueGateway gateway, Duration timeout, Executor executor) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public CompletableFuture<PetDialogueResponse> submit(PetDialogueRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return CompletableFuture
                    .supplyAsync(() -> gateway.submit(request), executor)
                    .thenCompose(AsyncPetDialogueDispatcher::toFuture)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException schedulingFailure) {
            return CompletableFuture.failedFuture(schedulingFailure);
        }
    }

    private static CompletableFuture<PetDialogueResponse> toFuture(
            CompletionStage<PetDialogueResponse> stage) {
        return Objects.requireNonNull(stage, "gateway returned null stage").toCompletableFuture();
    }
}
