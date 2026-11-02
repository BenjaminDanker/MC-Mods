package com.silver.aipets.service.subscription;

import java.net.URI;

@FunctionalInterface
public interface StripePortalClient {
    URI create(String customerId, URI returnUrl);
}
