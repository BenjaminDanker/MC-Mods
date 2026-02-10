package com.silver.elderguardianfight.teleport;

import com.silver.elderguardianfight.ElderGuardianFightMod;
import com.silver.elderguardianfight.config.ConfigManager;
import com.silver.elderguardianfight.config.ElderGuardianControlConfig;
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
 * Handles teleporting nearby players after the Elder Guardian dies.
 */
public final class GuardianRedirector {
    private static final int HALF_RANGE = 10;

    private GuardianRedirector() {
    }

    public static void handleGuardianDeath(ServerWorld world, BlockPos deathPos) {
        ConfigManager manager = ElderGuardianFightMod.getConfigManager();
        if (manager == null) {
            ElderGuardianFightMod.LOGGER.warn("Guardian death redirect skipped because config manager was not initialised");
            return;
        }

        ElderGuardianControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            ElderGuardianFightMod.LOGGER.info("Guardian redirect disabled via config; no action taken at {}", deathPos);
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Elder Guardian died at {} in world {}; evaluating nearby players", deathPos, world.getRegistryKey().getValue());

        List<ServerPlayerEntity> targets = world.getPlayers(isWithinSquare(deathPos));
        ElderGuardianFightMod.LOGGER.info("Found {} eligible players within {} blocks of {}", targets.size(), HALF_RANGE, deathPos);
        if (targets.isEmpty()) {
            ElderGuardianFightMod.LOGGER.info("No players within {} blocks of {} when Elder Guardian died", HALF_RANGE, deathPos);
            return;
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        final String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            ElderGuardianFightMod.LOGGER.warn("Guardian redirect target server missing; skipping");
            return;
        }

        final String secret = config.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            ElderGuardianFightMod.LOGGER.warn("portalRequestSecret is blank; cannot send portal requests");
            return;
        }

        String destinationPortalCandidate = config.portalRedirectTargetPortal();
        final String destinationPortal = destinationPortalCandidate != null ? destinationPortalCandidate : "";

        for (ServerPlayerEntity player : targets) {
            if (player.isRemoved()) {
                continue;
            }

            player.sendMessage(Text.literal("Elder Guardian defeated! Redirecting you to " + targetServer + "..."), false);
            ServerPlayerEntity requestPlayer = player;
            server.execute(() -> {
                try {
                    long issuedAtMs = System.currentTimeMillis();
                    String nonce = PortalRequestPayloadCodec.generateNonce();
                    byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(requestPlayer.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce);
                    byte[] signature = PortalRequestSigner.hmacSha256(secret, unsigned);
                    byte[] signed = PortalRequestPayloadCodec.encodeSigned(requestPlayer.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce, signature);
                    ServerPlayNetworking.send(requestPlayer, new PortalRequestPayload(signed));
                    ElderGuardianFightMod.LOGGER.info("Sent guardian redirect portal request for {} -> {}", requestPlayer.getName().getString(), targetServer);
                } catch (Exception ex) {
                    ElderGuardianFightMod.LOGGER.error("Error sending guardian redirect portal request for {}", requestPlayer.getName().getString(), ex);
                }
            });
        }
    }

    private static Predicate<ServerPlayerEntity> isWithinSquare(BlockPos center) {
        return player -> {
            if (player.isSpectator()) {
                return false;
            }
            int dx = Math.abs(player.getBlockX() - center.getX());
            int dz = Math.abs(player.getBlockZ() - center.getZ());
            int dy = Math.abs(player.getBlockY() - center.getY());
            return dx <= HALF_RANGE && dz <= HALF_RANGE && dy <= HALF_RANGE;
        };
    }
}
