package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.subscription.AccountLinkRepository;
import com.silver.aipets.service.subscription.AccountLinkService;
import com.silver.aipets.service.subscription.AccountLinkTarget;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.subscription.StripeCheckoutClient;
import com.silver.aipets.service.subscription.StripeCheckoutRequest;
import com.silver.aipets.service.subscription.StripeCheckoutSession;
import com.silver.aipets.service.subscription.StripeCheckoutState;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAdoptionReplacementTest {
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final String PEPPER = "a-test-only-account-link-pepper-at-least-32-characters";

    @Test
    void replacesPreCheckoutWithNewIntentAndAlias() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Mochi", PendingAdoptionState.PRE_CHECKOUT,
                null, null));
        PetAdoptionWorkflowService workflow = workflow(repository, ignored -> false);

        PetAdoptionWireResult result = workflow.adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.CHECKOUT_REQUIRED, result.status());
        assertEquals(2, repository.intents.size());
        assertEquals(PendingAdoptionState.CANCELLED, repository.intents.get(0).state());
        assertEquals("Mochi", repository.intents.get(0).name());
        assertEquals(PendingAdoptionState.PRE_CHECKOUT, repository.current().state());
        assertEquals(PetSpecies.DOG, repository.current().species());
        assertEquals("Luck", repository.current().name());
        assertTrue(result.checkoutUrl().orElseThrow().matches(
                "https://pets.example.test/checkout/[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}"));
    }

    @Test
    void replacesStartedIntentWhilePreservingOldSessionAssociation() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Mochi", PendingAdoptionState.CHECKOUT_STARTED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        TestStripe stripe = new TestStripe(StripeCheckoutState.OPEN, StripeCheckoutState.EXPIRED);
        PetAdoptionWorkflowService workflow = workflow(repository, ignored -> false, stripe);

        PetAdoptionWireResult result = workflow.adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.CHECKOUT_REQUIRED, result.status());
        assertEquals(PendingAdoptionState.CANCELLED, repository.intents.get(0).state());
        assertEquals("cs_old_session", repository.intents.get(0).checkoutSessionId());
        assertEquals(PendingAdoptionState.PRE_CHECKOUT, repository.current().state());
        assertEquals("Luck", repository.current().name());
        assertNotEquals(repository.intents.get(0).accountLinkHash(),
                repository.current().accountLinkHash());
        assertEquals(List.of("cs_old_session"), stripe.expired);
    }

    @Test
    void entitledOwnerCompletesOnlyCurrentIntentAfterOldPayment() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Mochi", PendingAdoptionState.CANCELLED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        repository.add(intent(PetSpecies.DOG, "Luck", PendingAdoptionState.PRE_CHECKOUT,
                null, null));
        InMemoryPetRepository pets = new InMemoryPetRepository();
        SubscriptionAccess entitled = ignored -> true;
        PetAdoptionService adoptions = new PetAdoptionService(
                pets, entitled,
                new PetRandomizer(AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(), new java.util.Random(7)),
                Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID);

        int completed = new PendingAdoptionCompletionWorker(
                repository, adoptions, entitled, Clock.fixed(NOW, ZoneOffset.UTC)).processBatch();

        assertEquals(1, completed);
        Pet adopted = pets.findByOwner(OWNER).orElseThrow();
        assertEquals("Luck", adopted.name());
        assertEquals(PetSpecies.DOG, adopted.appearance().species());
        assertEquals(PendingAdoptionState.CANCELLED, repository.intents.get(0).state());
        assertEquals(PendingAdoptionState.COMPLETED, repository.current().state());
    }

    @Test
    void completedOldCheckoutCreatesNoSecondCheckoutAndNewestPetWaitsForEntitlement() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Hut", PendingAdoptionState.CHECKOUT_STARTED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        TestStripe stripe = new TestStripe(StripeCheckoutState.COMPLETE, StripeCheckoutState.COMPLETE);
        PetAdoptionWorkflowService workflow = workflow(repository, ignored -> false, stripe);

        PetAdoptionWireResult result = workflow.adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.SUBSCRIPTION_PENDING, result.status());
        assertEquals(0, stripe.createCalls);
        assertEquals(0, stripe.expired.size());
        assertEquals(PendingAdoptionState.CANCELLED, repository.intents.get(0).state());
        assertEquals(PendingAdoptionState.WAITING_FOR_ENTITLEMENT, repository.current().state());
    }

    @Test
    void completionRaceDuringExpireReusesPaymentWithoutCreatingAnotherCheckout() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Hut", PendingAdoptionState.CHECKOUT_STARTED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        TestStripe stripe = new TestStripe(StripeCheckoutState.OPEN, StripeCheckoutState.COMPLETE);

        PetAdoptionWireResult result = workflow(repository, ignored -> false, stripe).adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.SUBSCRIPTION_PENDING, result.status());
        assertEquals(0, stripe.createCalls);
        assertEquals(List.of("cs_old_session"), stripe.expired);
        assertEquals(PendingAdoptionState.CANCELLED, repository.intents.get(0).state());
        assertEquals(PendingAdoptionState.WAITING_FOR_ENTITLEMENT, repository.current().state());
    }

    @Test
    void alreadyEntitledOwnerDoesNotCreateCheckout() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Hut", PendingAdoptionState.CHECKOUT_STARTED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        TestStripe stripe = new TestStripe(StripeCheckoutState.OPEN, StripeCheckoutState.EXPIRED);

        PetAdoptionWireResult result = workflow(repository, ignored -> true, stripe).adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.CREATED, result.status());
        assertEquals("Luck", result.pet().orElseThrow().name());
        assertEquals(0, stripe.createCalls);
        assertTrue(stripe.expired.isEmpty());
    }

    @Test
    void indeterminateStripeClosureDoesNotIssueAnotherCheckout() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Hut", PendingAdoptionState.CHECKOUT_STARTED,
                "cs_old_session", NOW.plusSeconds(86_400)));
        TestStripe stripe = new TestStripe(StripeCheckoutState.OPEN, StripeCheckoutState.OPEN);
        stripe.expireFailure = true;
        PetAdoptionWireResult result = workflow(repository, ignored -> false, stripe).adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));

        assertEquals(PetAdoptionWireStatus.CHECKOUT_CLOSING, result.status());
        assertEquals(0, stripe.createCalls);
        assertEquals(PendingAdoptionState.REPLACEMENT_PENDING, repository.current().state());

        PetAdoptionWireResult retry = workflow(repository, ignored -> false, stripe).adopt(
                new PetAdoptionWireRequest(OWNER, PetSpecies.DOG, "Luck"));
        assertEquals(PetAdoptionWireStatus.CHECKOUT_CLOSING, retry.status());
        assertEquals(PendingAdoptionState.REPLACEMENT_PENDING, repository.current().state(),
                "an indeterminate close must remain retryable and must not permit a new checkout");
        assertEquals(0, stripe.createCalls);
    }

    @Test
    void multipleReplacementsLeaveOnlyNewestEligible() {
        MemoryPendingLinks repository = new MemoryPendingLinks();
        repository.add(intent(PetSpecies.CAT, "Mochi", PendingAdoptionState.CANCELLED,
                "cs_mochi", NOW.plusSeconds(86_400)));
        repository.add(intent(PetSpecies.DOG, "Luck", PendingAdoptionState.CANCELLED,
                "cs_luck", NOW.plusSeconds(86_400)));
        repository.add(intent(PetSpecies.CAT, "Pochi", PendingAdoptionState.PRE_CHECKOUT,
                null, null));
        InMemoryPetRepository pets = new InMemoryPetRepository();
        SubscriptionAccess entitled = ignored -> true;
        PetAdoptionService adoptions = new PetAdoptionService(
                pets, entitled,
                new PetRandomizer(AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(), new java.util.Random(9)),
                Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID);

        new PendingAdoptionCompletionWorker(
                repository, adoptions, entitled, Clock.fixed(NOW, ZoneOffset.UTC)).processBatch();

        assertEquals("Pochi", pets.findByOwner(OWNER).orElseThrow().name());
        assertTrue(repository.intents.subList(0, 2).stream()
                .allMatch(intent -> intent.state() == PendingAdoptionState.CANCELLED));
    }

    private static PetAdoptionWorkflowService workflow(
            MemoryPendingLinks repository, SubscriptionAccess access) {
        return workflow(repository, access, new TestStripe(
                StripeCheckoutState.EXPIRED, StripeCheckoutState.EXPIRED));
    }

    private static PetAdoptionWorkflowService workflow(
            MemoryPendingLinks repository, SubscriptionAccess access, StripeCheckoutClient stripe) {
        PetAdoptionService adoptions = new PetAdoptionService(
                new InMemoryPetRepository(), access,
                new PetRandomizer(AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(), new java.util.Random(5)),
                Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID);
        AccountLinkService links = new AccountLinkService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(15),
                new java.security.SecureRandom(), PEPPER, URI.create("https://pets.example.test"));
        return new PetAdoptionWorkflowService(adoptions, access, repository, links, stripe,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PendingAdoption intent(
            PetSpecies species, String name, PendingAdoptionState state,
            String session, Instant hardExpiry) {
        Instant started = session == null ? null : NOW;
        return new PendingAdoption(
                OWNER, UUID.randomUUID(), species, name, state, NOW, NOW.plusSeconds(900),
                started, hardExpiry, "hash-" + UUID.randomUUID(), session, null,
                null, null, null, false);
    }

    private static final class MemoryPendingLinks implements AccountLinkRepository, PendingAdoptionRepository {
        private final List<PendingAdoption> intents = new ArrayList<>();
        private void add(PendingAdoption pending) {
            intents.add(pending);
        }

        private PendingAdoption current() {
            return intents.getLast();
        }

        @Override
        public Optional<PendingAdoption> find(UUID ownerUuid) {
            return intents.stream().filter(value -> value.ownerUuid().equals(ownerUuid))
                    .reduce((first, second) -> second);
        }

        @Override
        public void cancelUnfinished(UUID ownerUuid, Instant cancelledAt) {
            for (int i = 0; i < intents.size(); i++) {
                PendingAdoption value = intents.get(i);
                if (value.ownerUuid().equals(ownerUuid)
                        && (value.state() == PendingAdoptionState.PRE_CHECKOUT
                            || value.state() == PendingAdoptionState.CHECKOUT_STARTED
                            || value.state() == PendingAdoptionState.REPLACEMENT_PENDING
                            || value.state() == PendingAdoptionState.WAITING_FOR_ENTITLEMENT)) {
                    intents.set(i, copy(value, PendingAdoptionState.CANCELLED));
                }
            }
        }

        @Override
        public Optional<PendingAdoption> beginReplacement(UUID ownerUuid, Instant replacementAt) {
            PendingAdoption value = current();
            if (!value.ownerUuid().equals(ownerUuid)) return Optional.empty();
            if (value.state() == PendingAdoptionState.PRE_CHECKOUT) {
                intents.set(intents.size() - 1, copy(value, PendingAdoptionState.CANCELLED));
                return Optional.of(value);
            }
            if (value.state() == PendingAdoptionState.CHECKOUT_STARTED
                    || value.state() == PendingAdoptionState.REPLACEMENT_PENDING) {
                intents.set(intents.size() - 1, copy(value, PendingAdoptionState.REPLACEMENT_PENDING));
                return Optional.of(value);
            }
            return Optional.empty();
        }

        @Override
        public PendingAdoptionLinkStatus createWithLink(
                UUID ownerUuid, UUID intentId, PetSpecies species, String name, String tokenHash,
                String checkoutAliasHash, Instant createdAt, Instant expiresAt,
                Instant generationWindowStart, int maximumGenerations) {
            cancelUnfinished(ownerUuid, createdAt);
            intents.add(new PendingAdoption(
                    ownerUuid, intentId, species, name, PendingAdoptionState.PRE_CHECKOUT,
                    createdAt, expiresAt, null, null, tokenHash, null, null,
                    null, null, null, false));
            return PendingAdoptionLinkStatus.CREATED;
        }

        @Override
        public PendingAdoptionLinkStatus createWaitingForEntitlement(
                UUID ownerUuid, UUID intentId, PetSpecies species, String name,
                Instant createdAt, Instant expiresAt) {
            cancelUnfinished(ownerUuid, createdAt);
            intents.add(new PendingAdoption(
                    ownerUuid, intentId, species, name, PendingAdoptionState.WAITING_FOR_ENTITLEMENT,
                    createdAt, expiresAt, null, null, null, null, null,
                    null, null, null, false));
            return PendingAdoptionLinkStatus.CREATED;
        }

        @Override
        public PendingAdoptionLinkStatus rotatePreCheckoutLink(
                UUID ownerUuid, UUID intentId, String tokenHash, String checkoutAliasHash,
                Instant createdAt, Instant linkExpiresAt, Instant generationWindowStart,
                int maximumGenerations) {
            return PendingAdoptionLinkStatus.CHECKOUT_IN_PROGRESS;
        }

        @Override
        public List<PendingAdoption> findEligibleForCompletion(int limit) {
            PendingAdoption value = current();
            return value.state() == PendingAdoptionState.PRE_CHECKOUT
                    || value.state() == PendingAdoptionState.CHECKOUT_STARTED
                    || value.state() == PendingAdoptionState.WAITING_FOR_ENTITLEMENT
                    ? List.of(value) : List.of();
        }

        @Override
        public boolean complete(UUID ownerUuid, UUID intentId, UUID petId, Instant completedAt) {
            for (int i = 0; i < intents.size(); i++) {
                PendingAdoption value = intents.get(i);
                if (value.ownerUuid().equals(ownerUuid) && value.intentId().equals(intentId)) {
                    intents.set(i, copy(value, PendingAdoptionState.COMPLETED, petId, completedAt));
                    return true;
                }
            }
            return false;
        }

        @Override public void expireUnstarted(Instant now) { }
        @Override public CheckoutLaunchClaim claimCheckoutStart(String tokenHash, Instant now, Instant staleClaimBefore) {
            return CheckoutLaunchClaim.CLAIMED;
        }
        @Override public void releaseCheckoutStart(String tokenHash, Instant claimedAt) { }
        @Override public Optional<PendingAdoptionNotice> findNotification(UUID ownerUuid) { return Optional.empty(); }
        @Override public boolean acknowledgeNotification(UUID ownerUuid, UUID intentId, Instant acknowledgedAt) { return true; }
        @Override public boolean create(UUID ownerUuid, String tokenHash, String aliasHash, Instant createdAt, Instant expiresAt, Instant windowStart, int max) { return true; }
        @Override public Optional<AccountLinkTarget> findValidAlias(String hash, Instant checkedAt) { return Optional.empty(); }
        @Override public boolean attachCheckout(String tokenHash, String session, Instant startedAt) { return true; }

        private static PendingAdoption copy(PendingAdoption value, PendingAdoptionState state) {
            return new PendingAdoption(value.ownerUuid(), value.intentId(), value.species(), value.name(),
                    state, value.createdAt(), value.expiresAt(), value.checkoutStartedAt(),
                    value.hardExpiresAt(), value.accountLinkHash(), value.checkoutSessionId(),
                    value.checkoutLaunchClaimedAt(), value.checkoutCompletedAt(), value.completedPetId(),
                    value.completedAt(), value.notificationPending());
        }

        private static PendingAdoption copy(PendingAdoption value, PendingAdoptionState state,
                                            UUID petId, Instant completedAt) {
            return new PendingAdoption(value.ownerUuid(), value.intentId(), value.species(), value.name(),
                    state, value.createdAt(), value.expiresAt(), value.checkoutStartedAt(),
                    value.hardExpiresAt(), value.accountLinkHash(), value.checkoutSessionId(),
                    value.checkoutLaunchClaimedAt(), value.checkoutCompletedAt(), petId,
                    completedAt, true);
        }
    }

    private static final class TestStripe implements StripeCheckoutClient {
        private final StripeCheckoutState initialState;
        private final StripeCheckoutState postExpireState;
        private final List<String> expired = new ArrayList<>();
        private boolean expireFailure;
        private int createCalls;

        private TestStripe(StripeCheckoutState initialState, StripeCheckoutState postExpireState) {
            this.initialState = initialState;
            this.postExpireState = postExpireState;
        }

        @Override public StripeCheckoutSession create(StripeCheckoutRequest request, String idempotencyKey) {
            createCalls++;
            return new StripeCheckoutSession(
                    "cs_new_session", URI.create("https://checkout.stripe.com/c/pay/new"));
        }

        @Override public StripeCheckoutState retrieve(String checkoutSessionId) {
            return expired.isEmpty() ? initialState : postExpireState;
        }

        @Override public StripeCheckoutState expire(String checkoutSessionId) {
            if (expireFailure) throw new IllegalStateException("timeout");
            expired.add(checkoutSessionId);
            return postExpireState;
        }
    }
}
