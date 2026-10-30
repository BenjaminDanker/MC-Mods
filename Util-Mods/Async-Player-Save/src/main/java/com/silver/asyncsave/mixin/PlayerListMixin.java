package com.silver.asyncsave.mixin;

import com.silver.asyncsave.AsyncSaves;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stats and advancements are read when a player's counters are first created: their pending writes go first. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "getPlayerStats", at = @At("HEAD"))
    private void asyncsave$awaitStats(Player player, CallbackInfoReturnable<ServerStatsCounter> cir) {
        AsyncSaves.awaitPlayer(player.getUUID());
    }

    @Inject(method = "getPlayerAdvancements", at = @At("HEAD"))
    private void asyncsave$awaitAdvancements(ServerPlayer player, CallbackInfoReturnable<PlayerAdvancements> cir) {
        AsyncSaves.awaitPlayer(player.getUUID());
    }
}
