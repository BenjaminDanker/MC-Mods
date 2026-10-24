package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundDeathRetention;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDeathLootMixin {
    @Unique
    private ItemStack[] soulbounditems$heldBack;

    @Inject(method = "dropEquipment", at = @At("HEAD"), require = 1)
    private void soulbounditems$holdBackCraftedSoulbound(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            this.soulbounditems$heldBack = SoulboundDeathRetention.holdBack(player);
        }
    }

    @Inject(method = "dropEquipment", at = @At("RETURN"), require = 1)
    private void soulbounditems$putBackCraftedSoulbound(ServerLevel level, CallbackInfo ci) {
        ItemStack[] held = this.soulbounditems$heldBack;
        this.soulbounditems$heldBack = null;
        if (held != null && (Object) this instanceof ServerPlayer player) {
            SoulboundDeathRetention.putBack(player, held);
        }
    }
}
