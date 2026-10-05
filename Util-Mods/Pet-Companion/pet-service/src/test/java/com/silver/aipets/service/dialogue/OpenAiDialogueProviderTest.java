package com.silver.aipets.service.dialogue;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenAiDialogueProviderTest {
    private static final String TOKEN = "test-openai-key";
    private static final String OUTPUT = """
            {"reply":"Hello, owner!","importance":"LOW","memory_candidate":null,
             "trait_deltas":{"curiosity":0,"boldness":0,"playfulness":0,"expressiveness":0,
             "independence":0,"attachment":0,"trust":0,"security":0},
             "mood_deltas":{"content":0,"excited":0,"anxious":0,"tired":0}}
            """.replaceAll("\\s+", "");

    @Test
    void sendsStrictSchemaAndParsesUsage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            assertTrue(body.contains("json_schema"));
            assertTrue(body.contains("\"model\":\"gpt-6-luna\""));
            assertTrue(body.contains("\"reasoning_effort\":\"none\""));
            assertTrue(body.contains("\"max_completion_tokens\":2000"));
            var messages = com.google.gson.JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
            assertEquals("Pet context and instructions", messages.get(0).getAsJsonObject().get("content").getAsString());
            assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
            assertEquals("I was thinking about going back there.", messages.get(1).getAsJsonObject().get("content").getAsString());
            assertFalse(body.contains("\"stream\":true"));
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            String responseJson = "{\"id\":\"chatcmpl_test\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + quote(OUTPUT) + "}}],\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":12,"
                    + "\"prompt_tokens_details\":{\"cached_tokens\":4}}}";
            byte[] response = responseJson.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiDialogueModelClient client = new OpenAiDialogueModelClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "gpt-6-luna", TOKEN, Duration.ofSeconds(2));
            DialogueModelResponse result = client.complete(
                    UUID.randomUUID(), new DialoguePrompt("Pet context and instructions", 5,
                            com.silver.aipets.common.transport.DialogueContextUsageWire.empty(),
                            java.util.Optional.of("I was thinking about going back there.")),
                    DialogueStructuredSchema.JSON_SCHEMA, Duration.ofSeconds(2));
            assertEquals(OUTPUT, result.structuredJson());
            assertEquals(42, result.inputTokens());
            assertEquals(4, result.cachedInputTokens());
            assertEquals(12, result.outputTokens());
            assertEquals("stop", result.finishReason());
            assertEquals(2000, result.configuredCompletionLimit());
            assertEquals(0, result.estimatedCost().compareTo(
                    new java.math.BigDecimal("0.00000984")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void moderationFailsClosedOnFlaggedResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/moderations", exchange -> {
            byte[] response = "{\"results\":[{\"flagged\":true}]}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiModerationClient client = new OpenAiModerationClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "omni-moderation-latest", TOKEN, Duration.ofSeconds(2));
            OpenAiModerationClient.ModerationResult result = client.moderate("unsafe");
            assertTrue(!result.allowed());
            assertTrue(result.available());
            assertEquals("MODERATION_FLAGGED", result.category());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void moderationOutageFailsClosedForLocallySafeMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/moderations", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();
        try {
            OpenAiModerationClient client = new OpenAiModerationClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "omni-moderation-latest", TOKEN, Duration.ofSeconds(2));
            DialogueSafety.SafetyResult result = new ModeratedDialogueSafety(client)
                    .preprocess("hello pet", 500);
            assertFalse(result.allowed());
            assertEquals("MODERATION_UNAVAILABLE", result.category());
        } finally {
            server.stop(0);
        }
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
