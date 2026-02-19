package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Unique
    private static final int SOULBOUNDITEMS_ENFORCE_INTERVAL_TICKS = 4;

    @Unique
    private int[] soulbounditems$previousSoulboundCounts;

    @Unique
    private int soulbounditems$enforceTickCountdown;

    @Unique
    private boolean soulbounditems$overflowActive;

    @Inject(method = "tick", at = @At("TAIL"))
    private void soulbounditems$enforceSoulboundCapacityByDroppingOverflow(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!soulbounditems$overflowActive) {
            soulbounditems$enforceTickCountdown++;
            if (soulbounditems$enforceTickCountdown < SOULBOUNDITEMS_ENFORCE_INTERVAL_TICKS) {
                return;
            }
            soulbounditems$enforceTickCountdown = 0;
        }

        int[] previous = soulbounditems$previousSoulboundCounts;
        if (previous == null) {
            previous = new int[player.getInventory().size()];
        }

        SoulboundRules.EnforcementResult result = SoulboundRules.enforceOverflowByDroppingNewest(player, previous);
        soulbounditems$previousSoulboundCounts = result.updatedCounts();
        soulbounditems$overflowActive = result.stillOverflowing();
    }
}
