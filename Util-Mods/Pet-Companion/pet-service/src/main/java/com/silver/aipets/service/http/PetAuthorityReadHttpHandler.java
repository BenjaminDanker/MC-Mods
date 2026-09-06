package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/** Authenticated, read-only authority routes used by status and entity reconciliation. */
public final class PetAuthorityReadHttpHandler implements HttpHandler {
    public static final String REQUEST_ID_HEADER = PetAuthorityHttpHandler.REQUEST_ID_HEADER;

    private final PetAuthorityHttpHandler delegate;

    public PetAuthorityReadHttpHandler(
            PetRepository repository,
            PetSleepStateReader sleepStateReader,
            PetWireCodec codec,
            String bearerToken) {
        this.delegate = new PetAuthorityHttpHandler(
                repository, sleepStateReader, codec, null, bearerToken);
    }

    public PetAuthorityReadHttpHandler(
            PetRepository repository,
            PetSleepStateReader sleepStateReader,
            SubscriptionAccess subscriptionAccess,
            PetWireCodec codec,
            String bearerToken) {
        this.delegate = new PetAuthorityHttpHandler(
                repository, sleepStateReader, subscriptionAccess, codec, null, bearerToken);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        delegate.handle(exchange);
    }
}
