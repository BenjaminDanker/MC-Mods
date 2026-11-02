package com.silver.aipets.service.vector;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiEmbeddingModelClientTest {
    @Test
    void sendsBoundedEmbeddingRequestAndParsesFixedDimension() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"data\":[{\"embedding\":[0.1,-0.2,0.3]}]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiEmbeddingModelClient client = new OpenAiEmbeddingModelClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "text-embedding-3-small", 3, "secret-key",
                    Duration.ofSeconds(2));
            EmbeddingVector vector = client.embed("compact memory card");
            assertEquals("text-embedding-3-small", vector.model());
            assertArrayEquals(new float[]{0.1f, -0.2f, 0.3f}, vector.values(), 0.0001f);
            assertEquals("Bearer secret-key", authorization.get());
            assertEquals(true, requestBody.get().contains("\"dimensions\":3"));
            assertEquals(true, requestBody.get().contains("compact memory card"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void turnsProviderHttpFailureIntoBoundedOutage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] response = "{\"error\":{\"message\":\"hidden\"}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiEmbeddingModelClient client = new OpenAiEmbeddingModelClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "test-model", 3, "secret-key", Duration.ofSeconds(2));
            assertThrows(VectorStoreUnavailableException.class, () -> client.embed("memory"));
        } finally {
            server.stop(0);
        }
    }
}
