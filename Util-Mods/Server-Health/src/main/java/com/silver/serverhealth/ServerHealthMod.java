package com.silver.serverhealth;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Every 100 ticks (about 5 s) sends this server's ticks per second (measured against the clock), its average and worst tick time and
 * its player count and names to the host's capacity service
 * (Host-Ops host_capacity.py). The service finds which server this is from the process id, so nothing is configured per server.
 * A server whose ticks stay slow takes no new players from the proxy until it recovers.
 *
 * <p>It also answers command suggestions on a loopback-only port (reported with the numbers): GET /suggest?q=&lt;command typed
 * so far&gt; returns what the game's own command parser would offer next (player names, ids, items, ...), the same as tab
 * completion in the console. The home page's admin side uses it for autofill.</p>
 */
public final class ServerHealthMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("serverhealth");
    private static final URI REPORT = URI.create(System.getProperty("serverhealth.url", "http://127.0.0.1:25580/report"));
    private static final int WINDOW_TICKS = 100;
    /** A window normally takes 5 s; even a badly lagging server (4 TPS) finishes one within 25 s. */
    private static final double MAX_WINDOW_SECONDS = 30.0;
    private static final long PID = ProcessHandle.current().pid();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private long tickStartNanos;
    private long windowStartNanos;
    private long sumNanos;
    private long maxNanos;
    private int ticks;
    private volatile boolean failing;

    private volatile int suggestPort;
    private com.sun.net.httpserver.HttpServer suggestHttp;

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(this::endTick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(this::startSuggestions);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (suggestHttp != null) suggestHttp.stop(0);
        });
    }

    private void startSuggestions(MinecraftServer server) {
        try {
            var http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
            http.createContext("/suggest", exchange -> {
                String body;
                int code = 200;
                try {
                    String raw = exchange.getRequestURI().getRawQuery();
                    String q = "";
                    if (raw != null) {
                        for (String part : raw.split("&")) {
                            if (part.startsWith("q=")) q = java.net.URLDecoder.decode(part.substring(2), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                    if (q.length() > 300) q = q.substring(0, 300);
                    String typed = q;
                    // parsing reads the world (players, entities): on the server thread
                    com.mojang.brigadier.suggestion.Suggestions found = server.submit(() -> {
                        var dispatcher = server.getCommands().getDispatcher();
                        return dispatcher.getCompletionSuggestions(dispatcher.parse(typed, server.createCommandSourceStack()));
                    }).thenCompose(future -> future).get(2, java.util.concurrent.TimeUnit.SECONDS);
                    StringBuilder items = new StringBuilder();
                    int n = 0;
                    for (var suggestion : found.getList()) {
                        if (n++ >= 60) break;
                        items.append(items.isEmpty() ? "" : ",").append('"').append(json(suggestion.getText())).append('"');
                    }
                    body = "{\"start\":" + found.getRange().getStart() + ",\"items\":[" + items + "]}";
                } catch (Exception failure) {
                    code = 500;
                    body = "{\"start\":0,\"items\":[]}";
                }
                byte[] data = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, data.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(data);
                }
            });
            http.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread t = new Thread(task, "serverhealth-suggest");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            suggestHttp = http;
            suggestPort = http.getAddress().getPort();
        } catch (java.io.IOException failure) {
            LOGGER.warn("Command suggestions are off (could not open a loopback port): {}", failure.toString());
        }
    }

    private void endTick(MinecraftServer server) {
        long now = System.nanoTime();
        long nanos = now - tickStartNanos;
        if (ticks == 0) {
            windowStartNanos = tickStartNanos;
        }
        sumNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
        if (++ticks < WINDOW_TICKS) {
            return;
        }
        double mspt = sumNanos / (double) ticks / 1e6;
        double msptMax = maxNanos / 1e6;
        // ticks over the wall time they took: 20 when the server keeps up, less when it lags
        double seconds = (now - windowStartNanos) / 1e9;
        ticks = 0;
        sumNanos = 0;
        maxNanos = 0;
        if (seconds > MAX_WINDOW_SECONDS) {
            return; // the server paused while empty part-way through: this window says nothing about lag
        }
        StringBuilder names = new StringBuilder();
        for (var player : server.getPlayerList().getPlayers()) {
            names.append(names.isEmpty() ? "" : ",").append('"').append(json(player.getName().getString())).append('"');
        }
        send(WINDOW_TICKS / seconds, mspt, msptMax, server.getPlayerCount(), names.toString());
    }

    private static String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void send(double tps, double mspt, double msptMax, int players, String names) {
        String body = String.format(Locale.ROOT, "{\"pid\":%d,\"tps\":%.2f,\"mspt\":%.2f,\"mspt_max\":%.2f,\"players\":%d,\"names\":[%s],\"port\":%d}",
                PID, Math.min(20.0, tps), mspt, msptMax, players, names, suggestPort);
        HttpRequest request = HttpRequest.newBuilder(REPORT)
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            boolean bad = error != null || response.statusCode() != 200;
            if (bad && !failing) {
                LOGGER.warn("Can't reach the capacity service at {} ({}); still trying every 5 s", REPORT,
                        error != null ? error.toString() : "HTTP " + response.statusCode());
            } else if (!bad && failing) {
                LOGGER.info("Reporting to the capacity service again");
            }
            failing = bad;
        });
    }
}
