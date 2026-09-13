package com.silver.atlantis.protect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles lightweight protection-related player notifications each tick.
 */
public final class ProtectionService {

    private static final Component INNER_AIR_ENTER_MESSAGE = Component.literal("Celantis build by Natac");

    private final Set<UUID> playersInsideInnerAir = new HashSet<>();

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftServer server) {
        checkInnerAirEntry(server);
    }

    private void checkInnerAirEntry(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());

            ServerLevel world = player.level();
            BlockPos feetPos = player.blockPosition();
            BlockPos headPos = feetPos.above();

            boolean isInsideInnerAir =
                ProtectionManager.INSTANCE.isInteriorProtected(world, feetPos)
                    || ProtectionManager.INSTANCE.isInteriorProtected(world, headPos);

            boolean wasInsideInnerAir = playersInsideInnerAir.contains(player.getUUID());
            if (isInsideInnerAir && !wasInsideInnerAir) {
                player.sendSystemMessage(INNER_AIR_ENTER_MESSAGE, true);
                playersInsideInnerAir.add(player.getUUID());
            } else if (!isInsideInnerAir && wasInsideInnerAir) {
                playersInsideInnerAir.remove(player.getUUID());
                // Clear the previous action-bar notification immediately when
                // the player leaves the protected interior (including after undo).
                player.sendSystemMessage(Component.empty(), true);
            }
        }

        playersInsideInnerAir.retainAll(online);
    }
}
