package com.silver.aipets.velocity;

import com.silver.aipets.common.transport.PetPresenceWireCodec;
import com.silver.aipets.common.transport.PetPresenceWireRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Non-blocking authenticated client; redirects are disabled to avoid forwarding credentials. */
public final class HttpPresenceGateway implements PresenceGateway {
    private final PetPresenceClientConfig config;
    private final HttpClient client;
    private final PetPresenceWireCodec codec = new PetPresenceWireCodec();

    public HttpPresenceGateway(PetPresenceClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public CompletionStage<Void> publish(PetPresenceWireRequest presence) {
        HttpRequest request = HttpRequest.newBuilder(
                        config.serviceBaseUri().resolve("/v1/presence"))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(codec.encode(presence)))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new PresencePublishException("Presence service returned " + response.statusCode());
                    }
                    return null;
                });
    }

    public static final class PresencePublishException extends RuntimeException {
        public PresencePublishException(String message) { super(message); }
    }
}
