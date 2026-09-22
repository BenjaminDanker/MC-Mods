package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PetAdoptionWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetWireFormatException;
import com.silver.aipets.service.adoption.AdoptionResult;
import com.silver.aipets.service.adoption.PetAdoptionService;
import com.silver.aipets.service.adoption.PetAdoptionWorkflowService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/** Authenticated subscription-gated adoption endpoint for trusted gameplay backends. */
public final class PetAdoptionHttpHandler implements HttpHandler {
    private static final byte[] UNAUTHORIZED = error("UNAUTHORIZED");
    private static final byte[] BAD_REQUEST = error("BAD_REQUEST");
    private static final byte[] METHOD_NOT_ALLOWED = error("METHOD_NOT_ALLOWED");
    private static final byte[] SERVICE_FAILURE = error("SERVICE_FAILURE");

    private final PetAdoptionService adoptionService;
    private final PetAdoptionWorkflowService workflowService;
    private final PetAdoptionWireCodec codec;
    private final byte[] expectedAuthorization;

    public PetAdoptionHttpHandler(
            PetAdoptionService adoptionService,
            PetAdoptionWireCodec codec,
            String bearerToken) {
        this(adoptionService, null, codec, bearerToken);
    }

    public PetAdoptionHttpHandler(
            PetAdoptionService adoptionService,
            PetAdoptionWorkflowService workflowService,
            PetAdoptionWireCodec codec,
            String bearerToken) {
        this.adoptionService = Objects.requireNonNull(adoptionService, "adoptionService");
        this.workflowService = workflowService;
        this.codec = Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(bearerToken, "bearerToken");
        if (bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        this.expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set(PetAuthorityHttpHandler.REQUEST_ID_HEADER, requestId(exchange.getRequestHeaders()));
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, UNAUTHORIZED);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                headers.set("Allow", "POST");
                send(exchange, 405, METHOD_NOT_ALLOWED);
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null
                    || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                throw new IllegalArgumentException("Content-Type must be JSON");
            }
            PetAdoptionWireRequest request = codec.decodeRequest(readBody(exchange));
            AdoptionResult adoption = workflowService == null ? adoptionService.adopt(
                    request.ownerUuid(), request.species(), request.name()) : null;
            PetAdoptionWireResult wire = workflowService == null
                    ? new PetAdoptionWireResult(
                            PetAdoptionWireStatus.valueOf(adoption.status().name()), adoption.pet())
                    : workflowService.adopt(request);
            int status = wire.pet().isPresent() ? 200
                    : wire.status() == PetAdoptionWireStatus.CHECKOUT_REQUIRED
                            || wire.status() == PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS ? 200 : 403;
            send(exchange, status,
                    codec.encodeResult(wire).getBytes(StandardCharsets.UTF_8));
        } catch (PetWireFormatException | IllegalArgumentException malformed) {
            send(exchange, 400, BAD_REQUEST);
        } catch (RuntimeException failure) {
            send(exchange, 503, SERVICE_FAILURE);
        } finally {
            exchange.close();
        }
    }

    private boolean authenticated(Headers headers) {
        String authorization = headers.getFirst("Authorization");
        return authorization != null && MessageDigest.isEqual(
                expectedAuthorization,
                authorization.getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(PetWireCodec.DEFAULT_MAX_JSON_CHARS + 1);
        if (bytes.length > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Adoption body is too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String requestId(Headers headers) {
        String supplied = headers.getFirst(PetAuthorityHttpHandler.REQUEST_ID_HEADER);
        if (supplied != null) {
            try {
                UUID parsed = UUID.fromString(supplied);
                if (parsed.toString().equals(supplied)) {
                    return supplied;
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed correlation IDs.
            }
        }
        return UUID.randomUUID().toString();
    }

    private static byte[] error(String code) {
        return ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
