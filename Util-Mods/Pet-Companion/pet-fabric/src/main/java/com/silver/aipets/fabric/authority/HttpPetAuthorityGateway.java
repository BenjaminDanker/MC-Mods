package com.silver.aipets.fabric.authority;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.transport.PetSnapshotWire;
import com.silver.aipets.common.transport.PetMutationWireCodec;
import com.silver.aipets.common.transport.PetMutationWireResult;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetRecallWireCodec;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.AccountLinkWireCodec;
import com.silver.aipets.common.transport.AccountLinkWireResult;
import com.silver.aipets.common.transport.CustomerPortalWireCodec;
import com.silver.aipets.common.transport.CustomerPortalWireResult;
import com.silver.aipets.common.transport.RecallResetWireCodec;
import com.silver.aipets.common.transport.RecallResetWireResult;
import com.silver.aipets.fabric.config.PetServiceClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bounded asynchronous authenticated client for authority reads, adoption, and physical mutations. */
public final class HttpPetAuthorityGateway implements PetAuthorityGateway {
    private static final int MAX_RESPONSE_CHARS = PetWireCodec.DEFAULT_MAX_JSON_CHARS;

    private final PetServiceClientConfig config;
    private final PetWireCodec codec;
    private final PetMutationWireCodec mutationCodec;
    private final PetAdoptionWireCodec adoptionCodec;
    private final PetRecallWireCodec recallCodec;
    private final AccountLinkWireCodec accountLinkCodec;
    private final CustomerPortalWireCodec customerPortalCodec;
    private final RecallResetWireCodec recallResetCodec;
    private final HttpClient client;

    public HttpPetAuthorityGateway(PetServiceClientConfig config, PetWireCodec codec) {
        this(
                config,
                codec,
                HttpClient.newBuilder()
                        .connectTimeout(config.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    public HttpPetAuthorityGateway(
            PetServiceClientConfig config,
            PetWireCodec codec,
            HttpClient client) {
        this.config = Objects.requireNonNull(config, "config");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.mutationCodec = new PetMutationWireCodec(codec);
        this.adoptionCodec = new PetAdoptionWireCodec(codec);
        this.recallCodec = new PetRecallWireCodec(codec);
        this.accountLinkCodec = new AccountLinkWireCodec();
        this.customerPortalCodec = new CustomerPortalWireCodec();
        this.recallResetCodec = new RecallResetWireCodec();
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletionStage<AccountLinkWireResult> createAccountLink(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        URI uri = config.baseUri().resolve("v1/account-links/" + ownerUuid);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            return client.sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeAccountLinkResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private AccountLinkWireResult decodeAccountLinkResponse(HttpResponse<String> response) {
        if (response.statusCode() != 201 && response.statusCode() != 429) {
            throw new PetAuthorityTransportException(
                    "Account-link authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Account-link authority returned non-JSON");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Account-link response is missing or too large");
        }
        try {
            return accountLinkCodec.decodeResult(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException("Account-link response is invalid", malformed);
        }
    }

    @Override
    public CompletionStage<CustomerPortalWireResult> createCustomerPortal(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        URI uri = config.baseUri().resolve("v1/customer-portal/" + ownerUuid);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            return client.sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeCustomerPortalResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CustomerPortalWireResult decodeCustomerPortalResponse(HttpResponse<String> response) {
        if (response.statusCode() != 201
                && response.statusCode() != 404
                && response.statusCode() != 429) {
            throw new PetAuthorityTransportException(
                    "Customer Portal authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Customer Portal authority returned non-JSON");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException(
                    "Customer Portal response is missing or too large");
        }
        try {
            return customerPortalCodec.decode(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException(
                    "Customer Portal response is invalid", malformed);
        }
    }

    @Override
    public CompletionStage<RecallResetWireResult> resetRecall(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        URI uri = config.baseUri().resolve("v1/admin/recall-reset/" + petId);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            return client.sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeRecallResetResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private RecallResetWireResult decodeRecallResetResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200 && response.statusCode() != 429) {
            throw new PetAuthorityTransportException(
                    "Recall reset authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Recall reset authority returned non-JSON");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Recall reset response is missing or too large");
        }
        try {
            return recallResetCodec.decode(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException("Recall reset response is invalid", malformed);
        }
    }

    @Override
    public CompletionStage<PetAdoptionWireResult> adopt(PetAdoptionWireRequest adoption) {
        Objects.requireNonNull(adoption, "adoption");
        URI uri = config.baseUri().resolve("v1/adoptions");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(
                        adoptionCodec.encodeRequest(adoption), StandardCharsets.UTF_8))
                .build();
        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeAdoptionResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private PetAdoptionWireResult decodeAdoptionResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200 && response.statusCode() != 403) {
            throw new PetAuthorityTransportException(
                    "Pet adoption authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Pet adoption authority returned non-JSON");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Pet adoption response is missing or too large");
        }
        try {
            return adoptionCodec.decodeResult(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException("Pet adoption response is invalid", malformed);
        }
    }

    @Override
    public CompletionStage<Optional<PetAuthoritySnapshot>> findByOwner(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return get("v1/pets/by-owner/" + ownerUuid);
    }

    @Override
    public CompletionStage<Optional<PetAuthoritySnapshot>> findByPetId(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        return get("v1/pets/" + petId);
    }

    @Override
    public CompletionStage<AuthorityMutationResult> place(
            UUID operationId,
            UUID petId,
            PetTransitions.Place command) {
        Objects.requireNonNull(command, "command");
        return post(operationId, petId, "place", mutationCodec.encodePlace(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> compensatePlaceFailure(
            UUID operationId,
            UUID petId,
            PetTransitions.CompensatePlaceFailure command) {
        Objects.requireNonNull(command, "command");
        return post(
                operationId,
                petId,
                "compensate-place-failure",
                mutationCodec.encodeCompensate(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> pickup(
            UUID operationId,
            UUID petId,
            PetTransitions.Pickup command) {
        Objects.requireNonNull(command, "command");
        return post(operationId, petId, "pickup", mutationCodec.encodePickup(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> prepareTransfer(
            UUID operationId, UUID petId, PetTransitions.PrepareTransfer command) {
        Objects.requireNonNull(command, "command");
        return post(
                operationId, petId, "prepare-transfer",
                mutationCodec.encodePrepareTransfer(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> completeTransfer(
            UUID operationId, UUID petId, PetTransitions.CompleteTransfer command) {
        Objects.requireNonNull(command, "command");
        return post(
                operationId, petId, "complete-transfer",
                mutationCodec.encodeCompleteTransfer(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> expireTransfer(
            UUID operationId, UUID petId, PetTransitions.ExpireTransfer command) {
        Objects.requireNonNull(command, "command");
        return post(
                operationId, petId, "expire-transfer",
                mutationCodec.encodeExpireTransfer(command));
    }

    @Override
    public CompletionStage<AuthorityMutationResult> adminRecover(
            UUID operationId, UUID petId, PetTransitions.AdminRecover command) {
        Objects.requireNonNull(command, "command");
        return post(
                operationId, petId, "admin-recover",
                mutationCodec.encodeAdminRecover(command));
    }

    @Override
    public CompletionStage<PetRecallWireResult> recall(
            UUID operationId, UUID petId, PetTransitions.Recall command) {
        return postRecall(operationId, petId, "recall", recallCodec.encodeRecall(command));
    }

    @Override
    public CompletionStage<PetRecallWireResult> compensateRecallFailure(
            UUID recallOperationId,
            UUID petId,
            PetTransitions.CompensateRecallFailure command) {
        return postRecall(
                recallOperationId,
                petId,
                "compensate-recall-failure",
                recallCodec.encodeCompensation(command));
    }

    private CompletionStage<Optional<PetAuthoritySnapshot>> get(String relativePath) {
        URI uri = config.baseUri().resolve(relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .GET()
                .build();
        try {
            return client.sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeSnapshotResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private Optional<PetAuthoritySnapshot> decodeSnapshotResponse(HttpResponse<String> response) {
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new PetAuthorityTransportException(
                    "Pet authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Pet authority returned a non-JSON response");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Pet authority response is missing or too large");
        }
        PetSnapshotWire snapshot = codec.decodeSnapshot(body);
        return Optional.of(new PetAuthoritySnapshot(
                snapshot.pet(), snapshot.sleeping(), snapshot.aiAccessEnabled()));
    }

    private CompletionStage<AuthorityMutationResult> post(
            UUID operationId,
            UUID petId,
            String operation,
            String body) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(petId, "petId");
        URI uri = config.baseUri().resolve("v1/pets/" + petId + "/" + operation);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("Idempotency-Key", operationId.toString())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return client.sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeMutationResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private AuthorityMutationResult decodeMutationResponse(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Pet authority returned a non-JSON response");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Pet authority response is missing or too large");
        }
        if (response.statusCode() != 200
                && response.statusCode() != 404
                && response.statusCode() != 409) {
            throw new PetAuthorityTransportException(
                    "Pet authority returned HTTP " + response.statusCode());
        }
        final PetMutationWireResult result;
        try {
            result = mutationCodec.decodeResult(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException(
                    "Pet authority returned an invalid mutation response", malformed);
        }
        return new AuthorityMutationResult(
                AuthorityMutationStatus.valueOf(result.status().name()),
                result.pet(),
                result.failure());
    }

    private CompletionStage<PetRecallWireResult> postRecall(
            UUID operationId, UUID petId, String operation, String body) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(petId, "petId");
        URI uri = config.baseUri().resolve("v1/pets/" + petId + "/" + operation);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("Idempotency-Key", operationId.toString())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(this::decodeRecallResponse);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private PetRecallWireResult decodeRecallResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200
                && response.statusCode() != 404
                && response.statusCode() != 409) {
            throw new PetAuthorityTransportException(
                    "Pet recall authority returned HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("application/json")) {
            throw new PetAuthorityTransportException("Pet recall authority returned non-JSON");
        }
        String body = response.body();
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new PetAuthorityTransportException("Pet recall response is missing or too large");
        }
        try {
            return recallCodec.decodeResult(body);
        } catch (RuntimeException malformed) {
            throw new PetAuthorityTransportException("Pet recall response is invalid", malformed);
        }
    }
}
