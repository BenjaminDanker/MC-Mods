package com.silver.wardenfight.teleport;

import com.silver.wardenfight.WardenFightMod;
import com.silver.wardenfight.config.ConfigManager;
import com.silver.wardenfight.config.WardenControlConfig;
import com.silver.wakeuplobby.portal.PortalRequestPayload;
import com.silver.wakeuplobby.portal.PortalRequestPayloadCodec;
import com.silver.wakeuplobby.portal.PortalRequestSigner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;

/**
 * Handles teleporting nearby players after the Warden dies.
 */
public final class WardenRedirector {
    private WardenRedirector() {
    }

    public static void handleWardenDeath(ServerWorld world, BlockPos deathPos) {
        ConfigManager manager = WardenFightMod.getConfigManager();
        if (manager == null) {
            WardenFightMod.LOGGER.warn("Warden death redirect skipped because config manager was not initialised");
            return;
        }

        WardenControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            WardenFightMod.LOGGER.info("Warden redirect disabled via config; no action taken at {}", deathPos);
            return;
        }

        int range = Math.max(1, config.portalRedirectRange());
        WardenFightMod.LOGGER.info("Warden died at {} in world {}; evaluating nearby players", deathPos, world.getRegistryKey().getValue());

        List<ServerPlayerEntity> targets = world.getPlayers(isWithinSquare(deathPos, range));
        WardenFightMod.LOGGER.info("Found {} eligible players within {} blocks of {}", targets.size(), range, deathPos);
        if (targets.isEmpty()) {
            WardenFightMod.LOGGER.info("No players within {} blocks of {} when Warden died", range, deathPos);
            return;
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        final String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            WardenFightMod.LOGGER.warn("Warden redirect target server missing; skipping");
            return;
        }

        final String secret = config.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            WardenFightMod.LOGGER.warn("portalRequestSecret is blank; cannot send portal requests");
            return;
        }

        String destinationPortalCandidate = config.portalRedirectTargetPortal();
        final String destinationPortal = destinationPortalCandidate != null ? destinationPortalCandidate : "";

        for (ServerPlayerEntity player : targets) {
            if (player.isRemoved()) {
                continue;
            }

            player.sendMessage(Text.literal("Warden defeated! Redirecting you to " + targetServer + "..."), false);
            ServerPlayerEntity requestPlayer = player;
            server.execute(() -> {
                try {
                    long issuedAtMs = System.currentTimeMillis();
                    String nonce = PortalRequestPayloadCodec.generateNonce();
                    byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(requestPlayer.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce);
                    byte[] signature = PortalRequestSigner.hmacSha256(secret, unsigned);
                    byte[] signed = PortalRequestPayloadCodec.encodeSigned(requestPlayer.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce, signature);
                    ServerPlayNetworking.send(requestPlayer, new PortalRequestPayload(signed));
                    WardenFightMod.LOGGER.info("Sent warden redirect portal request for {} -> {}", requestPlayer.getName().getString(), targetServer);
                } catch (Exception ex) {
                    WardenFightMod.LOGGER.error("Error sending warden redirect portal request for {}", requestPlayer.getName().getString(), ex);
                }
            });
        }
    }

    private static Predicate<ServerPlayerEntity> isWithinSquare(BlockPos center, int range) {
        return player -> {
            if (player.isSpectator()) {
                return false;
            }
            int dx = Math.abs(player.getBlockX() - center.getX());
            int dz = Math.abs(player.getBlockZ() - center.getZ());
            int dy = Math.abs(player.getBlockY() - center.getY());
            return dx <= range && dz <= range && dy <= range;
        };
    }
}
