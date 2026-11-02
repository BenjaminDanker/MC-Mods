package com.silver.aipets.fabric.metrics;

import com.silver.aipets.common.transport.PetMetricWireCodec;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.config.PetServiceClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Fire-and-forget metrics delivery to the central service. At most 32 requests can be in flight;
 * a service outage only drops diagnostics and never blocks a Minecraft tick.
 */
public final class HttpPetMetricsReporter implements PetMetricsReporter, AutoCloseable {
    private static final int MAX_IN_FLIGHT = 32;
    private final URI endpoint;
    private final String bearerToken;
    private final java.time.Duration requestTimeout;
    private final HttpClient client;
    private final PetMetricWireCodec codec = new PetMetricWireCodec();
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

    public HttpPetMetricsReporter(PetServiceClientConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public HttpPetMetricsReporter(PetServiceClientConfig config, HttpClient client) {
        Objects.requireNonNull(config, "config");
        this.endpoint = config.baseUri().resolve("v1/metrics/events");
        this.bearerToken = config.bearerToken();
        this.requestTimeout = config.requestTimeout();
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void increment(PetMetricWireEvent.Metric metric) {
        Objects.requireNonNull(metric, "metric");
        if (!inFlight.tryAcquire()) {
            return;
        }
        String body = codec.encode(new PetMetricWireEvent(metric, 1));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((ignored, failure) -> {
                        inFlight.release();
                        if (failure != null) {
                            PetCompanionMod.LOGGER.debug(
                                    "Pet metrics delivery failed; diagnostic event dropped", failure);
                        }
                    });
        } catch (RuntimeException failure) {
            inFlight.release();
            PetCompanionMod.LOGGER.debug(
                    "Pet metrics delivery could not be queued; diagnostic event dropped", failure);
        }
    }

    @Override
    public void close() {
        // HttpClient has no close contract; in-flight requests are bounded and harmless at shutdown.
    }
}
