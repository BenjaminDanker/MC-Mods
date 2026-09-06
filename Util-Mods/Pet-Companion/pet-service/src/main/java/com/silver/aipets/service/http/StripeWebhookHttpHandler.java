package com.silver.aipets.service.http;

import com.silver.aipets.service.subscription.StripeSignatureException;
import com.silver.aipets.service.subscription.StripeWebhookApplyStatus;
import com.silver.aipets.service.subscription.StripeWebhookService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Public signed endpoint; raw bytes are retained until Stripe signature verification completes. */
public final class StripeWebhookHttpHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 256 * 1_024;
    private static final String PATH = "/v1/stripe/webhook";

    private final StripeWebhookService webhooks;

    public StripeWebhookHttpHandler(StripeWebhookService webhooks) {
        this.webhooks = Objects.requireNonNull(webhooks, "webhooks");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        try {
            if (!"POST".equals(exchange.getRequestMethod())
                    || !PATH.equals(exchange.getRequestURI().getRawPath())
                    || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            byte[] payload = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (payload.length > MAX_BODY_BYTES) {
                send(exchange, 413, "{\"error\":\"PAYLOAD_TOO_LARGE\"}");
                return;
            }
            StripeWebhookApplyStatus status = webhooks.handle(
                    payload, exchange.getRequestHeaders().getFirst("Stripe-Signature"));
            if (status == StripeWebhookApplyStatus.RETRY_NEEDED) {
                send(exchange, 503, "{\"status\":\"RETRY_NEEDED\"}");
                return;
            }
            send(exchange, 200, "{\"status\":\"" + status.name() + "\"}");
        } catch (StripeSignatureException invalidSignature) {
            send(exchange, 400, "{\"error\":\"INVALID_SIGNATURE\"}");
        } catch (IllegalArgumentException malformed) {
            send(exchange, 400, "{\"error\":\"BAD_EVENT\"}");
        } catch (RuntimeException failure) {
            send(exchange, 503, "{\"error\":\"SERVICE_FAILURE\"}");
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }
}
