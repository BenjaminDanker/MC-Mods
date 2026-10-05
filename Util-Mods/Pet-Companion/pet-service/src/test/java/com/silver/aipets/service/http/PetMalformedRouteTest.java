package com.silver.aipets.service.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

final class PetMalformedRouteTest {
    private static final String TOKEN = "test-bearer-token-with-at-least-32-characters";

    @Test void malformedConversationAndExperienceReceiveAResponseWithoutMutatingState()
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/dialogue", new PetDialogueHttpHandler(
                request -> { throw new AssertionError("Malformed input reached responder"); }, TOKEN));
        server.createContext("/v1/experiences", new PetExperienceHttpHandler(
                null, null, TOKEN, null, null));
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            for (String path : new String[] {"/v1/dialogue", "/v1/experiences"}) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                + server.getAddress().getPort() + path))
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer " + TOKEN)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
                assertEquals(400, client.send(request, HttpResponse.BodyHandlers.ofString())
                        .statusCode(), path);
            }
        } finally { server.stop(0); }
    }
}
