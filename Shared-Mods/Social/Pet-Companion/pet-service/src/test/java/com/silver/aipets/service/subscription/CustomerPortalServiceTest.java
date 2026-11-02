package com.silver.aipets.service.subscription;

import com.silver.aipets.common.transport.CustomerPortalWireResult;
import com.silver.aipets.common.transport.CustomerPortalWireStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerPortalServiceTest {
    private static final UUID OWNER = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Test
    void createsPortalFromPersistedCustomerWithTrustedReturnUrl() {
        AtomicReference<String> customer = new AtomicReference<>();
        AtomicReference<URI> returnUrl = new AtomicReference<>();
        CustomerPortalService service = new CustomerPortalService(
                owner -> {
                    assertEquals(OWNER, owner);
                    return Optional.of("cus_verified_123");
                },
                (customerId, requestedReturnUrl) -> {
                    customer.set(customerId);
                    returnUrl.set(requestedReturnUrl);
                    return URI.create("https://billing.stripe.com/p/session/test_123");
                },
                URI.create("https://pets.example.test/base/ignored"));

        CustomerPortalWireResult result = service.create(OWNER);

        assertEquals(CustomerPortalWireStatus.CREATED, result.status());
        assertEquals("cus_verified_123", customer.get());
        assertEquals(URI.create("https://pets.example.test/checkout/return"), returnUrl.get());
        assertEquals("https://billing.stripe.com/p/session/test_123",
                result.portalUrl().orElseThrow());
        assertEquals("customer=cus_verified_123&return_url=https%3A%2F%2Fpets.example.test%2Fcheckout%2Freturn",
                StripeHttpPortalClient.form(customer.get(), returnUrl.get()));
        assertThrows(IllegalArgumentException.class,
                () -> CustomerPortalWireResult.created("https://example.test/phishing"));
    }

    @Test
    void doesNotContactStripeWithoutVerifiedCustomerBinding() {
        AtomicInteger stripeCalls = new AtomicInteger();
        CustomerPortalService service = new CustomerPortalService(
                owner -> Optional.empty(),
                (customerId, returnUrl) -> {
                    stripeCalls.incrementAndGet();
                    return URI.create("https://billing.stripe.com/p/session/unexpected");
                },
                URI.create("https://pets.example.test"));

        assertEquals(CustomerPortalWireStatus.NOT_LINKED, service.create(OWNER).status());
        assertEquals(0, stripeCalls.get());
    }
}
