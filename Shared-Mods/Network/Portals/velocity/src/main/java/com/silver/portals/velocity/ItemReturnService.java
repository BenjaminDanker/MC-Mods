package com.silver.portals.velocity;

import com.silver.wakeup.api.ItemReturn;
import com.silver.wakeup.api.ReturnRemoveResponse;
import com.silver.wakeup.config.ReturnSpecial;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Asks Player Sync (on the player's current backend, channel playersync:item_return) to remove the special items a
 * player must give up when they use /return. Registered with the routing plugin as its {@link ItemReturn}.
 */
public final class ItemReturnService implements ItemReturn {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("playersync:item_return");

    private final Logger logger;
    private final Map<UUID, CompletableFuture<ReturnRemoveResponse>> pendingReturnRemovals = new ConcurrentHashMap<>();

    public ItemReturnService(Logger logger) {
        this.logger = logger;
    }

    /** Handles a Player Sync reply; consumed here, never forwarded to the client. */
    public void onPluginMessage(PluginMessageEvent event) {
        byte[] data = event.getData();
        try (var in = new DataInputStream(new ByteArrayInputStream(data))) {
            long msb = in.readLong();
            long lsb = in.readLong();
            UUID requestId = new UUID(msb, lsb);

            boolean success = in.readBoolean();
            int removedInv = in.readInt();
            int removedDb = in.readInt();
            int remainingInv = in.readInt();
            int remainingDb = in.readInt();
            String error = readStringBytes(in, 8192);

            CompletableFuture<ReturnRemoveResponse> fut = pendingReturnRemovals.remove(requestId);
            if (fut != null) {
                fut.complete(new ReturnRemoveResponse(success, removedInv, removedDb, remainingInv, remainingDb, error));
            }
        } catch (Exception ex) {
            logger.warn("[Portals] Failed parsing playersync:item_return response: {}", ex.toString());
        }

        // This is a proxy-internal control message; do not forward to the client.
        try {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        } catch (Throwable ignored) {
            // Older Velocity API variants may not expose ForwardResult; safe to ignore.
        }
    }

    @Override
    public CompletableFuture<ReturnRemoveResponse> requestItemReturn(Player player, List<ReturnSpecial> specials) {
        if (player == null || specials == null || specials.isEmpty()) {
            return CompletableFuture.completedFuture(new ReturnRemoveResponse(true, 0, 0, 0, 0, ""));
        }

        var current = player.getCurrentServer();
        if (current.isEmpty()) {
            return CompletableFuture.completedFuture(new ReturnRemoveResponse(false, 0, 0, 0, 0, "Not connected to a server"));
        }

        UUID requestId = UUID.randomUUID();
        CompletableFuture<ReturnRemoveResponse> fut = new CompletableFuture<>();
        pendingReturnRemovals.put(requestId, fut);

        byte[] payload;
        try (var bos = new ByteArrayOutputStream(); var out = new DataOutputStream(bos)) {
            out.writeLong(requestId.getMostSignificantBits());
            out.writeLong(requestId.getLeastSignificantBits());
            out.writeInt(Math.min(specials.size(), 32));
            for (int i = 0; i < specials.size() && i < 32; i++) {
                ReturnSpecial spec = specials.get(i);
                writeStringBytes(out, spec.key(), 128);
                writeStringBytes(out, spec.value(), 256);
            }
            out.flush();
            payload = bos.toByteArray();
        } catch (Exception ex) {
            pendingReturnRemovals.remove(requestId);
            return CompletableFuture.completedFuture(new ReturnRemoveResponse(false, 0, 0, 0, 0, "Encode failed: " + ex));
        }

        boolean sent = current.get().sendPluginMessage(CHANNEL, payload);
        if (!sent) {
            pendingReturnRemovals.remove(requestId);
            return CompletableFuture.completedFuture(new ReturnRemoveResponse(false, 0, 0, 0, 0, "Server did not accept plugin message"));
        }

        return fut.orTimeout(4, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    pendingReturnRemovals.remove(requestId);
                    return new ReturnRemoveResponse(false, 0, 0, 0, 0, "Timeout waiting for Player Sync confirmation");
                });
    }

    private static void writeStringBytes(DataOutputStream out, String value, int maxLen) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLen) {
            throw new IllegalArgumentException("String too long: " + bytes.length);
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readStringBytes(DataInputStream in, int maxLen) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > maxLen) {
            throw new IllegalArgumentException("Bad string length: " + len);
        }
        byte[] bytes = in.readNBytes(len);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
