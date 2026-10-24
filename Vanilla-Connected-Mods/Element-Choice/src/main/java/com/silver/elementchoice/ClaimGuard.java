package com.silver.elementchoice;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Open Parties and Claims lookups, in their own class so the mod still loads on a server without it. */
final class ClaimGuard {
    private ClaimGuard() {
    }

    /** Whether this player may not bend at this spot. */
    static boolean forbidden(MinecraftServer server, UUID player, Identifier dimension, BlockPos pos) {
        var claim = OpenPACServerAPI.get(server).getServerClaimsManager().get(dimension, pos);
        return ClaimRules.forbids(claim == null ? null : claim.getPlayerId(), player);
    }

    static boolean forbidden(ServerPlayer player) {
        return forbidden(player.level().getServer(), player.getUUID(), player.level().dimension().identifier(), player.blockPosition());
    }
}
