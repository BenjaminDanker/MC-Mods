package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncPetDialogueDispatcherTest {
    @Test
    void invokesGatewayOffCallerThreadAndAppliesTotalTimeout() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PetDialogueRequest request = request();
            AtomicReference<Thread> gatewayThread = new AtomicReference<>();
            PetDialogueResponse expected = new PetDialogueResponse(
                    request.requestId(), request.sessionId(), request.petId(),
                    PetDialogueResponse.Status.SUCCEEDED, "Hello there.");
            AsyncPetDialogueDispatcher successful = new AsyncPetDialogueDispatcher(
                    actual -> {
                        gatewayThread.set(Thread.currentThread());
                        return CompletableFuture.completedFuture(expected);
                    }, Duration.ofSeconds(1), executor);

            assertEquals(expected, successful.submit(request).get(1, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread(), gatewayThread.get());

            AsyncPetDialogueDispatcher timedOut = new AsyncPetDialogueDispatcher(
                    ignored -> new CompletableFuture<>(), Duration.ofMillis(30), executor);
            CompletionException failure = assertThrows(
                    CompletionException.class, () -> timedOut.submit(request).join());
            assertInstanceOf(TimeoutException.class, failure.getCause());

            AsyncPetDialogueDispatcher rejected = new AsyncPetDialogueDispatcher(
                    ignored -> CompletableFuture.completedFuture(expected),
                    Duration.ofSeconds(1), command -> {
                        throw new RejectedExecutionException("shutting down");
                    });
            CompletableFuture<PetDialogueResponse> rejectedFuture = rejected.submit(request);
            CompletionException rejectedFailure = assertThrows(
                    CompletionException.class, rejectedFuture::join);
            assertInstanceOf(RejectedExecutionException.class, rejectedFailure.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static PetDialogueRequest request() {
        return new PetDialogueRequest(
                UUID.fromString("50000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("60000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("20000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("10000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("30000000-0000-0000-0000-0000000000aa"),
                new BackendId("survival"), "minecraft:overworld", "Hello");
    }
}
