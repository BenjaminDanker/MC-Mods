package com.silver.aipets.service.subscription;

import com.silver.aipets.common.transport.AccountLinkWireResult;
import com.silver.aipets.common.transport.AccountLinkWireStatus;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeBillingFlowTest {
    private static final String SECRET = "whsec_fixture_signing_secret";
    private static final String PRICE = "price_pet_monthly";
    private static final UUID OWNER = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Test
    void verifiesRawSignaturesThenAppliesAndDeduplicatesTrustedEvents() throws Exception {
        MutableClock clock = new MutableClock(Instant.ofEpochSecond(2_000_000_000L));
        MemoryWebhookRepository repository = new MemoryWebhookRepository(OWNER);
        StripeWebhookService service = new StripeWebhookService(
                new StripeSignatureVerifier(SECRET, clock),
                new StripeWebhookParser(), repository, clock, PRICE, 3);

        byte[] checkout = fixture("checkout.session.completed.json");
        assertEquals(StripeWebhookApplyStatus.APPLIED,
                service.handle(checkout, sign(checkout, clock.instant())));
        assertFalse(repository.state.aiAccessEnabled(),
                "Checkout redirect/session completion must not grant AI access");

        clock.set(Instant.ofEpochSecond(2_000_000_010L));
        byte[] active = fixture("customer.subscription.created.json");
        String signature = sign(active, clock.instant());
        assertEquals(StripeWebhookApplyStatus.APPLIED, service.handle(active, signature));
        assertTrue(repository.state.aiAccessEnabled());
        assertEquals(SubscriptionStatus.ACTIVE, repository.state.status());
        assertEquals(StripeWebhookApplyStatus.DUPLICATE, service.handle(active, signature));
        assertEquals(2, repository.processedPayloads.size());

        byte[] tampered = (new String(active, StandardCharsets.UTF_8) + " ")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(StripeSignatureException.class,
                () -> service.handle(tampered, signature));
        assertEquals(2, repository.processedPayloads.size());
    }

    @Test
    void fixtureLifecycleConvergesWithoutRegressionAndHonorsGrace() throws Exception {
        StripeWebhookParser parser = new StripeWebhookParser();
        SubscriptionState state = inactive();

        StripeWebhookEvent active = parser.parse(fixture("customer.subscription.created.json"));
        SubscriptionState wrongPrice = SubscriptionStateReducer.applyEntitlement(
                inactive(), withPrice(active, "price_unconfigured"), active.createdAt(), PRICE, 3);
        assertFalse(wrongPrice.aiAccessEnabled());
        state = SubscriptionStateReducer.applyEntitlement(
                state, active, active.createdAt(), PRICE, 3);
        assertTrue(state.aiAccessEnabled());

        StripeWebhookEvent canceling = parser.parse(
                fixture("customer.subscription.updated.canceling.json"));
        state = SubscriptionStateReducer.applyEntitlement(
                state, canceling, canceling.createdAt(), PRICE, 3);
        assertTrue(state.cancelAtPeriodEnd());
        assertTrue(state.aiAccessEnabled(), "Access remains through the paid period");

        StripeWebhookEvent failed = parser.parse(fixture("invoice.payment_failed.json"));
        state = SubscriptionStateReducer.applyEntitlement(
                state, failed, failed.createdAt(), PRICE, 3);
        assertEquals(SubscriptionStatus.PAST_DUE, state.status());
        assertTrue(state.aiAccessEnabled());
        Instant originalGraceEnd = state.graceEndsAt();
        StripeWebhookEvent repeatedFailure = event(
                "evt_invoice_failed_002", StripeWebhookKind.INVOICE_PAYMENT_FAILED,
                failed.createdAt().plusSeconds(1), SubscriptionStatus.PAST_DUE);
        state = SubscriptionStateReducer.applyEntitlement(
                state, repeatedFailure, repeatedFailure.createdAt(), PRICE, 3);
        assertEquals(originalGraceEnd, state.graceEndsAt(),
                "Repeated past-due updates must not extend the grace window");
        assertFalse(SubscriptionEntitlementPolicy.allows(
                state.status(), true, state.cancelAtPeriodEnd(), state.currentPeriodEnd(),
                state.graceEndsAt(), failed.createdAt().plus(4, java.time.temporal.ChronoUnit.DAYS)));

        StripeWebhookEvent paid = parser.parse(fixture("invoice.paid.json"));
        state = SubscriptionStateReducer.applyEntitlement(state, paid, paid.createdAt(), PRICE, 3);
        assertEquals(SubscriptionStatus.ACTIVE, state.status());
        assertTrue(state.aiAccessEnabled(), "A successful renewal restores access immediately");

        StripeWebhookEvent deleted = parser.parse(
                fixture("customer.subscription.deleted.json"));
        state = SubscriptionStateReducer.applyEntitlement(
                state, deleted, deleted.createdAt(), PRICE, 3);
        assertEquals(SubscriptionStatus.CANCELED, state.status());
        assertFalse(state.aiAccessEnabled());
        assertEquals(OWNER, state.ownerUuid());

        MemoryWebhookRepository repository = new MemoryWebhookRepository(OWNER);
        repository.state = state;
        assertEquals(StripeWebhookApplyStatus.STALE,
                repository.apply(active, deleted.createdAt(), PRICE, 3));
        assertEquals(SubscriptionStatus.CANCELED, repository.state.status());
    }

    @Test
    void allSupportedStripeFixturesParseToExpectedKinds() throws Exception {
        StripeWebhookParser parser = new StripeWebhookParser();
        Map<String, StripeWebhookKind> fixtures = Map.of(
                "checkout.session.completed.json", StripeWebhookKind.CHECKOUT_COMPLETED,
                "customer.subscription.created.json", StripeWebhookKind.SUBSCRIPTION_CREATED,
                "customer.subscription.updated.canceling.json", StripeWebhookKind.SUBSCRIPTION_UPDATED,
                "invoice.payment_failed.json", StripeWebhookKind.INVOICE_PAYMENT_FAILED,
                "invoice.paid.json", StripeWebhookKind.INVOICE_PAID,
                "customer.subscription.deleted.json", StripeWebhookKind.SUBSCRIPTION_DELETED);
        for (Map.Entry<String, StripeWebhookKind> fixture : fixtures.entrySet()) {
            assertEquals(fixture.getValue(), parser.parse(fixture(fixture.getKey())).kind());
        }
    }

    @Test
    void accountLinkUrlIsOpaqueHashedExpiringAndUuidBound() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
        MemoryLinkRepository repository = new MemoryLinkRepository();
        AccountLinkService links = new AccountLinkService(
                repository, clock, Duration.ofMinutes(10), new SecureRandom(),
                "a-test-only-account-link-pepper-at-least-32-characters",
                URI.create("https://pets.example.test"));

        AccountLinkWireResult generated = links.generate(OWNER);
        assertEquals(AccountLinkWireStatus.CREATED, generated.status());
        URI checkoutUrl = URI.create(generated.checkoutUrl().orElseThrow());
        String token = checkoutUrl.getPath().substring("/checkout/".length());
        assertEquals("pets.example.test", checkoutUrl.getHost());
        assertEquals(43, token.length());
        assertEquals(64, repository.tokens.getFirst().hash.length());
        assertNotEquals(token, repository.tokens.getFirst().hash);
        assertEquals(OWNER, links.resolve(token).orElseThrow().ownerUuid());
        assertThrows(IllegalArgumentException.class,
                () -> links.resolve(token.substring(0, 42) + "!"));

        assertEquals(AccountLinkWireStatus.CREATED, links.generate(OWNER).status());
        AccountLinkWireResult expiring = links.generate(OWNER);
        assertEquals(AccountLinkWireStatus.RATE_LIMITED, links.generate(OWNER).status());
        clock.set(clock.instant().plus(Duration.ofMinutes(11)));
        String expiringToken = URI.create(expiring.checkoutUrl().orElseThrow())
                .getPath().substring("/checkout/".length());
        assertEquals(Optional.empty(), links.resolve(expiringToken));
    }

    @Test
    void checkoutLaunchPassesLinkWithoutTypingAndRetriesIdempotently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
        MemoryLinkRepository repository = new MemoryLinkRepository();
        URI publicBase = URI.create("https://pets.example.test");
        AccountLinkService links = new AccountLinkService(
                repository, clock, Duration.ofMinutes(10), new SecureRandom(),
                "a-test-only-account-link-pepper-at-least-32-characters", publicBase);
        String generatedUrl = links.generate(OWNER).checkoutUrl().orElseThrow();
        String token = URI.create(generatedUrl).getPath().substring("/checkout/".length());
        RecordingCheckoutClient stripe = new RecordingCheckoutClient();
        CheckoutLaunchService checkout = new CheckoutLaunchService(
                links, stripe, PRICE, publicBase);

        StripeCheckoutSession first = checkout.launch(token);
        StripeCheckoutSession retry = checkout.launch(token);

        assertEquals(first, retry);
        assertEquals(2, stripe.calls);
        assertEquals(repository.tokens.getFirst().hash, stripe.lastIdempotencyKey);
        assertEquals(OWNER, stripe.lastRequest.ownerUuid());
        assertEquals(repository.tokens.getFirst().hash, stripe.lastRequest.accountLinkHash());
        assertEquals("cs_test_linked", repository.tokens.getFirst().checkoutSessionId);
        char replacement = token.charAt(42) == 'A' ? 'B' : 'A';
        String altered = token.substring(0, 42) + replacement;
        assertThrows(InvalidAccountLinkException.class, () -> checkout.launch(altered));
    }

    @Test
    void stripeCheckoutFormIsSubscriptionModeAndServerBound() {
        String hash = "a".repeat(64);
        String form = StripeHttpCheckoutClient.form(new StripeCheckoutRequest(
                OWNER, hash, PRICE,
                URI.create("https://pets.example.test/checkout/success"),
                URI.create("https://pets.example.test/checkout/cancel")));

        assertTrue(form.contains("mode=subscription"));
        assertTrue(form.contains("line_items%5B0%5D%5Bprice%5D=" + PRICE));
        assertTrue(form.contains("client_reference_id=" + OWNER));
        assertTrue(form.contains("metadata%5Baccount_link_hash%5D=" + hash));
        assertTrue(form.contains("subscription_data%5Bmetadata%5D%5Bminecraft_uuid%5D=" + OWNER));
        assertFalse(form.contains("success_url=http%3A"));
    }

    private static SubscriptionState inactive() {
        return new SubscriptionState(
                OWNER, null, null, null, SubscriptionStatus.INACTIVE, false,
                null, null, false, null, null);
    }

    private static StripeWebhookEvent event(
            String id, StripeWebhookKind kind, Instant createdAt, SubscriptionStatus status) {
        return new StripeWebhookEvent(
                id, "test." + kind.name().toLowerCase(), kind, createdAt,
                "0".repeat(64), Optional.of(OWNER), Optional.empty(), Optional.empty(),
                Optional.of("cus_test_001"),
                Optional.of("sub_test_001"), Optional.of(PRICE), Optional.of(status),
                Optional.empty(), Optional.empty(), false);
    }

    private static StripeWebhookEvent withPrice(StripeWebhookEvent event, String priceId) {
        return new StripeWebhookEvent(
                event.eventId(), event.eventType(), event.kind(), event.createdAt(),
                event.payloadSha256(), event.ownerUuid(), event.accountLinkHash(),
                event.checkoutSessionId(), event.customerId(), event.subscriptionId(),
                Optional.of(priceId), event.subscriptionStatus(), event.currentPeriodStart(),
                event.currentPeriodEnd(), event.cancelAtPeriodEnd());
    }

    private static byte[] fixture(String name) throws IOException {
        try (java.io.InputStream stream = StripeBillingFlowTest.class
                .getResourceAsStream("/stripe/" + name)) {
            if (stream == null) throw new IOException("Missing fixture " + name);
            return stream.readAllBytes();
        }
    }

    private static String sign(byte[] payload, Instant timestamp)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(timestamp.getEpochSecond()).getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        String digest = HexFormat.of().formatHex(mac.doFinal(payload));
        return "t=" + timestamp.getEpochSecond() + ",v1=" + digest;
    }

    private static final class MemoryWebhookRepository implements StripeWebhookRepository {
        private final Map<String, String> processedPayloads = new HashMap<>();
        private SubscriptionState state;

        private MemoryWebhookRepository(UUID ownerUuid) {
            state = inactive();
            if (!ownerUuid.equals(state.ownerUuid())) throw new IllegalArgumentException();
        }

        @Override
        public StripeWebhookApplyStatus apply(
                StripeWebhookEvent event,
                Instant processedAt,
                String configuredPriceId,
                int paymentGraceDays) {
            String priorDigest = processedPayloads.putIfAbsent(
                    event.eventId(), event.payloadSha256());
            if (priorDigest != null) {
                if (!priorDigest.equals(event.payloadSha256())) throw new IllegalStateException();
                return StripeWebhookApplyStatus.DUPLICATE;
            }
            if (event.kind() == StripeWebhookKind.UNSUPPORTED) {
                return StripeWebhookApplyStatus.IGNORED;
            }
            if (event.ownerUuid().isPresent()
                    && !event.ownerUuid().orElseThrow().equals(state.ownerUuid())) {
                return StripeWebhookApplyStatus.REJECTED;
            }
            if (event.kind() == StripeWebhookKind.CHECKOUT_COMPLETED) {
                state = SubscriptionStateReducer.bindCheckout(state, event);
                return StripeWebhookApplyStatus.APPLIED;
            }
            if (state.lastStripeEventAt() != null
                    && event.createdAt().isBefore(state.lastStripeEventAt())) {
                return StripeWebhookApplyStatus.STALE;
            }
            state = SubscriptionStateReducer.applyEntitlement(
                    state, event, processedAt, configuredPriceId, paymentGraceDays);
            return StripeWebhookApplyStatus.APPLIED;
        }
    }

    private static final class MemoryLinkRepository implements AccountLinkRepository {
        private final List<Token> tokens = new ArrayList<>();

        @Override
        public boolean create(
                UUID ownerUuid,
                String tokenHash,
                Instant createdAt,
                Instant expiresAt,
                Instant windowStart,
                int maximumGenerations) {
            long count = tokens.stream()
                    .filter(token -> token.owner.equals(ownerUuid))
                    .filter(token -> !token.createdAt.isBefore(windowStart))
                    .count();
            if (count >= maximumGenerations) return false;
            tokens.stream().filter(token -> token.owner.equals(ownerUuid))
                    .filter(token -> token.consumedAt == null)
                    .forEach(token -> token.consumedAt = createdAt);
            tokens.add(new Token(ownerUuid, tokenHash, createdAt, expiresAt));
            return true;
        }

        @Override
        public Optional<AccountLinkTarget> findValid(String tokenHash, Instant checkedAt) {
            for (Token token : tokens) {
                if (token.hash.equals(tokenHash) && token.consumedAt == null
                        && token.expiresAt.isAfter(checkedAt)) {
                    return Optional.of(new AccountLinkTarget(token.owner, token.hash));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean attachCheckout(
                String tokenHash, String checkoutSessionId, Instant checkoutStartedAt) {
            for (Token token : tokens) {
                if (token.hash.equals(tokenHash) && token.consumedAt == null
                        && token.expiresAt.isAfter(checkoutStartedAt)
                        && (token.checkoutSessionId == null
                            || token.checkoutSessionId.equals(checkoutSessionId))) {
                    token.checkoutSessionId = checkoutSessionId;
                    return true;
                }
            }
            return false;
        }
    }

    private static final class RecordingCheckoutClient implements StripeCheckoutClient {
        private final StripeCheckoutSession session = new StripeCheckoutSession(
                "cs_test_linked", URI.create("https://checkout.stripe.com/c/pay/test"));
        private int calls;
        private StripeCheckoutRequest lastRequest;
        private String lastIdempotencyKey;

        @Override
        public StripeCheckoutSession create(
                StripeCheckoutRequest request, String idempotencyKey) {
            calls++;
            lastRequest = request;
            lastIdempotencyKey = idempotencyKey;
            return session;
        }
    }

    private static final class Token {
        private final UUID owner;
        private final String hash;
        private final Instant createdAt;
        private final Instant expiresAt;
        private Instant consumedAt;
        private String checkoutSessionId;

        private Token(UUID owner, String hash, Instant createdAt, Instant expiresAt) {
            this.owner = owner;
            this.hash = hash;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("UTC only");
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
