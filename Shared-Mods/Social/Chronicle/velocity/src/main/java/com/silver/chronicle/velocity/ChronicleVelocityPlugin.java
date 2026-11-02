package com.silver.chronicle.velocity;

import com.google.inject.Inject;
import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.common.ChronicleProtocol;
import com.silver.chronicle.common.ChronicleProtocol.Operation;
import com.silver.chronicle.common.PrivacyMode;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.slf4j.Logger;

@Plugin(id = "chronicle", name = "Chronicle", version = "1.0.0", authors = {"SilverSphere"})
public final class ChronicleVelocityPlugin {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("chronicle:events_v1");
    private static final long MAX_REQUEST_AGE_MS = 30_000;
    private static final long RESPONSE_CACHE_MS = 60_000;
    private static final int MAX_RESPONSE_CACHE = 4_096;
    private static final TextColor CHRONICLE_STAR_COLOR = TextColor.color(0xC084FC);
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final String PROMPT = "✦ You have made history. Choose how this should be announced:\n"
            + "1 - Public\n2 - Mysterious\n3 - Anonymous\n4 - Mysterious + Anonymous\n"
            + "Type only 1, 2, 3, or 4. You have 1 minute before your default privacy is used.\n"
            + "Change your default with /history privacy.";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, byte[]> backendKeys = new ConcurrentHashMap<>();
    private final Map<PromptKey, PromptSession> prompts = new ConcurrentHashMap<>();
    private final Map<RequestKey, CachedResponse> responses = new ConcurrentHashMap<>();
    private final Map<RequestKey, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "Chronicle-Database");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ChronicleDatabase database;

    @Inject
    public ChronicleVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        loadExistingBackendKeys();
        try {
            ChronicleDatabase db = new ChronicleDatabase(DatabaseSettings.load());
            db.migrate();
            database = db;
            logger.info("[Chronicle] MariaDB ready; Chronicle tables migrated in existing minecraft database");
            databaseExecutor.execute(this::recoverPersistentWork);
        } catch (Exception failure) {
            logger.error("[Chronicle] Could not initialize existing MariaDB connection or Chronicle migrations; Chronicle is disabled", failure);
        }
        logger.info("[Chronicle] Velocity component initialized; authenticated backend keys available={}", backendKeys.size());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        handlePluginMessage(event);
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        String backend = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().toLowerCase(Locale.ROOT)).orElse("");
        ChronicleEvent entry = entryEvent(backend);
        if (entry != null) processEntry(event.getPlayer(), entry);
        resumePrompt(event.getPlayer());
        flushReadyAnnouncements();
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String text = event.getMessage();
        if (!(text.equals("1") || text.equals("2") || text.equals("3") || text.equals("4"))) return;
        Player player = event.getPlayer();
        Map.Entry<PromptKey, PromptSession> selected = prompts.entrySet().stream()
                .filter(entry -> entry.getKey().playerId().equals(player.getUniqueId()))
                .min(Comparator.comparingLong((Map.Entry<PromptKey, PromptSession> entry) -> entry.getValue().deadlineMs())
                        .thenComparing(entry -> entry.getKey().eventId()))
                .orElse(null);
        if (selected == null) return;
        PromptKey key = selected.getKey();
        PromptSession session = selected.getValue();
        long now = System.currentTimeMillis();
        if (now >= session.deadlineMs()) {
            prompts.remove(key, session);
            expirePrompt(key, session.deadlineMs());
            return;
        }
        if (!prompts.remove(key, session)) return;
        // Velocity cannot cancel signed player chat on 1.19.1+. Replace the chat text
        // with an empty allowed message so the signed packet is acknowledged normally.
        event.setResult(PlayerChatEvent.ChatResult.message(""));
        PrivacyMode mode = switch (text) {
            case "1" -> PrivacyMode.PUBLIC;
            case "2" -> PrivacyMode.MYSTERIOUS;
            case "3" -> PrivacyMode.ANONYMOUS;
            default -> PrivacyMode.SECRET;
        };
        logger.info("[Chronicle] Privacy choice received player={} event={} mode={} deadline_ms={}",
                player.getUniqueId(), key.eventId(), mode.id(), session.deadlineMs());
        choosePrivacy(player.getUniqueId(), key.eventId(), mode, player, now);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        proxy.getChannelRegistrar().unregister(CHANNEL);
        databaseExecutor.shutdown();
        try { databaseExecutor.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        ChronicleDatabase db = database;
        if (db != null) db.close();
        logger.info("[Chronicle] Velocity component stopped");
    }

    private void handlePluginMessage(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection backend)
                || !(event.getTarget() instanceof Player player)
                || database == null) return;
        String backendId = backend.getServerInfo().getName().toLowerCase(Locale.ROOT);
        String currentBackend = player.getCurrentServer().map(connection -> connection.getServerInfo().getName().toLowerCase(Locale.ROOT)).orElse("");
        if (!backendId.equals(currentBackend)) return;
        byte[] key = backendKeys.get(backendId);
        if (key == null) return;
        try {
            ChronicleProtocol.Request request = ChronicleProtocol.decodeRequest(event.getData());
            long age = System.currentTimeMillis() - request.issuedAtMillis();
            if (request.version() != ChronicleProtocol.VERSION
                    || !request.backendId().equals(backendId)
                    || !request.playerId().equals(player.getUniqueId())
                    || !request.username().equals(player.getUsername())
                    || Math.abs(age) > MAX_REQUEST_AGE_MS
                    || !ChronicleProtocol.verify(request, key)
                    || !validOperation(request)) return;
            RequestKey requestKey = new RequestKey(backendId, request.backendEpoch(), request.nonce());
            CachedResponse cached = responses.get(requestKey);
            if (cached != null && cached.createdAtMs() + RESPONSE_CACHE_MS > System.currentTimeMillis()) {
                sendResponse(backend, player, cached.bytes());
                return;
            }
            if (cached != null) responses.remove(requestKey, cached);
            CompletableFuture<byte[]> future = inFlight.computeIfAbsent(requestKey, ignored ->
                    CompletableFuture.supplyAsync(() -> processRequest(request), databaseExecutor)
                            .thenApply(result -> makeResponse(request, key, result)));
            future.whenComplete((bytes, failure) -> {
                inFlight.remove(requestKey, future);
                if (failure != null) {
                    logger.warn("[Chronicle] Request failed backend={} player={}: {}", backendId, player.getUniqueId(), failure.toString());
                    byte[] error = makeResponse(request, key, new RequestResult(false, List.of("Chronicle is temporarily unavailable.")));
                    sendResponse(backend, player, error);
                } else {
                    responses.put(requestKey, new CachedResponse(bytes, System.currentTimeMillis()));
                    trimResponseCache();
                    sendResponse(backend, player, bytes);
                }
            });
        } catch (RuntimeException malformed) {
            logger.debug("[Chronicle] Rejected malformed or unauthenticated backend message: {}", malformed.toString());
        }
    }

    private RequestResult processRequest(ChronicleProtocol.Request request) {
        try {
            return switch (request.operation()) {
                case COMPLETE -> complete(request);
                case HISTORY -> history();
                case MINE -> mine(request.playerId());
                case SET_PRIVACY -> setPrivacyRequest(request);
                case ADMIN_CONCEALED -> concealed();
                case ADMIN_REANNOUNCE -> reannounce(request.args().getFirst());
                case PROMPT_STATUS -> promptStatus(request.playerId());
                case ADMIN_EXCLUDE -> admin(database.exclude(UUID.fromString(request.args().get(0)), request.args().get(1), request.args().get(2), request.playerId(), request.username()));
                case ADMIN_INCLUDE -> admin(database.include(UUID.fromString(request.args().getFirst()), request.playerId(), request.username()));
                case ADMIN_EXCLUSIONS -> exclusions();
                case ADMIN_RESET_EVENT -> admin(database.resetEvent(request.args().getFirst(), request.playerId(), request.username()));
                case ADMIN_REMOVE_PLAYER -> admin(database.removePlayer(UUID.fromString(request.args().getFirst()),
                        request.args().size() == 2 ? request.args().get(1) : null, request.playerId(), request.username()));
            };
        } catch (Exception failure) {
            throw new IllegalStateException("Chronicle operation failed", failure);
        }
    }

    private RequestResult complete(ChronicleProtocol.Request request) throws Exception {
        String eventId = request.args().getFirst();
        ChronicleEvent event = ChronicleEvent.byId(eventId);
        if (event == null) return new RequestResult(false, List.of());
        ChronicleDatabase.CompletionResult result = database.complete(eventId, request.playerId(), request.username());
        if (result.excluded()) {
            logger.info("[Chronicle] Excluded completion ignored event={} player={}", eventId, request.playerId());
            return new RequestResult(true, List.of());
        }
        if (!result.firstClaimed()) return new RequestResult(true, List.of());
        activatePrompt(request.playerId(), eventId, result.deadlineMs(), true);
        return new RequestResult(true, List.of());
    }

    private RequestResult history() throws Exception {
        long now = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        for (ChronicleDatabase.HistoryRecord record : database.history(now)) lines.add(renderHistory(record, now));
        if (lines.isEmpty()) lines.add("No historical firsts have been recorded yet.");
        return new RequestResult(true, lines);
    }

    private RequestResult mine(UUID playerId) throws Exception {
        List<String> lines = new ArrayList<>();
        for (ChronicleDatabase.PersonalRecord record : database.mine(playerId)) {
            ChronicleEvent event = ChronicleEvent.byId(record.eventId());
            if (event == null) continue;
            lines.add("Completed " + event.display() + " — " + record.username() + " — " + UTC_TIME.format(Instant.ofEpochMilli(record.completedAtMs())));
        }
        if (lines.isEmpty()) lines.add("You have no Chronicle completions yet.");
        return new RequestResult(true, lines);
    }

    private RequestResult setPrivacyRequest(ChronicleProtocol.Request request) throws Exception {
        PrivacyMode mode = request.args().isEmpty() ? null : PrivacyMode.parse(request.args().getFirst());
        if (mode == null) return new RequestResult(false, List.of("Choose public, mysterious, anonymous, or secret."));
        database.setPreference(request.playerId(), mode);
        logger.info("[Chronicle] Privacy timeout fallback changed player={} mode={}", request.playerId(), mode.id());
        String confirmation = switch (mode) {
            case PUBLIC -> "Chronicle default set to Public. If you do not choose within 1 minute, your name and achievement will both be revealed immediately.";
            case MYSTERIOUS -> "Chronicle default set to Mysterious. If you do not choose within 1 minute, your name will be shown but the achievement will stay hidden for 24 hours.";
            case ANONYMOUS -> "Chronicle default set to Anonymous. If you do not choose within 1 minute, the achievement will be shown but your name will stay hidden for 24 hours.";
            case SECRET -> "Chronicle default set to Mysterious + Anonymous. If you do not choose within 1 minute, both your name and the achievement will stay hidden for 24 hours.";
        };
        return new RequestResult(true, List.of(confirmation));
    }

    private RequestResult concealed() throws Exception {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ChronicleDatabase.FirstRecord record : database.concealed()) {
            ChronicleEvent event = ChronicleEvent.byId(record.eventId());
            if (event != null) lines.add("First to " + event.display() + " — " + record.username()
                    + " — concealed until " + UTC_TIME.format(Instant.ofEpochMilli(record.concealUntilMs())));
        }
        if (lines.isEmpty()) lines.add("No records are currently concealed.");
        return new RequestResult(true, lines);
    }

    private RequestResult reannounce(String eventId) throws Exception {
        ChronicleEvent event = ChronicleEvent.byId(eventId);
        if (event == null) return new RequestResult(false, List.of("Unknown Chronicle event."));
        ChronicleDatabase.FirstRecord record = database.ready(eventId);
        if (record == null) return new RequestResult(false, List.of("No first record exists for that event."));
        broadcast(announcement(record));
        return new RequestResult(true, List.of("Historical announcement sent."));
    }

    private RequestResult promptStatus(UUID playerId) throws Exception {
        for (ChronicleDatabase.PendingPrompt pending : database.pendingPrompts(playerId)) {
            activatePrompt(playerId, pending.eventId(), pending.deadlineMs(), true);
        }
        return new RequestResult(true, List.of());
    }

    private RequestResult exclusions() throws Exception {
        List<String> lines = new ArrayList<>();
        for (ChronicleDatabase.ExclusionRecord record : database.exclusions()) {
            String who = record.usernameHint() == null || record.usernameHint().isBlank() ? record.playerId().toString()
                    : record.usernameHint() + " (" + record.playerId() + ")";
            String line = who + " — excluded " + UTC_TIME.format(Instant.ofEpochMilli(record.excludedAtMs()))
                    + " by " + record.actorName();
            if (record.reason() != null && !record.reason().isBlank()) line += " — " + record.reason();
            lines.add(line);
        }
        if (lines.isEmpty()) lines.add("No players are excluded from Chronicle.");
        return new RequestResult(true, lines);
    }

    private RequestResult admin(ChronicleDatabase.AdminResult result) throws Exception {
        UUID playerId = result.promptPlayerId();
        if (playerId != null) {
            List<ChronicleDatabase.PendingPrompt> pending = database.pendingPrompts(playerId);
            java.util.Set<String> activeEvents = pending.stream().map(ChronicleDatabase.PendingPrompt::eventId)
                    .collect(java.util.stream.Collectors.toSet());
            prompts.keySet().removeIf(key -> key.playerId().equals(playerId) && !activeEvents.contains(key.eventId()));
            for (ChronicleDatabase.PendingPrompt prompt : pending) {
                activatePrompt(playerId, prompt.eventId(), prompt.deadlineMs(), false);
            }
        }
        return new RequestResult(true, result.lines());
    }

    private void activatePrompt(UUID playerId, String eventId, Long deadlineMs, boolean showNow) {
        if (deadlineMs == null) return;
        PromptKey key = new PromptKey(playerId, eventId);
        long remaining = deadlineMs - System.currentTimeMillis();
        if (remaining <= 0) {
            logger.info("[Chronicle] Prompt deadline already elapsed; resolving saved fallback player={} event={} deadline_ms={} recovered_or_late=true",
                    playerId, eventId, deadlineMs);
            prompts.remove(key);
            expirePrompt(key, deadlineMs);
            return;
        }
        AtomicBoolean created = new AtomicBoolean();
        PromptSession current = prompts.compute(key, (id, old) -> {
            if (old == null || old.deadlineMs() != deadlineMs) {
                created.set(true);
                return new PromptSession(deadlineMs);
            }
            return old;
        });
        if (showNow && current.deadlineMs() == deadlineMs) proxy.getPlayer(playerId).ifPresent(player -> player.sendMessage(Component.text(PROMPT)));
        if (created.get()) {
            logger.info("[Chronicle] Privacy prompt created player={} event={} deadline_ms={} remaining_ms={}",
                    playerId, eventId, deadlineMs, remaining);
            proxy.getScheduler().buildTask(this, () -> {
                if (prompts.remove(key, current)) {
                    logger.info("[Chronicle] Privacy timeout fired player={} event={} deadline_ms={}", playerId, eventId, deadlineMs);
                    expirePrompt(key, deadlineMs);
                }
            }).delay(Duration.ofMillis(remaining)).schedule();
        }
    }

    private void choosePrivacy(UUID playerId, String eventId, PrivacyMode mode, Player currentPlayer, long acceptedResponseAtMs) {
        if (database == null) return;
        databaseExecutor.execute(() -> {
            try {
                ChronicleDatabase.PreferenceResult result = database.choosePrompt(playerId, eventId, mode, acceptedResponseAtMs);
                logger.info("[Chronicle] Privacy resolved player={} event={} mode={} source=chat ready_events={}",
                        playerId, eventId, mode.id(), result.readyRecords().size());
                result.readyRecords().forEach(this::announceAndMark);
                if (currentPlayer != null) currentPlayer.sendMessage(Component.text("Chronicle privacy fallback set to " + mode.id() + "."));
            } catch (Exception failure) {
                logger.warn("[Chronicle] Could not save privacy choice player={} event={}: {}", playerId, eventId, failure.toString());
                if (currentPlayer != null) currentPlayer.sendMessage(Component.text("Chronicle could not save that choice. Please use /history privacy again."));
                try {
                    ChronicleDatabase.PendingPrompt pending = database.pendingPrompt(playerId, eventId);
                    if (pending != null) activatePrompt(playerId, eventId, pending.deadlineMs(), true);
                } catch (Exception ignored) { }
            }
        });
    }

    private void expirePrompt(PromptKey key, long deadlineMs) { expirePrompt(key, deadlineMs, 0); }

    private void expirePrompt(PromptKey key, long deadlineMs, int retry) {
        if (database == null) return;
        databaseExecutor.execute(() -> {
            try {
                List<ChronicleDatabase.FirstRecord> due = database.expirePrompt(key.playerId(), key.eventId(), deadlineMs);
                if (!due.isEmpty()) logger.info("[Chronicle] Privacy timeout resolved player={} event={} mode={} deadline_ms={}",
                        key.playerId(), key.eventId(), due.getFirst().privacyMode().id(), deadlineMs);
                due.forEach(this::announceAndMark);
                if (due.isEmpty()) {
                    ChronicleDatabase.PendingPrompt pending = database.pendingPrompt(key.playerId(), key.eventId());
                    if (pending != null && pending.deadlineMs() == deadlineMs) {
                        long remaining = deadlineMs - System.currentTimeMillis();
                        if (remaining > 0) activatePrompt(key.playerId(), key.eventId(), deadlineMs, false);
                        else proxy.getScheduler().buildTask(this,
                                () -> expirePrompt(key, deadlineMs, retry + 1)).delay(Duration.ofMillis(250)).schedule();
                    }
                }
            } catch (Exception failure) {
                logger.warn("[Chronicle] Privacy timeout persistence failed player={} event={}: {}",
                        key.playerId(), key.eventId(), failure.toString());
                proxy.getScheduler().buildTask(this,
                        () -> expirePrompt(key, deadlineMs, retry + 1)).delay(Duration.ofSeconds(Math.min(30, Math.max(1, retry + 1)))).schedule();
            }
        });
    }

    private void flushReadyAnnouncements() {
        if (database == null) return;
        databaseExecutor.execute(() -> {
            try { database.readyAnnouncements().forEach(this::announceAndMark); }
            catch (Exception failure) { logger.warn("[Chronicle] Could not flush historical announcements: {}", failure.toString()); }
        });
    }

    private void announceAndMark(ChronicleDatabase.FirstRecord record) {
        if (proxy.getPlayerCount() == 0) return;
        try {
            if (!database.claimAnnouncement(record.eventId())) return;
        } catch (Exception failure) {
            logger.warn("[Chronicle] Could not claim announcement event={}: {}", record.eventId(), failure.toString());
            return;
        }
        broadcast(announcement(record));
        logger.info("[Chronicle] Announcement emitted event={} player={} mode={} recipients={}", record.eventId(),
                record.playerId(), record.privacyMode() == null ? PrivacyMode.PUBLIC.id() : record.privacyMode().id(), proxy.getPlayerCount());
        try { database.markAnnounced(record.eventId()); }
        catch (Exception failure) { logger.warn("[Chronicle] Announcement state update failed event={}: {}", record.eventId(), failure.toString()); }
    }

    private static Component announcement(ChronicleDatabase.FirstRecord record) {
        ChronicleEvent event = ChronicleEvent.byId(record.eventId());
        if (event == null) return Component.empty();
        PrivacyMode mode = record.privacyMode() == null ? PrivacyMode.PUBLIC : record.privacyMode();
        String text = switch (mode) {
            case PUBLIC -> record.username() + " was the first to " + event.display() + ".";
            case MYSTERIOUS -> record.username() + " has made history.";
            case ANONYMOUS -> "An unknown traveler was the first to " + event.display() + ".";
            case SECRET -> "Someone has made history.";
        };
        return Component.text("✦", CHRONICLE_STAR_COLOR).append(Component.text(" " + text));
    }

    private static String renderHistory(ChronicleDatabase.HistoryRecord record, long now) {
        ChronicleEvent event = ChronicleEvent.byId(record.eventId());
        if (event == null) return "Historical event — Unknown";
        if (now >= record.concealUntilMs()) return "First to " + event.display() + " — " + record.username();
        return switch (record.privacyMode() == null ? PrivacyMode.SECRET : record.privacyMode()) {
            case PUBLIC -> "First to " + event.display() + " — " + record.username();
            case MYSTERIOUS -> "A historical event — " + record.username();
            case ANONYMOUS -> "First to " + event.display() + " — Unknown";
            case SECRET -> "A historical event — Unknown";
        };
    }

    private void broadcast(Component component) {
        for (Player recipient : List.copyOf(proxy.getAllPlayers())) recipient.sendMessage(component);
    }

    private void sendResponse(ServerConnection backend, Player player, byte[] response) {
        if (player.getCurrentServer().map(server -> server.getServerInfo().getName().equalsIgnoreCase(backend.getServerInfo().getName())).orElse(false)) {
            backend.sendPluginMessage(CHANNEL, response);
        }
    }

    private byte[] makeResponse(ChronicleProtocol.Request request, byte[] key, RequestResult result) {
        ChronicleProtocol.Response unsigned = new ChronicleProtocol.Response(ChronicleProtocol.VERSION, request.backendId(),
                request.playerId(), request.backendEpoch(), request.nonce(), result.success(), result.lines(), "");
        return ChronicleProtocol.encode(ChronicleProtocol.sign(unsigned, key));
    }

    private void recoverPersistentWork() {
        try {
            database.recoverAnnouncementClaims();
            List<ChronicleDatabase.PendingPrompt> pendingPrompts = database.allPendingPrompts();
            List<ChronicleDatabase.FirstRecord> ready = database.readyAnnouncements();
            logger.info("[Chronicle] Recovering {} pending privacy prompt(s) and {} ready announcement(s)", pendingPrompts.size(), ready.size());
            for (ChronicleDatabase.PendingPrompt pending : pendingPrompts) {
                activatePrompt(pending.playerId(), pending.eventId(), pending.deadlineMs(), true);
            }
            for (ChronicleDatabase.FirstRecord record : ready) announceAndMark(record);
        } catch (Exception failure) {
            logger.error("[Chronicle] Could not recover pending privacy prompts or announcements", failure);
        }
    }

    private void loadExistingBackendKeys() {
        Path plugins = dataDirectory.getParent();
        if (plugins == null) {
            logger.error("[Chronicle] Cannot locate the existing WakeUpLobby backend-key file");
            return;
        }
        Path file = plugins.resolve("wakeuplobby").resolve("authorization-backend-keys.properties");
        if (!Files.isRegularFile(file)) {
            logger.error("[Chronicle] Existing centralized backend keys are missing at {}", file.getFileName());
            return;
        }
        try {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(file)) { properties.load(input); }
            Map<String, byte[]> parsed = new HashMap<>();
            for (String backend : properties.stringPropertyNames()) {
                try {
                    byte[] key = java.util.Base64.getDecoder().decode(properties.getProperty(backend).strip());
                    if (backend.matches("[a-z0-9_-]{1,64}") && key.length >= 32) parsed.put(backend.toLowerCase(Locale.ROOT), key);
                } catch (IllegalArgumentException ignored) { }
            }
            Map<String, byte[]> unique = new HashMap<>();
            java.util.Set<String> duplicateIds = new java.util.HashSet<>();
            for (Map.Entry<String, byte[]> left : parsed.entrySet()) {
                for (Map.Entry<String, byte[]> right : parsed.entrySet()) {
                    if (left.getKey().compareTo(right.getKey()) < 0 && java.security.MessageDigest.isEqual(left.getValue(), right.getValue())) {
                        duplicateIds.add(left.getKey());
                        duplicateIds.add(right.getKey());
                    }
                }
            }
            parsed.forEach((id, key) -> { if (!duplicateIds.contains(id)) unique.put(id, key); });
            backendKeys.putAll(unique);
            logger.info("[Chronicle] Reused {} existing Network Authorization backend keys", backendKeys.size());
        } catch (IOException failure) {
            logger.error("[Chronicle] Could not read existing centralized backend keys", failure);
        }
    }

    private static boolean validOperation(ChronicleProtocol.Request request) {
        int exact = switch (request.operation()) {
            case COMPLETE, SET_PRIVACY, ADMIN_REANNOUNCE, ADMIN_RESET_EVENT, ADMIN_INCLUDE -> 1;
            case HISTORY, MINE, ADMIN_CONCEALED, PROMPT_STATUS, ADMIN_EXCLUSIONS -> 0;
            case ADMIN_EXCLUDE -> 3;
            case ADMIN_REMOVE_PLAYER -> request.args().size();
        };
        if (request.args().size() != exact || (request.operation() == Operation.ADMIN_REMOVE_PLAYER && exact != 1 && exact != 2)) return false;
        return switch (request.operation()) {
            case COMPLETE -> ChronicleEvent.byId(request.args().getFirst()) != null;
            case SET_PRIVACY -> PrivacyMode.parse(request.args().getFirst()) != null;
            case ADMIN_REANNOUNCE -> ChronicleEvent.byId(request.args().getFirst()) != null;
            case ADMIN_RESET_EVENT -> ChronicleEvent.byId(request.args().getFirst()) != null;
            case ADMIN_EXCLUDE -> validUuid(request.args().getFirst());
            case ADMIN_INCLUDE -> validUuid(request.args().getFirst());
            case ADMIN_REMOVE_PLAYER -> validUuid(request.args().getFirst())
                    && (request.args().size() == 1 || ChronicleEvent.byId(request.args().get(1)) != null);
            default -> true;
        };
    }

    private static boolean validUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private void processEntry(Player player, ChronicleEvent event) {
        if (database == null) return;
        UUID playerId = player.getUniqueId();
        String username = player.getUsername();
        recordEntry(playerId, username, event, 0);
    }

    private void recordEntry(UUID playerId, String username, ChronicleEvent event, int retry) {
        databaseExecutor.execute(() -> {
            try {
                ChronicleDatabase.CompletionResult result = database.complete(event.id(), playerId, username);
                if (result.excluded()) {
                    logger.info("[Chronicle] Excluded backend entry ignored event={} player={}", event.id(), playerId);
                    return;
                }
                if (!result.firstClaimed()) return;
                activatePrompt(playerId, event.id(), result.deadlineMs(), true);
            } catch (Exception failure) {
                logger.warn("[Chronicle] Backend entry recording failed event={} player={} attempt={}: {}", event.id(), playerId, retry + 1, failure.toString());
                if (retry < 3) proxy.getScheduler().buildTask(this,
                        () -> recordEntry(playerId, username, event, retry + 1)).delay(Duration.ofSeconds(1)).schedule();
            }
        });
    }

    private void resumePrompt(Player player) {
        if (database == null) return;
        databaseExecutor.execute(() -> {
            try {
                for (ChronicleDatabase.PendingPrompt pending : database.pendingPrompts(player.getUniqueId())) {
                    activatePrompt(player.getUniqueId(), pending.eventId(), pending.deadlineMs(), true);
                }
            } catch (Exception failure) {
                logger.warn("[Chronicle] Could not restore privacy prompt for player={}: {}", player.getUniqueId(), failure.toString());
            }
        });
    }

    private static ChronicleEvent entryEvent(String backend) {
        return switch (backend) {
            case "sky-island" -> ChronicleEvent.FIRST_ENTER_SKY_ISLAND;
            case "ocean" -> ChronicleEvent.FIRST_ENTER_OCEAN;
            case "desert" -> ChronicleEvent.FIRST_ENTER_DESERT;
            case "cave" -> ChronicleEvent.FIRST_ENTER_CAVE;
            case "magic" -> ChronicleEvent.FIRST_ENTER_MAGIC;
            default -> null;
        };
    }

    private void trimResponseCache() {
        long cutoff = System.currentTimeMillis() - RESPONSE_CACHE_MS;
        responses.entrySet().removeIf(entry -> entry.getValue().createdAtMs() < cutoff);
        if (responses.size() > MAX_RESPONSE_CACHE) {
            responses.entrySet().stream().sorted(Map.Entry.comparingByValue((a, b) -> Long.compare(a.createdAtMs(), b.createdAtMs())))
                    .limit(responses.size() - MAX_RESPONSE_CACHE).map(Map.Entry::getKey).toList().forEach(responses::remove);
        }
    }

    private record PromptSession(long deadlineMs) { }
    private record PromptKey(UUID playerId, String eventId) { }
    private record RequestKey(String backend, UUID epoch, UUID nonce) { }
    private record CachedResponse(byte[] bytes, long createdAtMs) { private CachedResponse { bytes = bytes.clone(); } @Override public byte[] bytes() { return bytes.clone(); } }
    private record RequestResult(boolean success, List<String> lines) { private RequestResult { lines = List.copyOf(lines); } }
}
