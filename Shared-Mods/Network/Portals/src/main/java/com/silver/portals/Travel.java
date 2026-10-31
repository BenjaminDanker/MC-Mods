package com.silver.portals;

import com.silver.authorization.NetworkShared;
import com.silver.authorization.PortalRequestCodec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Leaving this server: a player who steps into a portal (or whom a mod sends) is moved by the proxy. This server
 * sends the proxy a request signed with the network's portal secret: who, to which server, and which arrival point
 * there. If the Pet Companion mod is here, the player's pet is reserved for the trip first.
 */
final class Travel {
    /** The signed "move this player" request, read by the proxy. */
    record Request(byte[] bytes) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("portals", "transfer"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.ofMember(
            (request, buf) -> buf.writeBytes(request.bytes()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new Request(bytes);
            });

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** After joining, portals ignore a player for this long (they may have arrived standing in one). */
    private static final long JOIN_GRACE_MS = 2_000;
    /** A player who just came out of a portal can't go straight back into the one that sent them. */
    private static final long LOOP_GUARD_MS = 3_000;

    private static final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Used> lastUsed = new ConcurrentHashMap<>();
    /** Players whose trip is being prepared (pet reservation in flight): one trip at a time. */
    private static final Set<UUID> preparing = ConcurrentHashMap.newKeySet();

    private record Used(String portal, long at) {
    }

    private Travel() {
    }

    static void joined(UUID player) {
        joinedAt.put(player, System.currentTimeMillis());
    }

    static void left(UUID player) {
        joinedAt.remove(player);
        lastUsed.remove(player);
    }

    static void cameThrough(UUID player, String portal) {
        if (portal == null) {
            lastUsed.remove(player);
        } else {
            lastUsed.put(player, new Used(portal, System.currentTimeMillis()));
        }
    }

    /** Vanilla's portal timer fired for a player standing in {@code portal}. */
    static void steppedIn(ServerPlayer player, PortalConfig.Portal portal) {
        UUID id = player.getUUID();
        Long joined = joinedAt.get(id);
        if (joined != null && System.currentTimeMillis() - joined < JOIN_GRACE_MS) {
            return;
        }
        if (Arrivals.pending(id)) {
            return; // they are arriving; the arrival moves them
        }
        Used used = lastUsed.get(id);
        if (used != null && portal.toPortal != null && portal.toPortal.equals(used.portal())
                && System.currentTimeMillis() - used.at() < LOOP_GUARD_MS) {
            return;
        }
        cameThrough(id, portal.name);
        if (portal.toServer == null || portal.toServer.isBlank()) {
            PortalsMod.LOGGER.warn("Portal {} leads to no server", portal.name);
            return;
        }
        String arrival = portal.toPortal != null && !portal.toPortal.isBlank() ? portal.toPortal.trim() : portal.name;
        send(player, portal.toServer.trim(), arrival, "portal " + portal.name);
    }

    /** See {@link Portals#transfer}. */
    static boolean send(ServerPlayer player, String server, String arrival, String label) {
        if (server == null || server.isBlank()) {
            PortalsMod.LOGGER.warn("{}: no target server", label);
            return false;
        }
        if (secret().isBlank()) {
            PortalsMod.LOGGER.error("ALERT: no portal_request_secret in the network's shared values; {} can't move {}", label,
                player.getName().getString());
            return false;
        }
        if (!preparing.add(player.getUUID())) {
            return false;
        }
        MinecraftServer minecraft = player.level().getServer();
        String target = server.trim();
        String arrivalName = arrival == null ? "" : arrival.trim();
        reservePet(player, target).toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).whenComplete((ignored, failure) ->
            minecraft.execute(() -> {
                try {
                    if (failure != null) {
                        PortalsMod.LOGGER.warn("Reserving {}'s pet for the trip failed; they go without it", player.getName().getString(), failure);
                    }
                    // someone who logged out meanwhile must not be moved by a stale request
                    if (!player.isRemoved() && minecraft.getPlayerList().getPlayer(player.getUUID()) == player) {
                        sign(player, target, arrivalName);
                        PortalsMod.LOGGER.info("{}: asked the proxy to move {} to {}", label, player.getName().getString(), target);
                    }
                } catch (RuntimeException sendFailure) {
                    PortalsMod.LOGGER.error("{}: could not ask the proxy to move {}", label, player.getName().getString(), sendFailure);
                } finally {
                    preparing.remove(player.getUUID());
                }
            }));
        return true;
    }

    private static void sign(ServerPlayer player, String target, String arrival) {
        long issuedAt = System.currentTimeMillis();
        String nonce = PortalRequestCodec.generateNonce();
        byte[] unsigned = PortalRequestCodec.encodeUnsigned(player.getUUID(), target, arrival, issuedAt, nonce);
        byte[] signed = PortalRequestCodec.encodeSigned(player.getUUID(), target, arrival, issuedAt, nonce, hmac(secret(), unsigned));
        ServerPlayNetworking.send(player, new Request(signed));
    }

    private static String secret() {
        return NetworkShared.get("portal_request_secret", "").trim();
    }

    private static byte[] hmac(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception failure) {
            throw new IllegalStateException("could not sign the transfer request", failure);
        }
    }

    /** Pet Companion (if installed) reserves the player's pet so it follows them; called by name. */
    private static CompletionStage<?> reservePet(ServerPlayer player, String target) {
        if (!FabricLoader.getInstance().isModLoaded("pet_companion")) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            Object stage = Class.forName("com.silver.aipets.fabric.PetCompanionMod")
                .getMethod("preparePortalTransfer", ServerPlayer.class, String.class).invoke(null, player, target);
            return stage instanceof CompletionStage<?> completion ? completion
                : CompletableFuture.failedFuture(new IllegalStateException("Pet Companion returned no completion stage"));
        } catch (ReflectiveOperationException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
