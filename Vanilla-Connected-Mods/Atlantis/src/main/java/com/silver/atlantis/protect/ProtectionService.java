package com.silver.atlantis.protect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles lightweight protection-related player notifications each tick.
 */
public final class ProtectionService {

    private static final Text INNER_AIR_ENTER_MESSAGE = Text.literal("Celantis build by Natac");

    private final Set<UUID> playersInsideInnerAir = new HashSet<>();

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftServer server) {
        checkInnerAirEntry(server);
    }

    private void checkInnerAirEntry(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            online.add(player.getUuid());

            ServerWorld world = player.getEntityWorld();
            BlockPos feetPos = player.getBlockPos();
            BlockPos headPos = feetPos.up();

            boolean isInsideInnerAir =
                ProtectionManager.INSTANCE.isInteriorProtected(world, feetPos)
                    || ProtectionManager.INSTANCE.isInteriorProtected(world, headPos);

            boolean wasInsideInnerAir = playersInsideInnerAir.contains(player.getUuid());
            if (isInsideInnerAir && !wasInsideInnerAir) {
                player.sendMessage(INNER_AIR_ENTER_MESSAGE, true);
                playersInsideInnerAir.add(player.getUuid());
            } else if (!isInsideInnerAir && wasInsideInnerAir) {
                playersInsideInnerAir.remove(player.getUuid());
                // Clear the previous action-bar notification immediately when
                // the player leaves the protected interior (including after undo).
                player.sendMessage(Text.empty(), true);
            }
        }

        playersInsideInnerAir.retainAll(online);
    }
}
