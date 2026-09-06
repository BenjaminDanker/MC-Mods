package com.silver.aipets.service.subscription;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Minimal bounded Stripe API client; secret values never enter URLs or logs. */
public final class StripeHttpCheckoutClient implements StripeCheckoutClient {
    private static final int MAX_RESPONSE_CHARS = 64 * 1_024;

    private final HttpClient client;
    private final URI endpoint;
    private final String secretKey;
    private final Duration timeout;

    public StripeHttpCheckoutClient(String secretKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                URI.create("https://api.stripe.com/v1/checkout/sessions"),
                secretKey, Duration.ofSeconds(10));
    }

    StripeHttpCheckoutClient(
            HttpClient client, URI endpoint, String secretKey, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (!(secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_"))) {
            throw new IllegalArgumentException("secretKey must be a Stripe secret key");
        }
    }

    @Override
    public StripeCheckoutSession create(
            StripeCheckoutRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");
        if (idempotencyKey == null || !idempotencyKey.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(form(request), StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response;
        try {
            response = client.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stripe Checkout request was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Stripe Checkout request failed", failure);
        }
        if (response.statusCode() != 200 || response.body() == null
                || response.body().length() > MAX_RESPONSE_CHARS) {
            throw new IllegalStateException(
                    "Stripe Checkout returned HTTP " + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return new StripeCheckoutSession(
                    json.get("id").getAsString(), URI.create(json.get("url").getAsString()));
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Stripe Checkout returned an invalid response", malformed);
        }
    }

    static String form(StripeCheckoutRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        String owner = request.ownerUuid().toString();
        fields.put("mode", "subscription");
        fields.put("line_items[0][price]", request.priceId());
        fields.put("line_items[0][quantity]", "1");
        fields.put("client_reference_id", owner);
        fields.put("metadata[minecraft_uuid]", owner);
        fields.put("metadata[account_link_hash]", request.accountLinkHash());
        fields.put("metadata[stripe_price_id]", request.priceId());
        fields.put("subscription_data[metadata][minecraft_uuid]", owner);
        fields.put("subscription_data[metadata][account_link_hash]", request.accountLinkHash());
        fields.put("success_url", request.successUrl().toString());
        fields.put("cancel_url", request.cancelUrl().toString());
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
