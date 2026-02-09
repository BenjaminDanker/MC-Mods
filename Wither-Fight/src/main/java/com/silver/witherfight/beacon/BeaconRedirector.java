package com.silver.witherfight.beacon;

import com.silver.witherfight.WitherFightMod;
import com.silver.witherfight.config.ConfigManager;
import com.silver.witherfight.config.WitherControlConfig;
import com.silver.wakeuplobby.portal.PortalRequestPayload;
import com.silver.wakeuplobby.portal.PortalRequestPayloadCodec;
import com.silver.wakeuplobby.portal.PortalRequestSigner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.GameEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when a player stands on an activated beacon, executes the configured redirect command,
 * and removes the beacon that triggered the transfer.
 */
public final class BeaconRedirector {
    private static final long REDIRECT_COOLDOWN_MS = 3_000L;

    private static final Map<UUID, Long> lastRedirects = new ConcurrentHashMap<>();

    private BeaconRedirector() {
    }

    public static void handlePlayer(ServerPlayerEntity player, ServerWorld world) {
        if (player.isSpectator() || player.isRemoved()) {
            return;
        }

        ConfigManager manager = WitherFightMod.getConfigManager();
        if (manager == null) {
            return;
        }

        WitherControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            return;
        }

        BlockPos beaconPos = findActivatedBeacon(world, player);
        if (beaconPos == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRedirect = lastRedirects.get(player.getUuid());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            return;
        }

        if (!executeRedirect(player, config, world)) {
            return;
        }

        lastRedirects.put(player.getUuid(), now);
        removeBeacon(world, beaconPos, player);
    }

    private static BlockPos findActivatedBeacon(ServerWorld world, ServerPlayerEntity player) {
        BlockPos beaconPos = player.getBlockPos().down();
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(beaconPos);
        if (!(blockEntity instanceof BeaconBlockEntity beaconEntity)) {
            return null;
        }

        if (beaconEntity.getBeamSegments().isEmpty()) {
            return null;
        }

        WitherFightMod.LOGGER.info("Active beacon detected under {} at {}", player.getName().getString(), beaconPos);
        return beaconPos;
    }

    private static boolean executeRedirect(ServerPlayerEntity player, WitherControlConfig config, ServerWorld world) {
        String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            WitherFightMod.LOGGER.warn("Beacon redirect target server missing; skipping redirect for {}", player.getName().getString());
            return false;
        }

        String secret = config.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            WitherFightMod.LOGGER.warn("portalRequestSecret is blank; cannot send portal request for {}", player.getName().getString());
            return false;
        }

        player.sendMessage(Text.literal("Redirecting you to " + targetServer + "..."), false);

        String destinationPortal = config.portalRedirectTargetPortal();
        if (destinationPortal == null) {
            destinationPortal = "";
        }

        long issuedAtMs = System.currentTimeMillis();
        String nonce = PortalRequestPayloadCodec.generateNonce();
        byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(player.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce);
        byte[] signature = PortalRequestSigner.hmacSha256(secret, unsigned);
        byte[] signed = PortalRequestPayloadCodec.encodeSigned(player.getUuid(), targetServer, destinationPortal, issuedAtMs, nonce, signature);
        ServerPlayNetworking.send(player, new PortalRequestPayload(signed));
        WitherFightMod.LOGGER.info("Sent beacon redirect portal request for {} -> {}", player.getName().getString(), targetServer);
        return true;
    }

    private static void removeBeacon(ServerWorld world, BlockPos beaconPos, ServerPlayerEntity player) {
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return;
        }

        world.setBlockState(beaconPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
        world.emitGameEvent(GameEvent.BLOCK_DESTROY, beaconPos, GameEvent.Emitter.of(player, state));
        WitherFightMod.LOGGER.info("Removed beacon at {} after redirecting {}", beaconPos, player.getName().getString());
    }





}
