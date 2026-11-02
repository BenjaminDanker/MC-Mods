package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PetPresenceWireCodec;
import com.silver.aipets.common.transport.PetPresenceWireRequest;
import com.silver.aipets.common.transport.PetPresenceReconcileWireCodec;
import com.silver.aipets.common.transport.PetPresenceReconcileWireRequest;
import com.silver.aipets.service.sleep.InMemoryPetSleepStateStore;
import com.silver.aipets.service.sleep.PetSleepEvent;
import com.silver.aipets.service.sleep.PetSleepPolicy;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.sleep.PetSleepState;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPresenceHttpHandlerTest {
    private static final String TOKEN = "service-token-0123456789-0123456789-ab";
    private static final Instant START = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void authenticatesAndAppliesOrderedWholeNetworkPresenceToSleepDeadline() throws Exception {
        UUID owner = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID pet = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID absence = UUID.fromString("30000000-0000-0000-0000-000000000001");
        InMemoryPetSleepStateStore store = new InMemoryPetSleepStateStore();
        store.put(owner, PetSleepState.initial(pet, START, PetSleepPolicy.defaults()));
        PetSleepService ingestion = new PetSleepService(
                store, PetSleepPolicy.defaults(), Clock.fixed(START, ZoneOffset.UTC));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/presence", new PetPresenceHttpHandler(ingestion, TOKEN));
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/presence");
            PetPresenceWireCodec codec = new PetPresenceWireCodec();
            HttpClient client = HttpClient.newHttpClient();

            assertEquals(200, send(client, endpoint, TOKEN, codec.encode(
                    new PetPresenceWireRequest(owner, true, Optional.empty(), START))).statusCode());
            Instant logout = START.plusSeconds(60);
            assertEquals(200, send(client, endpoint, TOKEN, codec.encode(
                    new PetPresenceWireRequest(owner, false, Optional.of(absence), logout))).statusCode());
            assertEquals(200, send(client, endpoint, TOKEN, codec.encode(
                    new PetPresenceWireRequest(owner, false, Optional.of(UUID.randomUUID()),
                            logout.plusSeconds(20)))).statusCode());
            assertEquals(401, send(client, endpoint, TOKEN + "wrong", codec.encode(
                    new PetPresenceWireRequest(owner, true, Optional.empty(), START))).statusCode());

            // Delayed pre-logout online delivery is ignored by its source timestamp.
            send(client, endpoint, TOKEN, codec.encode(
                    new PetPresenceWireRequest(owner, true, Optional.empty(), START.plusSeconds(30))));
            assertFalse(store.find(pet).orElseThrow().ownerNetworkOnline());
            assertEquals(logout, store.find(pet).orElseThrow().ownerLastLogoutAt().orElseThrow());
            assertEquals(absence, store.find(pet).orElseThrow().ownerAbsenceSessionId().orElseThrow());

            PetSleepService due = new PetSleepService(
                    store, PetSleepPolicy.defaults(),
                    Clock.fixed(logout.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
            assertEquals(PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT,
                    due.processDue(10).getFirst().event());
            assertTrue(store.find(pet).orElseThrow().sleeping());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void proxyRestartReconciliationMarksOnlyCurrentPlayersOnline() throws Exception {
        UUID staleOwner = UUID.fromString("10000000-0000-0000-0000-000000000011");
        UUID currentOwner = UUID.fromString("10000000-0000-0000-0000-000000000012");
        InMemoryPetSleepStateStore store = new InMemoryPetSleepStateStore();
        store.put(staleOwner, PetSleepState.initial(
                UUID.fromString("20000000-0000-0000-0000-000000000011"), START,
                PetSleepPolicy.defaults()));
        store.put(currentOwner, PetSleepState.initial(
                UUID.fromString("20000000-0000-0000-0000-000000000012"), START,
                PetSleepPolicy.defaults()));
        PetSleepService ingestion = new PetSleepService(
                store, PetSleepPolicy.defaults(), Clock.fixed(START, ZoneOffset.UTC));
        ingestion.ownerOnline(staleOwner);
        ingestion.ownerOnline(currentOwner);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/presence", new PetPresenceHttpHandler(ingestion, TOKEN));
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/presence/reconcile");
            PetPresenceReconcileWireCodec codec = new PetPresenceReconcileWireCodec();
            HttpResponse<String> response = send(client(), endpoint, TOKEN, codec.encode(
                    new PetPresenceReconcileWireRequest(
                            Set.of(currentOwner), START.plusSeconds(60))));
            assertEquals(200, response.statusCode());
            assertFalse(store.find(UUID.fromString("20000000-0000-0000-0000-000000000011"))
                    .orElseThrow().ownerNetworkOnline());
            assertTrue(store.find(UUID.fromString("20000000-0000-0000-0000-000000000012"))
                    .orElseThrow().ownerNetworkOnline());
        } finally {
            server.stop(0);
        }
    }

    private static HttpClient client() {
        return HttpClient.newHttpClient();
    }

    private static HttpResponse<String> send(
            HttpClient client, URI endpoint, String token, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
