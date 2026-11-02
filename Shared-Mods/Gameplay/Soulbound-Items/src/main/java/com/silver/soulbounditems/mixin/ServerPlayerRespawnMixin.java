package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundDeathRetention;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRespawnMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"), require = 1)
    private void soulbounditems$restoreCraftedSoulboundAfterRespawn(
        ServerPlayer previousPlayer,
        boolean alive,
        CallbackInfo ci
    ) {
        SoulboundDeathRetention.restore((ServerPlayer) (Object) this, previousPlayer);
    }
}
