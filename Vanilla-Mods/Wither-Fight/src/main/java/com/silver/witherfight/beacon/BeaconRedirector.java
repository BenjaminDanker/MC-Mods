package com.silver.witherfight.beacon;

import com.silver.witherfight.WitherFightMod;
import com.silver.witherfight.config.ConfigManager;
import com.silver.witherfight.config.WitherControlConfig;
import com.silver.portalprotocol.PortalRequestPayload;
import com.silver.portalprotocol.PortalRequestPayloadCodec;
import com.silver.portalprotocol.PortalRequestSigner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.gameevent.GameEvent;

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

    public static void handlePlayer(ServerPlayer player, ServerLevel world) {
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
        Long lastRedirect = lastRedirects.get(player.getUUID());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            return;
        }

        if (!executeRedirect(player, config, world)) {
            return;
        }

        lastRedirects.put(player.getUUID(), now);
        removeBeacon(world, beaconPos, player);
    }

    private static BlockPos findActivatedBeacon(ServerLevel world, ServerPlayer player) {
        BlockPos beaconPos = player.blockPosition().below();
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(beaconPos);
        if (!(blockEntity instanceof BeaconBlockEntity beaconEntity)) {
            return null;
        }

        if (beaconEntity.getBeamSections().isEmpty()) {
            return null;
        }

        WitherFightMod.LOGGER.info("Active beacon detected under {} at {}", player.getName().getString(), beaconPos);
        return beaconPos;
    }

    private static boolean executeRedirect(ServerPlayer player, WitherControlConfig config, ServerLevel world) {
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

        player.sendSystemMessage(Component.literal("Redirecting you to " + targetServer + "..."));

        String destinationPortal = config.portalRedirectTargetPortal();
        if (destinationPortal == null) {
            destinationPortal = "";
        }

        long issuedAtMs = System.currentTimeMillis();
        String nonce = PortalRequestPayloadCodec.generateNonce();
        byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(player.getUUID(), targetServer, destinationPortal, issuedAtMs, nonce);
        byte[] signature = PortalRequestSigner.hmacSha256(secret, unsigned);
        byte[] signed = PortalRequestPayloadCodec.encodeSigned(player.getUUID(), targetServer, destinationPortal, issuedAtMs, nonce, signature);
        ServerPlayNetworking.send(player, new PortalRequestPayload(signed));
        WitherFightMod.LOGGER.info("Sent beacon redirect portal request for {} -> {}", player.getName().getString(), targetServer);
        return true;
    }

    private static void removeBeacon(ServerLevel world, BlockPos beaconPos, ServerPlayer player) {
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return;
        }

        world.setBlock(beaconPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.gameEvent(GameEvent.BLOCK_DESTROY, beaconPos, GameEvent.Context.of(player, state));
        WitherFightMod.LOGGER.info("Removed beacon at {} after redirecting {}", beaconPos, player.getName().getString());
    }





}
