package com.silver.aipets.service.dialogue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PetInteractionQueueTest {
    @Test
    void nextQueuedPromptWaitsForConsolidationFutureAndRejectsFourthWaitingMessage() throws Exception {
        Executor direct = Runnable::run;
        UUID pet = UUID.randomUUID();
        PetInteractionQueue<Integer, Integer> queue =
                new PetInteractionQueue<>(3, Duration.ofSeconds(2), direct);
        CompletableFuture<Void> consolidationFuture = new CompletableFuture<>();
        List<Integer> starts = new CopyOnWriteArrayList<>();
        var first = queue.submit(pet, 0, value -> {
            starts.add(value);
            return CompletableFuture.completedFuture(new PetInteractionQueue.Interaction<>(
                    CompletableFuture.completedFuture(value), consolidationFuture));
        });
        assertTrue(first.accepted());
        for (int value = 1; value <= 3; value++) {
            int current = value;
            assertTrue(queue.submit(pet, current, input -> {
                starts.add(input);
                return CompletableFuture.completedFuture(new PetInteractionQueue.Interaction<>(
                        CompletableFuture.completedFuture(input), CompletableFuture.completedFuture(null)));
            }).accepted());
        }
        assertFalse(queue.submit(pet, 4, input -> fail("overflow must not be processed")).accepted());
        assertEquals(List.of(0), starts);
        assertEquals(3, queue.waiting(pet));
        consolidationFuture.complete(null);
        assertEquals(0, first.response().get());
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (queue.active(pet) && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(List.of(0, 1, 2, 3), starts);
        queue.close();
    }

    @Test
    void independentPetsProceedAndFailedBackgroundWorkDoesNotStrandNextEntry() throws Exception {
        PetInteractionQueue<String, String> queue =
                new PetInteractionQueue<>(3, Duration.ofSeconds(1), Runnable::run);
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        CompletableFuture<Void> stuck = new CompletableFuture<>();
        var first = queue.submit(one, "first", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input), stuck)));
        var second = queue.submit(one, "second", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input),
                        CompletableFuture.completedFuture(null))));
        var other = queue.submit(two, "other", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input),
                        CompletableFuture.completedFuture(null))));
        assertEquals("other", other.response().get());
        assertEquals("first", first.response().get());
        stuck.completeExceptionally(new IllegalStateException("background failure"));
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (queue.active(one) && System.nanoTime() < deadline) Thread.sleep(5);
        assertFalse(queue.active(one));
        assertEquals("second", second.response().get());
        queue.close();
    }

    @Test
    void backgroundTimeoutReleasesNextPrompt() throws Exception {
        PetInteractionQueue<String, String> queue =
                new PetInteractionQueue<>(3, Duration.ofMillis(40), Runnable::run);
        UUID pet = UUID.randomUUID();
        CompletableFuture<Void> neverCompletes = new CompletableFuture<>();
        var first = queue.submit(pet, "first", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input), neverCompletes)));
        var next = queue.submit(pet, "next", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input),
                        CompletableFuture.completedFuture(null))));

        assertEquals("first", first.response().get());
        assertEquals("next", next.response().get(1, java.util.concurrent.TimeUnit.SECONDS));
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (queue.active(pet) && System.nanoTime() < deadline) Thread.sleep(1);
        assertFalse(queue.active(pet));
        queue.close();
    }

    @Test
    void closeDropsQueuedEntries() {
        PetInteractionQueue<String, String> queue =
                new PetInteractionQueue<>(3, Duration.ofSeconds(1), Runnable::run);
        UUID pet = UUID.randomUUID();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        queue.submit(pet, "active", input -> CompletableFuture.completedFuture(
                new PetInteractionQueue.Interaction<>(CompletableFuture.completedFuture(input), gate)));
        var waiting = queue.submit(pet, "queued", input -> fail("shutdown dropped queue"));
        queue.close();
        assertTrue(waiting.response().isCompletedExceptionally());
        gate.complete(null);
    }
}
