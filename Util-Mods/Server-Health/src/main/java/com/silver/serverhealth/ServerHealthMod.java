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
 * Every 100 ticks (about 5 s) sends this server's average and worst tick time and its player count to the host's capacity service
 * (Host-Ops host_capacity.py). The service finds which server this is from the process id, so nothing is configured per server.
 * A server whose ticks stay slow takes no new players from the proxy until it recovers.
 */
public final class ServerHealthMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("serverhealth");
    private static final URI REPORT = URI.create(System.getProperty("serverhealth.url", "http://127.0.0.1:25580/report"));
    private static final int WINDOW_TICKS = 100;
    private static final long PID = ProcessHandle.current().pid();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private long tickStartNanos;
    private long sumNanos;
    private long maxNanos;
    private int ticks;
    private volatile boolean failing;

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(this::endTick);
    }

    private void endTick(MinecraftServer server) {
        long nanos = System.nanoTime() - tickStartNanos;
        sumNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
        if (++ticks < WINDOW_TICKS) {
            return;
        }
        double mspt = sumNanos / (double) ticks / 1e6;
        double msptMax = maxNanos / 1e6;
        ticks = 0;
        sumNanos = 0;
        maxNanos = 0;
        send(mspt, msptMax, server.getPlayerCount());
    }

    private void send(double mspt, double msptMax, int players) {
        String body = String.format(Locale.ROOT, "{\"pid\":%d,\"mspt\":%.2f,\"mspt_max\":%.2f,\"players\":%d}", PID, mspt, msptMax, players);
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
