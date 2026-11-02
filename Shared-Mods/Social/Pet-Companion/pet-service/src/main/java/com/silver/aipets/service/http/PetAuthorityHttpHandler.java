package com.silver.aipets.service.http;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.transport.PetMutationWireCodec;
import com.silver.aipets.common.transport.PetMutationWireResult;
import com.silver.aipets.common.transport.PetMutationWireStatus;
import com.silver.aipets.common.transport.PetSnapshotWire;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetWireFormatException;
import com.silver.aipets.common.transport.PetRecallWireCodec;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.service.persistence.IdempotentMutationDisposition;
import com.silver.aipets.service.persistence.IdempotentMutationResult;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.placement.PetMutationResult;
import com.silver.aipets.service.placement.PetMutationStatus;
import com.silver.aipets.service.placement.PetPlacementService;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.recall.PetRecallService;
import com.silver.aipets.service.recall.RecallOperationDisposition;
import com.silver.aipets.service.recall.RecallOperationResult;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authenticated authority reads and durable-idempotent physical placement mutations. */
public final class PetAuthorityHttpHandler implements HttpHandler {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final String OWNER_PREFIX = "/v1/pets/by-owner/";
    private static final String PET_PREFIX = "/v1/pets/";
    private static final byte[] NOT_FOUND = jsonError("NOT_FOUND");
    private static final byte[] UNAUTHORIZED = jsonError("UNAUTHORIZED");
    private static final byte[] BAD_REQUEST = jsonError("BAD_REQUEST");
    private static final byte[] TOO_LARGE = jsonError("PAYLOAD_TOO_LARGE");
    private static final byte[] METHOD_NOT_ALLOWED = jsonError("METHOD_NOT_ALLOWED");
    private static final byte[] SERVICE_FAILURE = jsonError("SERVICE_FAILURE");
    private static final byte[] IDEMPOTENCY_REUSE = jsonError("IDEMPOTENCY_KEY_REUSE");
    private static final byte[] OPERATION_IN_PROGRESS = jsonError("OPERATION_IN_PROGRESS");

    private final PetRepository repository;
    private final PetSleepStateReader sleepStateReader;
    private final PetWireCodec petCodec;
    private final PetMutationWireCodec mutationCodec;
    private final PetPlacementService placementService;
    private final PetRecallService recallService;
    private final PetRecallWireCodec recallCodec;
    private final SubscriptionAccess subscriptionAccess;
    private final byte[] expectedAuthorization;

    public PetAuthorityHttpHandler(
            PetRepository repository,
            PetSleepStateReader sleepStateReader,
            PetWireCodec petCodec,
            PetPlacementService placementService,
            String bearerToken) {
        this(
                repository,
                sleepStateReader,
                ignored -> true,
                petCodec,
                placementService,
                null,
                bearerToken);
    }

    public PetAuthorityHttpHandler(
            PetRepository repository,
            PetSleepStateReader sleepStateReader,
            SubscriptionAccess subscriptionAccess,
            PetWireCodec petCodec,
            PetPlacementService placementService,
            String bearerToken) {
        this(repository, sleepStateReader, subscriptionAccess, petCodec, placementService, null, bearerToken);
    }

    public PetAuthorityHttpHandler(
            PetRepository repository,
            PetSleepStateReader sleepStateReader,
            SubscriptionAccess subscriptionAccess,
            PetWireCodec petCodec,
            PetPlacementService placementService,
            PetRecallService recallService,
            String bearerToken) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sleepStateReader = Objects.requireNonNull(sleepStateReader, "sleepStateReader");
        this.subscriptionAccess = Objects.requireNonNull(subscriptionAccess, "subscriptionAccess");
        this.petCodec = Objects.requireNonNull(petCodec, "petCodec");
        this.mutationCodec = new PetMutationWireCodec(petCodec);
        this.recallCodec = new PetRecallWireCodec(petCodec);
        this.placementService = placementService;
        this.recallService = recallService;
        Objects.requireNonNull(bearerToken, "bearerToken");
        if (bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        this.expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set(REQUEST_ID_HEADER, requestId(exchange.getRequestHeaders()));
        responseHeaders.set("Cache-Control", "no-store");
        responseHeaders.set("Content-Type", "application/json; charset=utf-8");
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, UNAUTHORIZED);
                return;
            }
            URI uri = exchange.getRequestURI();
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                send(exchange, 400, BAD_REQUEST);
                return;
            }
            switch (exchange.getRequestMethod()) {
                case "GET" -> handleGet(exchange, uri.getRawPath());
                case "POST" -> handlePost(exchange, uri.getRawPath());
                default -> {
                    responseHeaders.set("Allow", "GET, POST");
                    send(exchange, 405, METHOD_NOT_ALLOWED);
                }
            }
        } catch (PayloadTooLargeException failure) {
            send(exchange, 413, TOO_LARGE);
        } catch (PetWireFormatException | IllegalArgumentException malformed) {
            send(exchange, 400, BAD_REQUEST);
        } catch (RuntimeException failure) {
            send(exchange, 503, SERVICE_FAILURE);
        } finally {
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange, String rawPath) throws IOException {
        Optional<Pet> pet;
        if (rawPath.startsWith(OWNER_PREFIX)) {
            pet = repository.findByOwner(canonicalUuid(rawPath.substring(OWNER_PREFIX.length())));
        } else if (rawPath.startsWith(PET_PREFIX)) {
            pet = repository.findById(canonicalUuid(rawPath.substring(PET_PREFIX.length())));
        } else {
            throw new IllegalArgumentException("Unknown authority read route");
        }
        if (pet.isEmpty()) {
            send(exchange, 404, NOT_FOUND);
            return;
        }
        Pet found = pet.orElseThrow();
        send(exchange, 200, petCodec.encodeSnapshot(new PetSnapshotWire(
                        found,
                        sleepStateReader.isSleeping(found.petId()),
                        subscriptionAccess.canAdopt(found.ownerUuid())))
                .getBytes(StandardCharsets.UTF_8));
    }

    private void handlePost(HttpExchange exchange, String rawPath) throws IOException {
        if (placementService == null) {
            send(exchange, 503, SERVICE_FAILURE);
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            throw new IllegalArgumentException("Mutation content type must be JSON");
        }
        UUID operationId = canonicalUuid(requireHeader(
                exchange.getRequestHeaders(), IDEMPOTENCY_KEY_HEADER));
        MutationRoute route = mutationRoute(rawPath);
        String requestBody = readBody(exchange);
        if (route.operation().equals("recall") || route.operation().equals("compensate-recall-failure")) {
            handleRecallPost(exchange, operationId, route, requestBody);
            return;
        }
        IdempotentMutationResult result = switch (route.operation()) {
            case "place" -> placementService.place(
                    operationId, route.petId(), mutationCodec.decodePlace(requestBody));
            case "compensate-place-failure" -> placementService.compensatePlaceFailure(
                    operationId, route.petId(), mutationCodec.decodeCompensate(requestBody));
            case "pickup" -> placementService.pickup(
                    operationId, route.petId(), mutationCodec.decodePickup(requestBody));
            case "prepare-transfer" -> placementService.prepareTransfer(
                    operationId, route.petId(), mutationCodec.decodePrepareTransfer(requestBody));
            case "complete-transfer" -> placementService.completeTransfer(
                    operationId, route.petId(), mutationCodec.decodeCompleteTransfer(requestBody));
            case "expire-transfer" -> placementService.expireTransfer(
                    operationId, route.petId(), mutationCodec.decodeExpireTransfer(requestBody));
            case "admin-recover" -> placementService.adminRecover(
                    operationId, route.petId(), mutationCodec.decodeAdminRecover(requestBody));
            default -> throw new IllegalArgumentException("Unknown authority mutation route");
        };
        if (result.disposition() == IdempotentMutationDisposition.KEY_CONFLICT) {
            send(exchange, 409, IDEMPOTENCY_REUSE);
            return;
        }
        if (result.disposition() == IdempotentMutationDisposition.IN_PROGRESS) {
            send(exchange, 409, OPERATION_IN_PROGRESS);
            return;
        }
        if (result.disposition() == IdempotentMutationDisposition.REPLAYED) {
            exchange.getResponseHeaders().set(IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        PetMutationResult mutation = result.mutation().orElseThrow();
        send(exchange, httpStatus(mutation.status()), mutationCodec.encodeResult(toWire(mutation))
                .getBytes(StandardCharsets.UTF_8));
    }

    private void handleRecallPost(
            HttpExchange exchange,
            UUID operationId,
            MutationRoute route,
            String requestBody) throws IOException {
        if (recallService == null) {
            send(exchange, 503, SERVICE_FAILURE);
            return;
        }
        RecallOperationResult operation = switch (route.operation()) {
            case "recall" -> recallService.recall(
                    operationId, route.petId(), recallCodec.decodeRecall(requestBody));
            case "compensate-recall-failure" -> recallService.compensateFailure(
                    operationId, route.petId(), recallCodec.decodeCompensation(requestBody));
            default -> throw new IllegalArgumentException("Unknown recall route");
        };
        if (operation.disposition() == RecallOperationDisposition.KEY_CONFLICT) {
            send(exchange, 409, IDEMPOTENCY_REUSE);
            return;
        }
        if (operation.disposition() == RecallOperationDisposition.REPLAYED) {
            exchange.getResponseHeaders().set(IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        PetRecallWireResult result = operation.result().orElseThrow();
        int status = switch (result.status()) {
            case APPLIED, COMPENSATED -> 200;
            case NOT_FOUND -> 404;
            case UNAVAILABLE, REJECTED -> 409;
        };
        send(exchange, status, recallCodec.encodeResult(result).getBytes(StandardCharsets.UTF_8));
    }

    private static MutationRoute mutationRoute(String rawPath) {
        if (!rawPath.startsWith(PET_PREFIX)) {
            throw new IllegalArgumentException("Unknown authority mutation route");
        }
        String[] segments = rawPath.substring(PET_PREFIX.length()).split("/", -1);
        if (segments.length != 2 || segments[1].isBlank()) {
            throw new IllegalArgumentException("Mutation route requires pet and operation");
        }
        return new MutationRoute(canonicalUuid(segments[0]), segments[1]);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        long declared = exchange.getRequestHeaders().getFirst("Content-Length") == null
                ? -1
                : Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declared > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PayloadTooLargeException();
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(PetWireCodec.DEFAULT_MAX_JSON_CHARS + 1);
        if (bytes.length > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PayloadTooLargeException();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static PetMutationWireResult toWire(PetMutationResult result) {
        return new PetMutationWireResult(
                PetMutationWireStatus.valueOf(result.status().name()),
                result.pet(),
                result.failure());
    }

    private static int httpStatus(PetMutationStatus status) {
        return switch (status) {
            case APPLIED -> 200;
            case NOT_FOUND -> 404;
            case REJECTED, CONCURRENT_MODIFICATION -> 409;
        };
    }

    private boolean authenticated(Headers headers) {
        String authorization = headers.getFirst("Authorization");
        return authorization != null && MessageDigest.isEqual(
                expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID canonicalUuid(String encoded) {
        if (encoded.indexOf('/') >= 0 || encoded.indexOf('%') >= 0) {
            throw new IllegalArgumentException("UUID segment must not be escaped or nested");
        }
        UUID value = UUID.fromString(encoded);
        if (!value.toString().equals(encoded)) {
            throw new IllegalArgumentException("UUID is not canonical");
        }
        return value;
    }

    private static String requireHeader(Headers headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requestId(Headers headers) {
        String supplied = headers.getFirst(REQUEST_ID_HEADER);
        if (supplied != null) {
            try {
                UUID parsed = UUID.fromString(supplied);
                if (parsed.toString().equals(supplied)) {
                    return supplied;
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed external correlation IDs.
            }
        }
        return UUID.randomUUID().toString();
    }

    private static byte[] jsonError(String code) {
        return ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private record MutationRoute(UUID petId, String operation) {
    }

    private static final class PayloadTooLargeException extends IllegalArgumentException {
    }
}
