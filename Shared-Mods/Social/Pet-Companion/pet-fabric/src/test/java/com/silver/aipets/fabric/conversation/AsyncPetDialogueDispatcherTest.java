package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void remainsNonBlockingAndHandlesARepresentativeConcurrentBurst() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch gatewayStarted = new CountDownLatch(1);
            CountDownLatch releaseGateway = new CountDownLatch(1);
            AsyncPetDialogueDispatcher delayed = new AsyncPetDialogueDispatcher(
                    ignored -> {
                        gatewayStarted.countDown();
                        try {
                            releaseGateway.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return CompletableFuture.completedFuture(response());
                    }, Duration.ofSeconds(3), executor);
            long started = System.nanoTime();
            CompletableFuture<PetDialogueResponse> delayedResult = delayed.submit(request());
            assertTrue(gatewayStarted.await(1, TimeUnit.SECONDS));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 250);
            releaseGateway.countDown();
            assertEquals(PetDialogueResponse.Status.SUCCEEDED, delayedResult.join().status());

            AtomicInteger calls = new AtomicInteger();
            AsyncPetDialogueDispatcher burst = new AsyncPetDialogueDispatcher(
                    ignored -> {
                        calls.incrementAndGet();
                        return CompletableFuture.completedFuture(response());
                    }, Duration.ofSeconds(2), executor);
            CompletableFuture<?>[] submissions = new CompletableFuture<?>[32];
            for (int index = 0; index < submissions.length; index++) {
                submissions[index] = CompletableFuture.supplyAsync(
                        () -> burst.submit(request()), callers).thenCompose(stage -> stage);
            }
            CompletableFuture.allOf(submissions).join();
            assertEquals(32, calls.get());
        } finally {
            callers.shutdownNow();
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

    private static PetDialogueResponse response() {
        PetDialogueRequest request = request();
        return new PetDialogueResponse(
                request.requestId(), request.sessionId(), request.petId(),
                PetDialogueResponse.Status.SUCCEEDED, "Hello there.");
    }
}
