package com.silver.aipets.service.http;

import com.silver.aipets.service.subscription.CheckoutLaunchService;
import com.silver.aipets.service.subscription.ActiveSubscriptionException;
import com.silver.aipets.service.subscription.InvalidAccountLinkException;
import com.silver.aipets.service.subscription.StripeCheckoutSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Public no-input bootstrap that exchanges an opaque URL token for hosted Stripe Checkout. */
public final class CheckoutHttpHandler implements HttpHandler {
    private static final String PREFIX = "/checkout/";
    private final CheckoutLaunchService checkout;
    private final AccountLinkHttpHandler.AttemptLimiter limiter;

    public CheckoutHttpHandler(CheckoutLaunchService checkout, Clock clock) {
        this.checkout = Objects.requireNonNull(checkout, "checkout");
        limiter = new AccountLinkHttpHandler.AttemptLimiter(
                Objects.requireNonNull(clock, "clock"), Duration.ofMinutes(5), 1_000, 4_096);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try {
            if (!"GET".equals(exchange.getRequestMethod())
                    || exchange.getRequestURI().getRawQuery() != null) {
                page(exchange, 404, "Link not found", "Request a new link in Minecraft.");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            if ((PREFIX + "success").equals(path)) {
                page(exchange, 200, "Checkout returned",
                        "You're all set. Return to Minecraft and continue your adoption there.");
                return;
            }
            if ((PREFIX + "cancel").equals(path)) {
                page(exchange, 200, "Checkout cancelled",
                        "No changes were made. Return to Minecraft whenever you're ready.");
                return;
            }
            if ((PREFIX + "return").equals(path)) {
                page(exchange, 200, "Billing portal closed",
                        "Return to Minecraft and we'll let you know when your change is ready.");
                return;
            }
            if (!path.startsWith(PREFIX)
                    || !limiter.allow(exchange.getRemoteAddress().getAddress().getHostAddress())) {
                page(exchange, 404, "Link not found", "Request a new link in Minecraft.");
                return;
            }
            String token = path.substring(PREFIX.length());
            if (token.contains("/")) {
                page(exchange, 404, "Link not found", "Request a new link in Minecraft.");
                return;
            }
            StripeCheckoutSession session = checkout.launch(token);
            exchange.getResponseHeaders().set("Location", session.checkoutUrl().toString());
            exchange.sendResponseHeaders(303, -1);
        } catch (ActiveSubscriptionException alreadyActive) {
            page(exchange, 409, "Subscription already active",
                    "This account already has an active membership. Return to Minecraft to continue.");
        } catch (InvalidAccountLinkException | IllegalArgumentException invalid) {
            page(exchange, 404, "Link expired", "Run /pet adopt in Minecraft for a new link.");
        } catch (RuntimeException failure) {
            page(exchange, 503, "Checkout unavailable", "Please retry this link in a moment.");
        } finally {
            exchange.close();
        }
    }

    private static void page(HttpExchange exchange, int status, String title, String message)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set(
                "Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>"
                + title + "</title></head><body><main><h1>" + title + "</h1><p>"
                + message + "</p></main></body></html>";
        byte[] encoded = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }
}
