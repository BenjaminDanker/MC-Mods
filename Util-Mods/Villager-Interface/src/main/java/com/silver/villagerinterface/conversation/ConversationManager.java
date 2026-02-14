package com.silver.villagerinterface.conversation;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.config.VillagerInterfaceConfig;
import com.silver.villagerinterface.config.VillagerPosition;
import com.silver.villagerinterface.villager.CustomVillagerManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class ConversationManager {
    private static final Set<String> EXIT_KEYWORDS = Set.of("!exit");
    private static final String SYSTEM_FALLBACK_PROMPT = "You have amnesia.";
    private static final int INTERACT_COOLDOWN_TICKS = 60;
    private static final int COOLDOWN_MESSAGE_INTERVAL_TICKS = 20;

    private final CustomVillagerManager villagerManager;
    private final Map<UUID, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastHandledTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> interactCooldownUntilTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastCooldownMessageTick = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private static final long STREAM_FLUSH_MILLIS = 350L;
    private static final int STREAM_MAX_CHARS = 160;
    private static final int STREAM_MIN_CHARS = 48;

    private static final String CMD_CONFIRM = "confirm";
    private static final String CMD_MODIFY = "modify";

    public ConversationManager(CustomVillagerManager villagerManager) {
        this.villagerManager = villagerManager;
    }

    public boolean startConversation(ServerPlayerEntity player, VillagerEntity villager) {
        ConversationSession existing = sessions.get(player.getUuid());
        VillagerConfigEntry entry = villagerManager.getEntryForVillager(villager);
        if (entry == null) {
            return false;
        }

        if (isInteractionCoolingDown(player)) {
            return true;
        }

        applyInteractionCooldown(player);

        if (existing != null) {
            endConversation(player, "Conversation ended.");
        }

        VillagerInterfaceConfig config = getConfig();
        ConversationSession session = new ConversationSession(entry, config.maxHistoryTurns());
        session.addSystemPrompt(resolveSystemPrompt(entry));
        if (BlacksmithInteraction.isBlacksmith(entry)) {
            BlacksmithInteraction.addBlacksmithSystemRules(session);
        }
        sessions.put(player.getUuid(), session);

        player.sendMessage(Text.literal("Please be patient with the dumb villagers. Type '!exit' to end the conversation.").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.empty(), false);
        requestReply(player, session, "The player approaches. Greet them briefly.", true);
        return true;
    }

    public boolean handleChatMessage(ServerPlayerEntity player, String message) {
        ConversationSession session = sessions.get(player.getUuid());
        if (session == null) {
            return false;
        }

        markHandled(player);

        String trimmed = message != null ? message.trim() : "";
        if (trimmed.isEmpty()) {
            return true;
        }

        if (session.isAwaitingResponse()) {
            player.sendMessage(Text.literal("The villager is thinking...").formatted(Formatting.DARK_GRAY), false);
            return true;
        }

        if (isExitKeyword(trimmed)) {
            sendPlayerLine(player, trimmed);
            endConversation(player, "Conversation ended.");
            return true;
        }

        if (trimmed.startsWith("!") && BlacksmithInteraction.isBlacksmith(session.entry())) {
            sendPlayerLine(player, trimmed);
            if (BlacksmithInteraction.handleCommand(this, player, session, trimmed)) {
                return true;
            }
            // Unknown command: treat as normal dialogue so the LLM can explain usage.
        }

        sendPlayerLine(player, trimmed);
        requestReply(player, session, trimmed, true);
        return true;
    }

    public void onPlayerDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        UUID playerId = handler.getPlayer().getUuid();
        sessions.remove(playerId);
        lastHandledTick.remove(playerId);
        interactCooldownUntilTick.remove(playerId);
        lastCooldownMessageTick.remove(playerId);
    }

    public void onServerTick(MinecraftServer server) {
        if (sessions.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, ConversationSession> entry : sessions.entrySet()) {
            UUID playerId = entry.getKey();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                sessions.remove(playerId);
                lastHandledTick.remove(playerId);
                continue;
            }

            if (!player.isAlive()) {
                endConversation(player, "Conversation ended because you died.");
                continue;
            }

            ConversationSession session = entry.getValue();
            VillagerConfigEntry villagerEntry = session.entry();

            RegistryKey<World> expectedWorld = toWorldKey(villagerEntry.dimension());
            if (!player.getEntityWorld().getRegistryKey().equals(expectedWorld)) {
                endConversation(player, "Conversation ended because you changed dimensions.");
                continue;
            }

            VillagerPosition position = villagerEntry.position();
            if (position == null) {
                continue;
            }

            Vec3d target = position.toVec3d();
            double maxDistance = villagerEntry.maxDistance() > 0.0 ? villagerEntry.maxDistance() : 5.0;
            double distanceSq = target.squaredDistanceTo(player.getX(), player.getY(), player.getZ());
            if (distanceSq > maxDistance * maxDistance) {
                endConversation(player, "Conversation ended because you walked away.");
            }
        }
    }

    public boolean isInConversation(ServerPlayerEntity player) {
        return sessions.containsKey(player.getUuid());
    }

    public boolean shouldSuppressBroadcast(ServerPlayerEntity player) {
        if (isInConversation(player)) {
            return true;
        }
        Integer handledTick = lastHandledTick.get(player.getUuid());
        return handledTick != null && handledTick == player.getEntityWorld().getServer().getTicks();
    }

    public int runDevOllamaTest(ServerPlayerEntity player, int count) {
        VillagerInterfaceConfig config = getConfig();
        if (config.villagers().isEmpty()) {
            player.sendMessage(Text.literal("No villagers configured; unable to run test.").formatted(Formatting.DARK_GRAY), false);
            return 0;
        }

        int total = Math.max(1, count);
        player.sendMessage(Text.literal("Starting " + total + " Ollama test request(s)...").formatted(Formatting.GRAY), false);

        for (int i = 0; i < total; i++) {
            VillagerConfigEntry entry = config.villagers().get(i % config.villagers().size());
            ConversationSession session = new ConversationSession(entry, config.maxHistoryTurns());
            session.addSystemPrompt(resolveSystemPrompt(entry));
            session.addUserMessage("This is a concurrent test request. Reply with at least 50 words and no more than 70 words.");
            requestTestReply(player, session, entry, i + 1, Instant.now());
        }

        return total;
    }

    private boolean isExitKeyword(String message) {
        String normalized = message.toLowerCase();
        return EXIT_KEYWORDS.contains(normalized);
    }

    private void endConversation(ServerPlayerEntity player, String systemMessage) {
        sessions.remove(player.getUuid());
        player.sendMessage(Text.literal(systemMessage), false);
    }

    private void markHandled(ServerPlayerEntity player) {
        lastHandledTick.put(player.getUuid(), player.getEntityWorld().getServer().getTicks());
    }

    private RegistryKey<World> toWorldKey(String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            id = Identifier.of("minecraft", "overworld");
        }
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }

    private boolean isInteractionCoolingDown(ServerPlayerEntity player) {
        int currentTick = player.getEntityWorld().getServer().getTicks();
        Integer cooldownUntil = interactCooldownUntilTick.get(player.getUuid());
        if (cooldownUntil == null || currentTick >= cooldownUntil) {
            return false;
        }

        Integer lastMessageTick = lastCooldownMessageTick.get(player.getUuid());
        if (lastMessageTick == null || currentTick - lastMessageTick >= COOLDOWN_MESSAGE_INTERVAL_TICKS) {
            player.sendMessage(Text.literal("Please wait a moment before talking again.").formatted(Formatting.DARK_GRAY), true);
            lastCooldownMessageTick.put(player.getUuid(), currentTick);
        }

        return true;
    }

    private void applyInteractionCooldown(ServerPlayerEntity player) {
        int currentTick = player.getEntityWorld().getServer().getTicks();
        interactCooldownUntilTick.put(player.getUuid(), currentTick + INTERACT_COOLDOWN_TICKS);
    }

    private void sendVillagerLine(ServerPlayerEntity player, VillagerConfigEntry entry, String line) {
        String name = entry.displayName() != null && !entry.displayName().isBlank() ? entry.displayName() : entry.id();
        player.sendMessage(Text.literal(name + ":"), false);
        player.sendMessage(Text.literal(line), false);
    }

    private void sendPlayerLine(ServerPlayerEntity player, String line) {
        String name = player.getNameForScoreboard();
        player.sendMessage(Text.literal(name + ":").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
    }

    void requestReply(ServerPlayerEntity player, ConversationSession session, String userMessage, boolean includeUserInHistory) {
        if (includeUserInHistory) {
            session.addUserMessage(userMessage);
        }

        sendOllamaRequest(player, session, session.history());
    }

    void requestTransientReply(ServerPlayerEntity player, ConversationSession session, String transientUserMessage, String transientSystemMessage) {
        List<OllamaChatMessage> messages = new ArrayList<>(session.history());
        if (transientSystemMessage != null && !transientSystemMessage.isBlank()) {
            messages.add(OllamaChatMessage.system(transientSystemMessage));
        }
        if (transientUserMessage != null && !transientUserMessage.isBlank()) {
            messages.add(OllamaChatMessage.user(transientUserMessage));
        }

        sendOllamaRequest(player, session, messages);
    }

    private void sendOllamaRequest(ServerPlayerEntity player, ConversationSession session, List<OllamaChatMessage> messages) {

        session.setAwaitingResponse(true);
        VillagerInterfaceConfig config = getConfig();
        URI endpoint = buildOllamaEndpoint(config.ollamaBaseUrl());
        OllamaChatRequest requestBody = new OllamaChatRequest(
            config.ollamaModel(),
            messages,
            normalizeKeepAlive(config.ollamaKeepAlive()),
            true
        );
        String payload = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(getConfig().ollamaTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
            .whenComplete((response, error) -> {
                MinecraftServer server = player.getEntityWorld().getServer();
                server.execute(() -> {
                    if (!sessions.containsKey(player.getUuid())) {
                        session.setAwaitingResponse(false);
                        return;
                    }

                    if (error != null) {
                        session.setAwaitingResponse(false);
                        handleOllamaError("Ollama request failed", error, player, "The villager is taking too long to respond.");
                        return;
                    }

                    if (response == null) {
                        session.setAwaitingResponse(false);
                        VillagerInterfaceMod.LOGGER.warn("Ollama response was null");
                        player.sendMessage(Text.literal("The villager seems distracted.").formatted(Formatting.DARK_GRAY), false);
                        return;
                    }

                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        session.setAwaitingResponse(false);
                        VillagerInterfaceMod.LOGGER.warn("Ollama HTTP {}", status);
                        player.sendMessage(Text.literal("The villager seems distracted.").formatted(Formatting.DARK_GRAY), false);
                        return;
                    }

                    Stream<String> lines = response.body();
                    CompletableFuture.runAsync(() -> consumeConversationStream(server, player.getUuid(), session, lines));
                });
            });
    }

    private void requestTestReply(ServerPlayerEntity player, ConversationSession session, VillagerConfigEntry entry, int index, Instant startedAt) {
        VillagerInterfaceConfig config = getConfig();
        URI endpoint = buildOllamaEndpoint(config.ollamaBaseUrl());
        OllamaChatRequest requestBody = new OllamaChatRequest(
            config.ollamaModel(),
            session.history(),
            normalizeKeepAlive(config.ollamaKeepAlive()),
            true
        );
        String payload = gson.toJson(requestBody);

        player.sendMessage(Text.literal(formatDevtestPrefix(index, entry.id(), "started") + " at " + startedAt).formatted(Formatting.DARK_GRAY), false);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(getConfig().ollamaTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
            .whenComplete((response, error) -> {
                MinecraftServer server = player.getEntityWorld().getServer();
                server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(player.getUuid());
                    if (current == null) {
                        return;
                    }

                    if (error != null) {
                        handleOllamaError("Ollama test " + index + " failed", error, current, "Test " + index + " timed out.");
                        current.sendMessage(Text.literal(formatDevtestPrefix(index, entry.id(), "failed") + durationSince(startedAt)).formatted(Formatting.DARK_GRAY), false);
                        current.sendMessage(Text.literal("Test " + index + " (" + entry.id() + ") failed.").formatted(Formatting.DARK_GRAY), false);
                        return;
                    }

                    if (response == null) {
                        VillagerInterfaceMod.LOGGER.warn("Ollama test {} response was null", index);
                        current.sendMessage(Text.literal(formatDevtestPrefix(index, entry.id(), "failed") + durationSince(startedAt)).formatted(Formatting.DARK_GRAY), false);
                        current.sendMessage(Text.literal("Test " + index + " (" + entry.id() + ") failed.").formatted(Formatting.DARK_GRAY), false);
                        return;
                    }

                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        VillagerInterfaceMod.LOGGER.warn("Ollama test {} HTTP {}", index, status);
                        current.sendMessage(Text.literal(formatDevtestPrefix(index, entry.id(), "failed") + durationSince(startedAt)).formatted(Formatting.DARK_GRAY), false);
                        current.sendMessage(Text.literal("Test " + index + " (" + entry.id() + ") failed.").formatted(Formatting.DARK_GRAY), false);
                        return;
                    }

                    Stream<String> lines = response.body();
                    CompletableFuture.runAsync(() -> consumeTestStream(server, player.getUuid(), entry.id(), index, lines, startedAt));
                });
            });
    }

    private VillagerInterfaceConfig getConfig() {
        return VillagerInterfaceMod.getConfigManager().getConfig();
    }

    private String resolveSystemPrompt(VillagerConfigEntry entry) {
        String prompt = entry.systemPrompt();
        return prompt != null && !prompt.isBlank() ? prompt : SYSTEM_FALLBACK_PROMPT;
    }

    private URI buildOllamaEndpoint(String baseUrl) {
        String normalized = baseUrl != null ? baseUrl.trim() : "";
        if (normalized.isEmpty()) {
            normalized = VillagerInterfaceConfig.createDefault().ollamaBaseUrl();
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return URI.create(normalized + "api/chat");
    }

    private String normalizeKeepAlive(String keepAlive) {
        String value = keepAlive != null ? keepAlive.trim() : "";
        if (value.isEmpty()) {
            value = VillagerInterfaceConfig.createDefault().ollamaKeepAlive();
        }

        if ("-1".equals(value)) {
            return "876000h";
        }

        if (value.chars().allMatch(Character::isDigit)) {
            return value + "s";
        }

        return value;
    }

    private OllamaChatResponse parseOllamaResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonReader reader = new JsonReader(new StringReader(body));
            reader.setLenient(true);
            return gson.fromJson(reader, OllamaChatResponse.class);
        } catch (Exception ex) {
            VillagerInterfaceMod.LOGGER.warn("Failed to parse Ollama response: {}", body);
            return null;
        }
    }

    private void handleOllamaError(String prefix, Throwable error, ServerPlayerEntity player, String timeoutMessage) {
        Throwable root = unwrap(error);
        if (root instanceof HttpTimeoutException) {
            VillagerInterfaceMod.LOGGER.warn("{}: request timed out", prefix);
            player.sendMessage(Text.literal(timeoutMessage).formatted(Formatting.DARK_GRAY), false);
            return;
        }

        VillagerInterfaceMod.LOGGER.warn(prefix + ": " + root.getMessage());
        player.sendMessage(Text.literal("The villager seems distracted.").formatted(Formatting.DARK_GRAY), false);
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private void consumeTestStream(MinecraftServer server, UUID playerId, String villagerId, int index, Stream<String> lines, Instant startedAt) {
        StringBuilder full = new StringBuilder();
        long[] lastUpdate = new long[] { 0L };
        boolean[] doneSeen = new boolean[] { false };

        try (Stream<String> stream = lines) {
            stream.forEach(line -> {
                if (line == null || line.isBlank() || doneSeen[0]) {
                    return;
                }

                String payload = normalizeStreamLine(line);
                if (payload == null) {
                    return;
                }

                OllamaChatStreamResponse parsed = parseStreamResponse(payload);
                if (parsed == null) {
                    return;
                }

                OllamaChatMessage message = parsed.message();
                if (message != null && message.content() != null) {
                    applyStreamChunk(full, message.content());
                }

                long now = System.currentTimeMillis();
                if (parsed.done()) {
                    doneSeen[0] = true;
                }

                if (parsed.done() || now - lastUpdate[0] >= 200L) {
                    lastUpdate[0] = now;
                }
            });
        }

        String finalText = full.toString().trim();
        server.execute(() -> sendDevtestFinal(server, playerId, villagerId, index, finalText, startedAt));
    }

    private void consumeConversationStream(MinecraftServer server, UUID playerId, ConversationSession session, Stream<String> lines) {
        StringBuilder full = new StringBuilder();
        long[] lastUpdate = new long[] { 0L };
        int[] lastSentIndex = new int[] { 0 };
        boolean[] prefixSent = new boolean[] { false };
        boolean[] doneSeen = new boolean[] { false };
        VillagerConfigEntry entry = session.entry();

        try (Stream<String> stream = lines) {
            stream.forEach(line -> {
                if (line == null || line.isBlank() || doneSeen[0]) {
                    return;
                }

                String payload = normalizeStreamLine(line);
                if (payload == null) {
                    return;
                }

                OllamaChatStreamResponse parsed = parseStreamResponse(payload);
                if (parsed == null) {
                    return;
                }

                OllamaChatMessage message = parsed.message();
                if (message != null && message.content() != null) {
                    applyStreamChunk(full, message.content());
                }

                long now = System.currentTimeMillis();
                if (parsed.done()) {
                    doneSeen[0] = true;
                }

                if (parsed.done() || now - lastUpdate[0] >= STREAM_FLUSH_MILLIS) {
                    String current = full.toString();
                    int chunkEnd = findChunkEnd(current, lastSentIndex[0], STREAM_MAX_CHARS, parsed.done());
                    if (chunkEnd > lastSentIndex[0]) {
                        String chunk = current.substring(lastSentIndex[0], chunkEnd);
                        lastSentIndex[0] = chunkEnd;
                        server.execute(() -> sendVillagerChunk(server, playerId, session, entry, chunk, prefixSent));
                    }
                    lastUpdate[0] = now;
                }
            });
        }

        String finalText = full.toString().trim();
        server.execute(() -> finishConversationStream(server, playerId, session, full.toString(), lastSentIndex[0], entry, prefixSent));
    }

    private void finishConversationStream(MinecraftServer server, UUID playerId, ConversationSession session, String fullText, int lastSentIndex, VillagerConfigEntry entry, boolean[] prefixSent) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            session.setAwaitingResponse(false);
            return;
        }

        if (sessions.get(playerId) != session) {
            session.setAwaitingResponse(false);
            return;
        }

        session.setAwaitingResponse(false);

        String remaining = "";
        if (fullText != null && lastSentIndex < fullText.length()) {
            remaining = fullText.substring(lastSentIndex);
        }

        if (!remaining.isBlank()) {
            sendVillagerChunk(server, playerId, session, entry, remaining, prefixSent);
        }

        String reply = fullText != null ? sanitizeAssistantText(fullText).trim() : "";
        if (reply.isBlank()) {
            player.sendMessage(Text.literal("The villager seems distracted.").formatted(Formatting.DARK_GRAY), false);
            return;
        }

        session.addAssistantMessage(reply);
    }

    private void sendVillagerChunk(MinecraftServer server, UUID playerId, ConversationSession session, VillagerConfigEntry entry, String chunk, boolean[] prefixSent) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }

        if (sessions.get(playerId) != session) {
            return;
        }

        String cleaned = sanitizeAssistantText(chunk).trim();
        if (cleaned.isBlank()) {
            return;
        }

        if (prefixSent != null && !prefixSent[0]) {
            sendVillagerLine(player, entry, cleaned);
            prefixSent[0] = true;
        } else {
            player.sendMessage(Text.literal(cleaned), false);
        }
    }

    private int findChunkEnd(String text, int startIndex, int maxChars, boolean force) {
        if (text == null) {
            return startIndex;
        }
        if (startIndex >= text.length()) {
            return startIndex;
        }

        int endLimit = Math.min(text.length(), startIndex + Math.max(1, maxChars));
        int available = text.length() - startIndex;
        if (!force && available < STREAM_MIN_CHARS) {
            return startIndex;
        }

        // Prefer boundaries that make reading pleasant.
        int preferred = -1;
        for (int i = endLimit - 1; i >= startIndex; i--) {
            char c = text.charAt(i);
            if (c == '\n') {
                if (i > startIndex && text.charAt(i - 1) == '!' && isLikelyCommandBang(text, i - 1)) {
                    continue;
                }
                preferred = i + 1;
                break;
            }
            if (c == '!') {
                if (isLikelyCommandBang(text, i)) {
                    continue;
                }
                preferred = i + 1;
                break;
            }
            if (c == '.' || c == '?' || c == ';') {
                preferred = i + 1;
                break;
            }
        }

        if (preferred == -1) {
            for (int i = endLimit - 1; i >= startIndex; i--) {
                char c = text.charAt(i);
                if (c == ',' || c == ':' || c == ')') {
                    preferred = i + 1;
                    break;
                }
            }
        }

        if (preferred != -1 && preferred - startIndex >= STREAM_MIN_CHARS) {
            return preferred;
        }

        // Fall back to whitespace cut near the limit.
        for (int i = endLimit - 1; i > startIndex; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                int p = i - 1;
                while (p >= startIndex && Character.isWhitespace(text.charAt(p))) {
                    p--;
                }
                if (p >= startIndex && text.charAt(p) == '!' && isLikelyCommandBang(text, p)) {
                    continue;
                }
                int candidate = i + 1;
                if (candidate - startIndex >= STREAM_MIN_CHARS) {
                    return candidate;
                }
                break;
            }
        }

        return force ? text.length() : startIndex;
    }

    private boolean isLikelyCommandBang(String text, int bangIndex) {
        if (text == null) {
            return false;
        }
        if (bangIndex < 0 || bangIndex >= text.length()) {
            return false;
        }
        if (text.charAt(bangIndex) != '!') {
            return false;
        }

        if (bangIndex > 0) {
            char prev = text.charAt(bangIndex - 1);
            if (!Character.isWhitespace(prev) && prev != '"' && prev != '\'' && prev != '(' && prev != '[') {
                return false;
            }
        }

        int j = bangIndex + 1;
        while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
            j++;
        }
        if (j >= text.length()) {
            return false;
        }

        return text.regionMatches(true, j, CMD_CONFIRM, 0, CMD_CONFIRM.length())
            || text.regionMatches(true, j, CMD_MODIFY, 0, CMD_MODIFY.length());
    }

    private String sanitizeAssistantText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text.replace("\r", "");
        // Collapse split command tokens across whitespace/newlines: "!\nconfirm" -> "!confirm".
        cleaned = cleaned.replaceAll("(?i)!\\s+confirm\\b", "!confirm");
        cleaned = cleaned.replaceAll("(?i)!\\s+modify\\b", "!modify");
        // Normalize common blacksmith command argument spacing.
        cleaned = cleaned.replaceAll("(?i)!modify\\s+soulbound\\b", "!modify Soulbound");

        // Fix common missing spaces before numbers in English phrases.
        cleaned = cleaned.replaceAll("(?i)\\bis(\\d)\\b", "is $1");
        cleaned = cleaned.replaceAll("(?i)\\bof(\\d)\\b", "of $1");
        cleaned = cleaned.replaceAll("(?i)(maximum\\s+of)(\\d)\\b", "$1 $2");
        return cleaned;
    }

    private void applyStreamChunk(StringBuilder full, String chunk) {
        if (chunk.isBlank()) {
            return;
        }

        if (full.length() == 0) {
            full.append(chunk);
            return;
        }

        String current = full.toString();
        if (chunk.startsWith(current)) {
            full.setLength(0);
            full.append(chunk);
            return;
        }

        if (current.endsWith(chunk)) {
            return;
        }

        full.append(chunk);
    }

    private String normalizeStreamLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("data:")) {
            trimmed = trimmed.substring("data:".length()).trim();
        }

        if (trimmed.equals("[DONE]")) {
            return null;
        }

        return trimmed;
    }

    private OllamaChatStreamResponse parseStreamResponse(String payload) {
        try {
            JsonReader reader = new JsonReader(new StringReader(payload));
            reader.setLenient(true);
            return gson.fromJson(reader, OllamaChatStreamResponse.class);
        } catch (Exception ex) {
            VillagerInterfaceMod.LOGGER.warn("Failed to parse Ollama stream chunk: {}", payload);
            return null;
        }
    }

    private void sendDevtestFinal(MinecraftServer server, UUID playerId, String villagerId, int index, String reply, Instant startedAt) {
        if (reply.isBlank()) {
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }

        player.sendMessage(Text.literal(formatDevtestPrefix(index, villagerId, "complete") + durationSince(startedAt)).formatted(Formatting.DARK_GRAY), false);
        player.sendMessage(Text.literal("Test " + index + " (" + villagerId + "): " + reply).formatted(Formatting.GRAY), false);
    }

    private String buildPreview(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String durationSince(Instant startedAt) {
        Duration duration = Duration.between(startedAt, Instant.now());
        long seconds = duration.getSeconds();
        long millis = duration.toMillisPart();
        return " (" + seconds + "s " + millis + "ms)";
    }

    private String formatDevtestPrefix(int index, String villagerId, String status) {
        return "Test " + index + " (" + villagerId + ") " + status;
    }
}
