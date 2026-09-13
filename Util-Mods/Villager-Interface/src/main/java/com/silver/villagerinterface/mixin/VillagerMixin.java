package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.villager.CustomVillagerData;
import com.silver.villagerinterface.villager.CustomVillagerManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerMixin implements CustomVillagerData {
    @Unique
    private String villagerinterface$customId;

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        CustomVillagerManager manager = VillagerInterfaceMod.getVillagerManager();
        if (!(player instanceof ServerPlayer serverPlayer) || manager == null) {
            return;
        }

        Villager villager = (Villager) (Object) this;
        if (!manager.isCustomVillager(villager)) {
            return;
        }

        boolean started = VillagerInterfaceMod.getConversationManager().startConversation(serverPlayer, villager);
        if (started) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void villagerinterface$lockMovement(CallbackInfo ci) {
        if (villagerinterface$customId == null) {
            return;
        }

        Villager villager = (Villager) (Object) this;
        if (villager.level().isClientSide()) {
            return;
        }

        villager.setNoAi(true);
        villager.setDeltaMovement(Vec3.ZERO);

        if (villager.isPassenger()) {
            villager.stopRiding();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void villagerinterface$writeCustomData(ValueOutput view, CallbackInfo ci) {
        if (villagerinterface$customId != null) {
            view.putString(VillagerInterfaceMod.CUSTOM_VILLAGER_ID_KEY, villagerinterface$customId);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void villagerinterface$readCustomData(ValueInput view, CallbackInfo ci) {
        villagerinterface$customId = view.getString(VillagerInterfaceMod.CUSTOM_VILLAGER_ID_KEY).orElse(null);
    }

    @Override
    public String villagerinterface$getCustomId() {
        return villagerinterface$customId;
    }

    @Override
    public void villagerinterface$setCustomId(String id) {
        this.villagerinterface$customId = id;
    }
}
