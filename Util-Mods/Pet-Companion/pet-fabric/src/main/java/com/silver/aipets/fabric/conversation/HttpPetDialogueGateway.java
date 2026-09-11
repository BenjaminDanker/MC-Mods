package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.transport.PetDialogueWireCodec;
import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.fabric.config.PetServiceClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Authenticated asynchronous client for the central dialogue transport. */
public final class HttpPetDialogueGateway implements PetDialogueGateway {
    private final PetServiceClientConfig config;
    private final PetDialogueWireCodec codec = new PetDialogueWireCodec();
    private final HttpClient client;

    public HttpPetDialogueGateway(PetServiceClientConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public HttpPetDialogueGateway(PetServiceClientConfig config, HttpClient client) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletionStage<PetDialogueResponse> submit(PetDialogueRequest request) {
        Objects.requireNonNull(request, "request");
        PetDialogueWireRequest wire = new PetDialogueWireRequest(
                request.requestId(), request.sessionId(), request.ownerUuid(), request.petId(),
                request.backendId(), request.dimensionId(), request.message());
        URI uri = config.baseUri().resolve("v1/dialogue");
        HttpRequest http = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", request.requestId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(codec.encode(wire), StandardCharsets.UTF_8))
                .build();
        try {
            return client.sendAsync(http, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> decode(response, request));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private PetDialogueResponse decode(HttpResponse<String> response, PetDialogueRequest request) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Dialogue authority returned HTTP " + response.statusCode());
        }
        PetDialogueWireResult result = codec.decodeResult(response.body());
        if (!result.requestId().equals(request.requestId())
                || !result.sessionId().equals(request.sessionId())
                || !result.petId().equals(request.petId())) {
            throw new IllegalStateException("Dialogue authority correlation mismatch");
        }
        return new PetDialogueResponse(
                result.requestId(), result.sessionId(), result.petId(),
                result.status() == PetDialogueWireResult.Status.SUCCEEDED
                        ? PetDialogueResponse.Status.SUCCEEDED : PetDialogueResponse.Status.DENIED,
                result.message());
    }
}
