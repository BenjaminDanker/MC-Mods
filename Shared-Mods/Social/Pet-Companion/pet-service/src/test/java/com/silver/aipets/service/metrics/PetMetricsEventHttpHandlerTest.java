package com.silver.aipets.service.metrics;

import com.silver.aipets.common.transport.PetMetricWireCodec;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetMetricsEventHttpHandlerTest {
    private static final String TOKEN = "service-token-0123456789-0123456789-ab";

    @Test
    void acceptsOnlyAuthenticatedBoundedKnownMetricDeltas() throws Exception {
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/metrics/events",
                new PetMetricsEventHttpHandler(metrics, TOKEN));
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/metrics/events");
            HttpClient client = HttpClient.newHttpClient();
            String body = new PetMetricWireCodec().encode(new PetMetricWireEvent(
                    PetMetricWireEvent.Metric.STALE_ENTITY_DISCARDS, 3));
            assertEquals(202, send(client, endpoint, TOKEN, body).statusCode());
            assertEquals(401, send(client, endpoint, TOKEN + "wrong", body).statusCode());
            assertEquals(400, send(client, endpoint, TOKEN,
                    "{\"metric\":\"HTTP_REQUESTS\",\"amount\":1}").statusCode());
            assertEquals(3, counter(metrics, PetOperationalMetrics.Counter.STALE_ENTITY_DISCARDS));
            assertTrue(counter(metrics, PetOperationalMetrics.Counter.HTTP_REQUESTS) == 0);
        } finally {
            server.stop(0);
        }
    }

    private static long counter(PetOperationalMetrics metrics, PetOperationalMetrics.Counter counter) {
        String line = new String(metrics.prometheusSnapshot(), java.nio.charset.StandardCharsets.UTF_8)
                .lines().filter(value -> value.startsWith(counterName(counter) + " "))
                .findFirst().orElseThrow();
        return Long.parseLong(line.substring(line.lastIndexOf(' ') + 1));
    }

    private static String counterName(PetOperationalMetrics.Counter counter) {
        return switch (counter) {
            case STALE_ENTITY_DISCARDS -> "aipets_stale_entity_discards_total";
            case HTTP_REQUESTS -> "aipets_http_requests_total";
            default -> throw new IllegalArgumentException(counter.name());
        };
    }

    private static HttpResponse<String> send(
            HttpClient client, URI endpoint, String token, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
