package com.silver.disabledimensions.mixin;

import com.silver.disabledimensions.DisableDimensionsMod;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(
        method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disabledimensions$blockTeleportToDisabledDimension(
        ServerWorld targetWorld,
        double x,
        double y,
        double z,
        Set<PositionFlag> flags,
        float yaw,
        float pitch,
        boolean missingRespawnBlock,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        RegistryKey<World> targetDimension = targetWorld.getRegistryKey();

        if (DisableDimensionsMod.shouldBlockTeleportInto(player, targetDimension)) {
            DisableDimensionsMod.notifyBlockedTeleport(player);
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disabledimensions$blockTeleportTargetToDisabledDimension(
        TeleportTarget target,
        CallbackInfoReturnable<ServerPlayerEntity> cir
    ) {
        if (target == null || target.world() == null) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        RegistryKey<World> targetDimension = target.world().getRegistryKey();

        if (DisableDimensionsMod.shouldBlockTeleportInto(player, targetDimension)) {
            DisableDimensionsMod.notifyBlockedTeleport(player);
            cir.setReturnValue(player);
        }
    }
}
