package com.silver.aipets.service.dialogue;

import com.sun.net.httpserver.HttpServer;
import com.silver.aipets.service.billing.AiPricing;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DialogueProviderDiagnosticsTest {
    @Test void capturesActualRequestAndProviderMetadataOnlyInsideDiagnosticScope() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"id\":\"chatcmpl-test\",\"choices\":[{\"finish_reason\":\"stop\","
                    + "\"message\":{\"content\":\"{\\\"reply\\\":\\\"Hello\\\"}\"}}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":12,"
                    + "\"completion_tokens_details\":{\"reasoning_tokens\":3}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("x-request-id", "req-test");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var messages = new ArrayList<String>();
            var client = new OpenAiDialogueModelClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "diagnostic-model", "synthetic-test-key", Duration.ofSeconds(3),
                    "test", AiPricing.defaults(), "low");
            DialogueModelResponse completed;
            try (AutoCloseable scope = DialogueProviderDiagnostics.attach(messages::add)) {
                completed = client.complete(UUID.randomUUID(), new DialoguePrompt("Say hello", 10),
                        "{\"type\":\"object\"}", Duration.ofSeconds(3));
            }
            DialogueProviderDiagnostics.emit("outside scope");
            String body = requestBody.get();
            assertTrue(body.contains("\"reasoning_effort\":\"low\""));
            assertTrue(body.contains("\"max_completion_tokens\":2000"));
            assertTrue(body.contains("\"strict\":true"));
            assertFalse(body.contains("\"stream\":true"));
            assertTrue(messages.stream().anyMatch(s -> s.contains("x_request_id=req-test")));
            assertTrue(messages.stream().anyMatch(s -> s.contains("reasoning_tokens=3")));
            assertEquals("stop", completed.finishReason());
            assertEquals(2000, completed.configuredCompletionLimit());
            assertEquals("req-test", completed.providerRequestId().orElseThrow());
            assertFalse(messages.stream().anyMatch(s -> s.contains("synthetic-test-key")));
            assertFalse(messages.contains("outside scope"));
        } finally { server.stop(0); }
    }
}
