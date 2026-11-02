package com.silver.aipets.service.subscription;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Parses only billing fields used by the entitlement state machine. */
public final class StripeWebhookParser {
    public StripeWebhookEvent parse(byte[] rawPayload) {
        if (rawPayload == null || rawPayload.length == 0) {
            throw new IllegalArgumentException("Stripe payload is empty");
        }
        final JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(rawPayload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Event must be an object");
            root = parsed.getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed Stripe event", malformed);
        }
        String eventId = requiredString(root, "id");
        String eventType = requiredString(root, "type");
        Instant createdAt = epoch(requiredLong(root, "created"));
        StripeWebhookKind kind = kind(eventType);
        if (kind == StripeWebhookKind.UNSUPPORTED) {
            return event(eventId, eventType, kind, createdAt, rawPayload,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), false);
        }

        JsonObject object = requiredObject(requiredObject(root, "data"), "object");
        Optional<UUID> ownerUuid = ownerUuid(object, kind);
        Optional<String> accountLinkHash = metadataString(object, "account_link_hash");
        Optional<String> checkoutSessionId = kind == StripeWebhookKind.CHECKOUT_COMPLETED
                ? Optional.of(requiredString(object, "id")) : Optional.empty();
        Optional<String> customerId = optionalId(object, "customer");
        Optional<String> subscriptionId;
        Optional<String> priceId = Optional.empty();
        Optional<SubscriptionStatus> status = Optional.empty();
        Optional<Instant> periodStart = Optional.empty();
        Optional<Instant> periodEnd = Optional.empty();
        boolean cancelAtPeriodEnd = false;

        if (kind == StripeWebhookKind.CHECKOUT_COMPLETED) {
            if (!"subscription".equals(requiredString(object, "mode"))) {
                throw new IllegalArgumentException("Checkout event is not subscription mode");
            }
            subscriptionId = optionalId(object, "subscription");
            priceId = metadataString(object, "stripe_price_id");
        } else if (kind == StripeWebhookKind.SUBSCRIPTION_CREATED
                || kind == StripeWebhookKind.SUBSCRIPTION_UPDATED
                || kind == StripeWebhookKind.SUBSCRIPTION_DELETED) {
            subscriptionId = Optional.of(requiredString(object, "id"));
            priceId = subscriptionPrice(object);
            status = Optional.of(kind == StripeWebhookKind.SUBSCRIPTION_DELETED
                    ? SubscriptionStatus.CANCELED
                    : SubscriptionStatus.fromStripe(requiredString(object, "status")));
            periodStart = timestamp(object, "current_period_start")
                    .or(() -> itemTimestamp(object, "current_period_start"));
            periodEnd = timestamp(object, "current_period_end")
                    .or(() -> itemTimestamp(object, "current_period_end"));
            cancelAtPeriodEnd = optionalBoolean(object, "cancel_at_period_end").orElse(false);
        } else {
            subscriptionId = invoiceSubscription(object);
            status = Optional.of(kind == StripeWebhookKind.INVOICE_PAID
                    ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PAST_DUE);
            periodStart = invoicePeriod(object, "start");
            periodEnd = invoicePeriod(object, "end");
        }
        return event(eventId, eventType, kind, createdAt, rawPayload,
                ownerUuid, accountLinkHash, checkoutSessionId,
                customerId, subscriptionId, priceId, status,
                periodStart, periodEnd, cancelAtPeriodEnd);
    }

    private static StripeWebhookEvent event(
            String eventId,
            String eventType,
            StripeWebhookKind kind,
            Instant createdAt,
            byte[] payload,
            Optional<UUID> ownerUuid,
            Optional<String> accountLinkHash,
            Optional<String> checkoutSessionId,
            Optional<String> customerId,
            Optional<String> subscriptionId,
            Optional<String> priceId,
            Optional<SubscriptionStatus> status,
            Optional<Instant> periodStart,
            Optional<Instant> periodEnd,
            boolean cancelAtPeriodEnd) {
        return new StripeWebhookEvent(
                eventId, eventType, kind, createdAt, sha256(payload), ownerUuid,
                accountLinkHash, checkoutSessionId,
                customerId, subscriptionId, priceId, status,
                periodStart, periodEnd, cancelAtPeriodEnd);
    }

    private static StripeWebhookKind kind(String type) {
        return switch (type) {
            case "checkout.session.completed" -> StripeWebhookKind.CHECKOUT_COMPLETED;
            case "customer.subscription.created" -> StripeWebhookKind.SUBSCRIPTION_CREATED;
            case "customer.subscription.updated" -> StripeWebhookKind.SUBSCRIPTION_UPDATED;
            case "customer.subscription.deleted" -> StripeWebhookKind.SUBSCRIPTION_DELETED;
            case "invoice.paid" -> StripeWebhookKind.INVOICE_PAID;
            case "invoice.payment_failed" -> StripeWebhookKind.INVOICE_PAYMENT_FAILED;
            default -> StripeWebhookKind.UNSUPPORTED;
        };
    }

    private static Optional<UUID> ownerUuid(JsonObject object, StripeWebhookKind kind) {
        Optional<String> encoded = metadataString(object, "minecraft_uuid");
        if (encoded.isEmpty() && kind == StripeWebhookKind.CHECKOUT_COMPLETED) {
            encoded = optionalString(object, "client_reference_id");
        }
        if (encoded.isEmpty()) {
            JsonObject details = nestedObject(object, "parent", "subscription_details").orElse(null);
            if (details != null) encoded = metadataString(details, "minecraft_uuid");
        }
        return encoded.map(UUID::fromString);
    }

    private static Optional<String> subscriptionPrice(JsonObject subscription) {
        JsonArray data = nestedArray(subscription, "items", "data").orElse(null);
        if (data == null || data.isEmpty() || !data.get(0).isJsonObject()) return Optional.empty();
        JsonObject item = data.get(0).getAsJsonObject();
        Optional<JsonObject> price = optionalObject(item, "price");
        if (price.isPresent()) return optionalString(price.orElseThrow(), "id");
        return optionalObject(item, "plan").flatMap(value -> optionalString(value, "id"));
    }

    private static Optional<String> invoiceSubscription(JsonObject invoice) {
        Optional<String> direct = optionalId(invoice, "subscription");
        if (direct.isPresent()) return direct;
        return nestedObject(invoice, "parent", "subscription_details")
                .flatMap(value -> optionalId(value, "subscription"));
    }

    private static Optional<Instant> invoicePeriod(JsonObject invoice, String field) {
        JsonArray data = nestedArray(invoice, "lines", "data").orElse(null);
        if (data == null) return Optional.empty();
        long selected = field.equals("end") ? Long.MIN_VALUE : Long.MAX_VALUE;
        boolean found = false;
        for (JsonElement element : data) {
            if (!element.isJsonObject()) continue;
            JsonObject period = optionalObject(element.getAsJsonObject(), "period").orElse(null);
            if (period == null) continue;
            Optional<Long> value = optionalLong(period, field);
            if (value.isEmpty()) continue;
            selected = field.equals("end")
                    ? Math.max(selected, value.orElseThrow())
                    : Math.min(selected, value.orElseThrow());
            found = true;
        }
        return found ? Optional.of(epoch(selected)) : Optional.empty();
    }

    private static Optional<Instant> itemTimestamp(JsonObject subscription, String field) {
        JsonArray data = nestedArray(subscription, "items", "data").orElse(null);
        if (data == null || data.isEmpty() || !data.get(0).isJsonObject()) return Optional.empty();
        return timestamp(data.get(0).getAsJsonObject(), field);
    }

    private static Optional<Instant> timestamp(JsonObject object, String field) {
        return optionalLong(object, field).map(StripeWebhookParser::epoch);
    }

    private static Instant epoch(long seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Stripe timestamp must be positive");
        try {
            return Instant.ofEpochSecond(seconds);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Stripe timestamp is invalid", malformed);
        }
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static Optional<String> metadataString(JsonObject object, String field) {
        return optionalObject(object, "metadata").flatMap(value -> optionalString(value, field));
    }

    private static Optional<String> optionalId(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return Optional.empty();
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return Optional.of(value.getAsString());
        }
        if (value.isJsonObject()) return optionalString(value.getAsJsonObject(), "id");
        throw new IllegalArgumentException(field + " must be an ID or object");
    }

    private static String requiredString(JsonObject object, String field) {
        return optionalString(object, field)
                .orElseThrow(() -> new IllegalArgumentException(field + " is required"));
    }

    private static Optional<String> optionalString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return Optional.empty();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String text = value.getAsString();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private static long requiredLong(JsonObject object, String field) {
        return optionalLong(object, field)
                .orElseThrow(() -> new IllegalArgumentException(field + " is required"));
    }

    private static Optional<Long> optionalLong(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return Optional.empty();
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(field + " must be a number");
            }
            return Optional.of(value.getAsLong());
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(field + " must be an integer", malformed);
        }
    }

    private static Optional<Boolean> optionalBoolean(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return Optional.empty();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return Optional.of(value.getAsBoolean());
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        return optionalObject(object, field)
                .orElseThrow(() -> new IllegalArgumentException(field + " object is required"));
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return Optional.empty();
        if (!value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        return Optional.of(value.getAsJsonObject());
    }

    private static Optional<JsonObject> nestedObject(
            JsonObject root, String parent, String child) {
        return optionalObject(root, parent).flatMap(value -> optionalObject(value, child));
    }

    private static Optional<JsonArray> nestedArray(
            JsonObject root, String parent, String child) {
        Optional<JsonObject> container = optionalObject(root, parent);
        if (container.isEmpty()) return Optional.empty();
        JsonElement value = container.orElseThrow().get(child);
        if (value == null || value.isJsonNull()) return Optional.empty();
        if (!value.isJsonArray()) throw new IllegalArgumentException(child + " must be an array");
        return Optional.of(value.getAsJsonArray());
    }
}
