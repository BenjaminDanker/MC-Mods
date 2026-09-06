package com.silver.aipets.service.subscription;

import com.silver.aipets.common.transport.CustomerPortalWireResult;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/** Creates a portal only from the customer identity previously trusted into SQL. */
public final class CustomerPortalService {
    private final SubscriptionCustomerLookup customers;
    private final StripePortalClient stripe;
    private final URI returnUrl;

    public CustomerPortalService(
            SubscriptionCustomerLookup customers,
            StripePortalClient stripe,
            URI publicBaseUri) {
        this.customers = Objects.requireNonNull(customers, "customers");
        this.stripe = Objects.requireNonNull(stripe, "stripe");
        returnUrl = Objects.requireNonNull(publicBaseUri, "publicBaseUri")
                .resolve("/checkout/return");
    }

    public CustomerPortalWireResult create(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return customers.findCustomerId(ownerUuid)
                .map(customer -> CustomerPortalWireResult.created(
                        stripe.create(customer, returnUrl).toString()))
                .orElseGet(CustomerPortalWireResult::notLinked);
    }
}
