package com.silver.aipets.service.http;

import com.silver.aipets.service.memory.InMemoryLongTermMemoryStore;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.vector.EmbeddingJobStore;
import com.silver.aipets.service.vector.EmbeddingModelClient;
import com.silver.aipets.service.vector.EmbeddingVector;
import com.silver.aipets.service.vector.InMemoryEmbeddingJobStore;
import com.silver.aipets.service.vector.InMemoryVectorMemoryRepository;
import com.silver.aipets.service.vector.MemoryEmbeddingWorker;
import com.silver.aipets.service.vector.MemoryReindexService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryReindexHttpHandlerTest {
    private static final String TOKEN = "reindex-test-token-012345678901234567890";

    @Test
    void requiresBearerAndReturnsBoundedIdempotentCounts() throws Exception {
        InMemoryLongTermMemoryStore memories = new InMemoryLongTermMemoryStore();
        UUID petId = UUID.randomUUID();
        memories.put(new LongTermMemoryCard(
                UUID.randomUUID(), petId, 1, "compact memory", MemoryImportance.MEDIUM,
                Set.of(), Set.of(), Set.of(), true));
        InMemoryEmbeddingJobStore jobs = new InMemoryEmbeddingJobStore();
        MemoryEmbeddingWorker worker = new MemoryEmbeddingWorker(
                memories, jobs, new TestEmbeddingModel(), new InMemoryVectorMemoryRepository(),
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC),
                UUID::randomUUID, 3, Duration.ofMinutes(2), Duration.ofSeconds(30));
        MemoryReindexService reindex = new MemoryReindexService(memories, worker);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/admin/memory/reindex",
                new MemoryReindexHttpHandler(reindex, TOKEN));
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/admin/memory/reindex?pageSize=1");
            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(endpoint).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());

            HttpRequest authorizedRequest = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + TOKEN)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> first = client.send(
                    authorizedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode());
            assertEquals("{\"scanned\":1,\"enqueued\":1,\"already_queued\":0}", first.body());

            HttpResponse<String> replay = client.send(
                    authorizedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, replay.statusCode());
            assertEquals("{\"scanned\":1,\"enqueued\":0,\"already_queued\":1}", replay.body());
            assertTrue(replay.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        } finally {
            server.stop(0);
        }
    }

    private static final class TestEmbeddingModel implements EmbeddingModelClient {
        @Override
        public String model() {
            return "reindex-test";
        }

        @Override
        public EmbeddingVector embed(String normalizedText) {
            return new EmbeddingVector(model(), new float[]{1, 0});
        }
    }
}
