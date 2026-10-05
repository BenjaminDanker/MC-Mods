package com.silver.portalprotocol;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Delays a signed proxy request until the optional pet source reservation settles. */
public final class PortalPetTransferGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalPetTransferGate.class);

    private PortalPetTransferGate() { }

    /** Returns whether a valid request was queued. Preparation never blocks a server tick. */
    public static boolean send(ServerPlayer player, String targetServer,
            String arrivalPortal, String secret) {
        if (player == null || player.isRemoved() || targetServer == null
                || targetServer.isBlank() || secret == null || secret.isBlank()) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        String target = targetServer.trim();
        CompletionStage<?> preparation = prepare(player, target);
        preparation.toCompletableFuture().orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((outcome, failure) -> server.execute(() -> {
                    if (failure != null) {
                        LOGGER.warn("Pet transfer preparation failed for {}; proxy request continues",
                                player.getName().getString(), failure);
                    }
                    // A logout while SQL was in flight must not send a stale proxy request.
                    if (player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player)
                        return;
                    try {
                        long issuedAt = System.currentTimeMillis();
                        String nonce = PortalRequestPayloadCodec.generateNonce();
                        String portal = arrivalPortal == null ? "" : arrivalPortal;
                        byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(
                                player.getUUID(), target, portal, issuedAt, nonce);
                        byte[] signature = PortalRequestSigner.hmacSha256(secret.trim(), unsigned);
                        ServerPlayNetworking.send(player, new PortalRequestPayload(
                                PortalRequestPayloadCodec.encodeSigned(player.getUUID(), target,
                                        portal, issuedAt, nonce, signature)));
                    } catch (RuntimeException sendFailure) {
                        LOGGER.error("Signed proxy request failed for {}", player.getName().getString(),
                                sendFailure);
                    }
                }));
        return true;
    }

    private static CompletionStage<?> prepare(ServerPlayer player, String target) {
        if (!FabricLoader.getInstance().isModLoaded("pet_companion"))
            return CompletableFuture.completedFuture(null);
        try {
            Class<?> mod = Class.forName("com.silver.aipets.fabric.PetCompanionMod");
            Object result = mod.getMethod("preparePortalTransfer", ServerPlayer.class, String.class)
                    .invoke(null, player, target);
            return result instanceof CompletionStage<?> stage ? stage
                    : CompletableFuture.failedFuture(new IllegalStateException(
                            "Pet Companion transfer hook returned no completion stage"));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
