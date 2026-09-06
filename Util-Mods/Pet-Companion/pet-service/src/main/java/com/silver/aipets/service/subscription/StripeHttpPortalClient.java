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
import java.util.Objects;

/** Bounded server-side client for Stripe's short-lived hosted Customer Portal sessions. */
public final class StripeHttpPortalClient implements StripePortalClient {
    private static final int MAX_RESPONSE_CHARS = 64 * 1_024;
    private final HttpClient client;
    private final URI endpoint;
    private final String secretKey;
    private final Duration timeout;

    public StripeHttpPortalClient(String secretKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                URI.create("https://api.stripe.com/v1/billing_portal/sessions"),
                secretKey, Duration.ofSeconds(10));
    }

    StripeHttpPortalClient(
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
    public URI create(String customerId, URI returnUrl) {
        if (customerId == null || !customerId.startsWith("cus_")
                || customerId.length() > 255 || customerId.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("customerId is invalid");
        }
        Objects.requireNonNull(returnUrl, "returnUrl");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        form(customerId, returnUrl), StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response;
        try {
            response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stripe Customer Portal request was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Stripe Customer Portal request failed", failure);
        }
        if (response.statusCode() != 200 || response.body() == null
                || response.body().length() > MAX_RESPONSE_CHARS) {
            throw new IllegalStateException(
                    "Stripe Customer Portal returned HTTP " + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            URI portalUrl = URI.create(json.get("url").getAsString());
            if (!"https".equalsIgnoreCase(portalUrl.getScheme())
                    || !"billing.stripe.com".equalsIgnoreCase(portalUrl.getHost())) {
                throw new IllegalArgumentException("unsafe Customer Portal URL");
            }
            return portalUrl;
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Stripe Customer Portal returned an invalid response", malformed);
        }
    }

    static String form(String customerId, URI returnUrl) {
        return "customer=" + encode(customerId) + "&return_url=" + encode(returnUrl.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
