package com.silver.branchreset;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Open Parties and Claims access, kept in its own class so the mod loads without OPAC. */
final class Claims {
    private Claims() {
    }

    /** Unclaims every chunk the player owns, in every dimension. Returns how many chunks were released. */
    static int removeAll(MinecraftServer server, UUID player) {
        var manager = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (!manager.hasPlayerInfo(player)) {
            return 0;
        }
        record Owned(Identifier dimension, ChunkPos chunk) {
        }
        List<Owned> owned = new ArrayList<>();
        manager.getPlayerInfo(player).getStream().forEach(entry ->
            entry.getValue().getStream().forEach(list ->
                list.getStream().forEach(chunk -> owned.add(new Owned(entry.getKey(), chunk)))));
        for (Owned claim : owned) {
            manager.unclaim(claim.dimension(), claim.chunk().x(), claim.chunk().z());
        }
        return owned.size();
    }
}
