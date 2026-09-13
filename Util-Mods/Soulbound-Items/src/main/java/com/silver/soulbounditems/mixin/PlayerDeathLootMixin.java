package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundDeathRetention;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDeathLootMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), require = 1)
    private void soulbounditems$captureCraftedSoulboundBeforeDeathDrop(
        ServerLevel level,
        CallbackInfo ci
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            SoulboundDeathRetention.captureAndRemove(player);
        }
    }
}
