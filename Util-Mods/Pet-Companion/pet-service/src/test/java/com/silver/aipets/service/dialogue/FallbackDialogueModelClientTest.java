package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.provider.AiFallbackReporter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackDialogueModelClientTest {
    private static DialogueModelResponse response(String cost) {
        return new DialogueModelResponse("{}", Optional.of("id"), 10, 0, 5, new BigDecimal(cost), 20,
                "stop", 1000, Optional.of("id"));
    }

    private static DialogueModelClient model(AtomicInteger calls, String cost, RuntimeException failure) {
        return new DialogueModelClient() {
            @Override public String model() { return "gpt-6-luna"; }
            @Override public DialogueModelResponse complete(UUID id, DialoguePrompt prompt, String schema, Duration timeout) {
                calls.incrementAndGet();
                if (failure != null) throw failure;
                return response(cost);
            }
        };
    }

    @Test
    void healthyPrimaryNeverTouchesTheFallback() {
        AtomicInteger primary = new AtomicInteger(), fallback = new AtomicInteger();
        var client = new FallbackDialogueModelClient(model(primary, "0.0001", null), model(fallback, "0.0009", null));
        DialogueModelResponse result = client.complete(UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofSeconds(10));
        assertEquals(0, new BigDecimal("0.0001").compareTo(result.estimatedCost()));
        assertEquals(1, primary.get());
        assertEquals(0, fallback.get());
    }

    @Test
    void failedPrimaryIsRetriedOnceOnTheFallbackAndChargedByTheFallback() {
        AtomicInteger primary = new AtomicInteger(), fallback = new AtomicInteger();
        var client = new FallbackDialogueModelClient(
                model(primary, "0", new IllegalStateException("OpenAI returned HTTP 503")),
                model(fallback, "0.0009", null));
        DialogueModelResponse result = client.complete(UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofSeconds(10));
        assertEquals(0, new BigDecimal("0.0009").compareTo(result.estimatedCost()));
        assertEquals(1, primary.get());
        assertEquals(1, fallback.get());
    }

    @Test
    void everyFallbackIsCountedAndMarkedSoItIsNeverSilent() {
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        AiFallbackReporter reporter = new AiFallbackReporter(metrics);
        var client = new FallbackDialogueModelClient(
                model(new AtomicInteger(), "0", new IllegalStateException("OpenAI returned HTTP 503")),
                new DialogueModelClient() {
                    @Override public String model() { return "openai/gpt-6-luna"; }
                    @Override public DialogueModelResponse complete(UUID id, DialoguePrompt prompt, String schema, Duration timeout) {
                        return new DialogueModelResponse("{}", Optional.of("gen-1"), 10, 0, 5, new BigDecimal("0.0001"), 20,
                                "stop", 1000, Optional.of("gen-1"), DialogueModelResponse.FALLBACK);
                    }
                }, reporter);
        DialogueModelResponse result = client.complete(UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofSeconds(10));
        assertEquals(1, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_CHAT_USED));
        assertEquals(0, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_FAILED));
        // The billed row is marked so a fallback is identifiable in the ledger too.
        assertEquals("FALLBACK:openrouter:gpt-6-luna", result.usageModel("gpt-6-luna"));
        assertEquals("gpt-6-luna", response("0.0001").usageModel("gpt-6-luna"));
        assertTrue(new String(metrics.prometheusSnapshot()).contains("aipets_ai_fallback_chat_used_total 1"));

        var failing = new FallbackDialogueModelClient(
                model(new AtomicInteger(), "0", new IllegalStateException("down")),
                model(new AtomicInteger(), "0", new IllegalStateException("also down")), reporter);
        assertThrows(IllegalStateException.class, () -> failing.complete(
                UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofSeconds(10)));
        assertEquals(1, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_FAILED));
    }

    @Test
    void bothFailingSurfacesThePrimaryFailureAndAShortBudgetSkipsTheFallback() {
        AtomicInteger primary = new AtomicInteger(), fallback = new AtomicInteger();
        IllegalStateException first = new IllegalStateException("primary down");
        var client = new FallbackDialogueModelClient(model(primary, "0", first),
                model(fallback, "0", new IllegalStateException("fallback down")));
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> client.complete(
                UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofSeconds(10)));
        assertSame(first, thrown);
        assertEquals(1, fallback.get());
        // With almost no time left a retry cannot finish, so it is not attempted.
        AtomicInteger skipped = new AtomicInteger();
        var tight = new FallbackDialogueModelClient(model(new AtomicInteger(), "0", new IllegalStateException("down")),
                model(skipped, "0", null));
        assertThrows(IllegalStateException.class, () -> tight.complete(
                UUID.randomUUID(), new DialoguePrompt("x", 1), "{}", Duration.ofMillis(1_200)));
        assertEquals(0, skipped.get());
    }
}
