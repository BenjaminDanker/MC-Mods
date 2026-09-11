package com.silver.aipets.fabric.metrics;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.transport.PetMetricWireCodec;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.silver.aipets.fabric.config.PetServiceClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPetMetricsReporterTest {
    private static final String TOKEN = "service-token-0123456789-0123456789-ab";

    @Test
    void deliversMetricWithoutBlockingCallerAndUsesAuthenticatedWireEvent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/metrics/events", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(202, 0);
            exchange.close();
            received.countDown();
        });
        server.start();
        try {
            PetServiceClientConfig config = new PetServiceClientConfig(
                    new BackendId("fabric-test"),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(2), TOKEN,
                    Map.of(new BackendId("fabric-test"), "Fabric Test"), "disabled");
            HttpPetMetricsReporter reporter = new HttpPetMetricsReporter(
                    config, HttpClient.newHttpClient());
            long started = System.nanoTime();
            reporter.increment(PetMetricWireEvent.Metric.DUPLICATE_ENTITY_DISCARDS);
            assertTrue(Duration.ofNanos(System.nanoTime() - started)
                    .compareTo(Duration.ofMillis(250)) < 0);
            assertTrue(received.await(5, TimeUnit.SECONDS));
            PetMetricWireEvent event = new PetMetricWireCodec().decode(body.get());
            assertEquals(PetMetricWireEvent.Metric.DUPLICATE_ENTITY_DISCARDS, event.metric());
            assertEquals(1, event.amount());
            assertEquals("Bearer " + TOKEN, authorization.get());
            reporter.close();
        } finally {
            server.stop(0);
        }
    }
}
