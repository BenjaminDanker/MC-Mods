package com.silver.chronicle.fabric;

import com.silver.chronicle.common.ChronicleProtocol;
import com.silver.chronicle.common.ChronicleProtocol.Operation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/** Sends signed, player-bound requests through the existing backend connection. */
public final class ChronicleRuntime {
    private static final long RETRY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_PENDING = 512;

    private final BackendIdentity identity;
    private final Logger logger;
    private final UUID backendEpoch = UUID.randomUUID();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private long tick;

    public ChronicleRuntime(BackendIdentity identity, Logger logger) {
        this.identity = identity;
        this.logger = logger;
    }

    public void register() {
        ServerPlayNetworking.registerGlobalReceiver(ChroniclePayload.ID, (payload, context) -> {
            byte[] bytes = payload.bytes();
            context.server().execute(() -> receive(bytes, context.player()));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 5 != 0 || pending.isEmpty()) return;
            for (Pending request : List.copyOf(pending.values())) {
                if (request.nextAttemptTick() > tick) continue;
                if (request.attempts() >= MAX_ATTEMPTS) {
                    pending.remove(request.nonce());
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(request.playerId());
                if (player == null) {
                    pending.put(request.nonce(), request.withNext(tick + RETRY_TICKS));
                } else {
                    send(player, request);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(this::stop);
    }

    UUID backendEpoch() { return backendEpoch; }

    void complete(ServerPlayer player, String eventId) {
        if (player == null || eventId == null || eventId.length() > 80) return;
        request(player, Operation.COMPLETE, List.of(eventId), ignored -> { });
    }

    public void request(ServerPlayer player, Operation operation, List<String> args, Consumer<ChronicleProtocol.Response> callback) {
        if (player == null) return;
        if (identity.key().isEmpty() || identity.backendId().isBlank()) {
            player.sendSystemMessage(Component.literal("Chronicle is temporarily unavailable."));
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            player.sendSystemMessage(Component.literal("Chronicle is busy. Please try again shortly."));
            return;
        }
        UUID nonce = UUID.randomUUID();
        ChronicleProtocol.Request unsigned = new ChronicleProtocol.Request(ChronicleProtocol.VERSION,
                identity.backendId(), player.getUUID(), player.getGameProfile().name(), backendEpoch,
                nonce, System.currentTimeMillis(), operation, args, "");
        byte[] frame = ChronicleProtocol.encode(ChronicleProtocol.sign(unsigned, identity.key().orElseThrow()));
        Pending request = new Pending(player.getUUID(), nonce, frame, 0, tick, callback);
        pending.put(nonce, request);
        send(player, request);
    }

    void receive(byte[] bytes, ServerPlayer player) {
        if (player == null || identity.key().isEmpty()) return;
        try {
            ChronicleProtocol.Response response = ChronicleProtocol.decodeResponse(bytes);
            Pending request = pending.get(response.nonce());
            if (request == null || response.version() != ChronicleProtocol.VERSION
                    || !response.backendId().equals(identity.backendId())
                    || !response.playerId().equals(player.getUUID())
                    || !response.backendEpoch().equals(backendEpoch)
                    || !request.playerId().equals(player.getUUID())
                    || !ChronicleProtocol.verify(response, identity.key().orElseThrow())) return;
            if (!response.success() && request.attempts() < MAX_ATTEMPTS) {
                pending.put(response.nonce(), request.withNext(tick + RETRY_TICKS));
                return;
            }
            pending.remove(response.nonce());
            for (String line : response.lines()) player.sendSystemMessage(Component.literal(line));
            request.callback().accept(response);
        } catch (RuntimeException malformed) {
            logger.debug("[Chronicle] Rejected malformed proxy response: {}", malformed.toString());
        }
    }

    void stop(MinecraftServer server) { pending.clear(); }

    private void send(ServerPlayer player, Pending request) {
        Pending attempted = request.withAttempt(request.attempts() + 1, tick + RETRY_TICKS);
        pending.put(request.nonce(), attempted);
        try {
            ServerPlayNetworking.send(player, new ChroniclePayload(request.frame()));
        } catch (RuntimeException failure) {
            logger.debug("[Chronicle] Could not send proxy request: {}", failure.toString());
        }
    }

    private record Pending(UUID playerId, UUID nonce, byte[] frame, int attempts,
                          long nextAttemptTick, Consumer<ChronicleProtocol.Response> callback) {
        private Pending { frame = frame.clone(); }
        @Override public byte[] frame() { return frame.clone(); }
        Pending withAttempt(int value, long next) { return new Pending(playerId, nonce, frame, value, next, callback); }
        Pending withNext(long next) { return new Pending(playerId, nonce, frame, attempts, next, callback); }
    }
}
