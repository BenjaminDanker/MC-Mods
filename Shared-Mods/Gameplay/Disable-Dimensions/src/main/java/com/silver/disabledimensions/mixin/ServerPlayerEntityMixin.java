package com.silver.disabledimensions.mixin;

import com.silver.disabledimensions.DisableDimensionsMod;
import net.minecraft.world.entity.Relative;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disabledimensions$blockTeleportToDisabledDimension(
        ServerLevel targetWorld,
        double x,
        double y,
        double z,
        Set<Relative> flags,
        float yaw,
        float pitch,
        boolean missingRespawnBlock,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ResourceKey<Level> targetDimension = targetWorld.dimension();

        if (DisableDimensionsMod.shouldBlockTeleportInto(player, targetDimension)) {
            DisableDimensionsMod.notifyBlockedTeleport(player);
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disabledimensions$blockTeleportTargetToDisabledDimension(
        TeleportTransition target,
        CallbackInfoReturnable<ServerPlayer> cir
    ) {
        if (target == null || target.newLevel() == null) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        ResourceKey<Level> targetDimension = target.newLevel().dimension();

        if (DisableDimensionsMod.shouldBlockTeleportInto(player, targetDimension)) {
            DisableDimensionsMod.notifyBlockedTeleport(player);
            cir.setReturnValue(player);
        }
    }
}
