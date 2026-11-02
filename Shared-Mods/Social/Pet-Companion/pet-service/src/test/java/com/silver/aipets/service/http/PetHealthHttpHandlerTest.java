package com.silver.aipets.service.http;

import com.silver.aipets.service.health.PetReadinessSnapshot;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetHealthHttpHandlerTest {
    private static final String TOKEN = "service-token-0123456789-0123456789-ab";

    @Test
    void authenticatesAndDistinguishesLivenessFromReadiness() throws Exception {
        AtomicReference<PetReadinessSnapshot> readiness =
                new AtomicReference<>(PetReadinessSnapshot.fullyReady());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        AtomicInteger refreshes = new AtomicInteger();
        metrics.increment(PetOperationalMetrics.Counter.DIALOGUE_SUCCEEDED);
        server.createContext("/health", new PetHealthHttpHandler(
                readiness::get, TOKEN, metrics, refreshes::incrementAndGet));
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> live = client.send(
                    request(base.resolve("/health/live"), TOKEN),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> ready = client.send(
                    request(base.resolve("/health/ready"), TOKEN),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> unauthorized = client.send(
                    request(base.resolve("/health/live"), TOKEN + "wrong"),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, live.statusCode());
            assertTrue(live.body().contains("\"UP\""));
            assertEquals(200, ready.statusCode());
            assertTrue(ready.body().contains("\"CURRENT\""));
            assertTrue(ready.body().contains("DEGRADED_NOT_CONFIGURED"));
            assertEquals(401, unauthorized.statusCode());

            readiness.set(PetReadinessSnapshot.fullyReady(false, true, true));
            HttpResponse<String> configured = client.send(
                    request(base.resolve("/health/ready"), TOKEN),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, configured.statusCode());
            assertTrue(configured.body().contains("\"vector\":\"CONFIGURED\""));
            assertTrue(configured.body().contains("\"model\":\"CONFIGURED\""));

            HttpResponse<String> metricSnapshot = client.send(
                    request(base.resolve("/health/metrics"), TOKEN),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metricSnapshot.statusCode());
            assertEquals(1, refreshes.get());
            assertTrue(metricSnapshot.body().contains("aipets_dialogue_succeeded_total 1"));
            assertTrue(metricSnapshot.body().contains("aipets_embedding_queue_depth 0"));

            readiness.set(PetReadinessSnapshot.databaseDown());
            HttpResponse<String> notReady = client.send(
                    request(base.resolve("/health/ready"), TOKEN),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, notReady.statusCode());
            assertTrue(notReady.body().contains("\"database\":\"DOWN\""));
        } finally {
            server.stop(0);
        }
    }

    private static HttpRequest request(URI uri, String token) {
        return HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header(PetAuthorityHttpHandler.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .GET()
                .build();
    }
}
