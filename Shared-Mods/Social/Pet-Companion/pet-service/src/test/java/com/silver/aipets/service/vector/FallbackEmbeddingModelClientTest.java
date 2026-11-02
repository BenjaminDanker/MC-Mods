package com.silver.aipets.service.vector;

import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.provider.AiFallbackReporter;
import com.silver.aipets.service.provider.FakeOpenRouter;
import com.silver.aipets.service.provider.ProviderTarget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackEmbeddingModelClientTest {
    private static EmbeddingModelClient failing() {
        return new EmbeddingModelClient() {
            @Override public String model() { return "text-embedding-3-small"; }
            @Override public EmbeddingVector embed(String text) { return embedWithUsage(text).embedding(); }
            @Override public EmbeddingModelResponse embedWithUsage(String text) {
                throw new VectorStoreUnavailableException("Embedding provider returned HTTP 503");
            }
        };
    }

    @Test
    void outageFallsBackToTheSameModelOnOpenRouterAndIsReportedAndMarkedInTheLedger() throws Exception {
        try (FakeOpenRouter fake = new FakeOpenRouter()) {
            fake.reply(200, "{\"id\":\"gen-e\",\"data\":[{\"embedding\":[0.1,-0.2,0.3]}],"
                    + "\"usage\":{\"prompt_tokens\":7,\"cost\":1.4e-7}}");
            PetOperationalMetrics metrics = new PetOperationalMetrics();
            var fallback = new OpenAiEmbeddingModelClient(
                    ProviderTarget.openRouter(fake.base(), "sk-or-test", new BigDecimal("0.0002")),
                    "openai/text-embedding-3-small", "text-embedding-3-small", 3, Duration.ofSeconds(2),
                    AiPricing.defaults());
            var client = new FallbackEmbeddingModelClient(failing(), fallback, new AiFallbackReporter(metrics));
            EmbeddingModelResponse response = client.embedWithUsage("compact memory card");
            // Vectors carry the primary model name, so they are interchangeable in the existing index.
            assertEquals("text-embedding-3-small", response.embedding().model());
            assertArrayEquals(new float[]{0.1f, -0.2f, 0.3f}, response.embedding().values(), 1e-6f);
            assertEquals(0, new BigDecimal("0.00000014").compareTo(response.estimatedCost()));
            assertEquals("FALLBACK:openrouter:text-embedding-3-small", response.usageModel());
            assertEquals(1, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_EMBEDDING_USED));
            assertTrue(fake.requests().getFirst().body().contains("\"model\":\"openai/text-embedding-3-small\""));
            assertTrue(fake.requests().getFirst().body().contains("\"dimensions\":3"));

            fake.reply(503, "{}");
            assertThrows(VectorStoreUnavailableException.class, () -> client.embed("memory"));
            assertEquals(1, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_FAILED));
        }
    }

    @Test
    void healthyPrimaryIsUsedAndNeverReportedAsFallback() {
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        EmbeddingModelClient healthy = new EmbeddingModelClient() {
            @Override public String model() { return "text-embedding-3-small"; }
            @Override public EmbeddingVector embed(String text) { return embedWithUsage(text).embedding(); }
            @Override public EmbeddingModelResponse embedWithUsage(String text) {
                return new EmbeddingModelResponse(new EmbeddingVector("text-embedding-3-small", new float[]{1f}),
                        Optional.empty(), 3, BigDecimal.ZERO, 5);
            }
        };
        var client = new FallbackEmbeddingModelClient(healthy, failing(), new AiFallbackReporter(metrics));
        assertEquals("text-embedding-3-small", client.embedWithUsage("x").usageModel());
        assertEquals(0, metrics.counter(PetOperationalMetrics.Counter.AI_FALLBACK_EMBEDDING_USED));
    }
}
