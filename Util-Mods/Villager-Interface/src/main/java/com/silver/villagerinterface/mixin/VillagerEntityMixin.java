package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.villager.CustomVillagerData;
import com.silver.villagerinterface.villager.CustomVillagerManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin implements CustomVillagerData {
    @Unique
    private String villagerinterface$customId;

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        CustomVillagerManager manager = VillagerInterfaceMod.getVillagerManager();
        if (!(player instanceof ServerPlayerEntity serverPlayer) || manager == null) {
            return;
        }

        VillagerEntity villager = (VillagerEntity) (Object) this;
        if (!manager.isCustomVillager(villager)) {
            return;
        }

        boolean started = VillagerInterfaceMod.getConversationManager().startConversation(serverPlayer, villager);
        if (started) {
            cir.setReturnValue(ActionResult.SUCCESS);
            cir.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void villagerinterface$lockMovement(CallbackInfo ci) {
        if (villagerinterface$customId == null) {
            return;
        }

        VillagerEntity villager = (VillagerEntity) (Object) this;
        if (villager.getEntityWorld().isClient()) {
            return;
        }

        villager.setAiDisabled(true);
        villager.setVelocity(Vec3d.ZERO);

        if (villager.hasVehicle()) {
            villager.stopRiding();
        }
    }

    @Inject(method = "writeCustomData(Lnet/minecraft/storage/WriteView;)V", at = @At("HEAD"))
    private void villagerinterface$writeCustomData(WriteView view, CallbackInfo ci) {
        if (villagerinterface$customId != null) {
            view.putString(VillagerInterfaceMod.CUSTOM_VILLAGER_ID_KEY, villagerinterface$customId);
        }
    }

    @Inject(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V", at = @At("HEAD"))
    private void villagerinterface$readCustomData(ReadView view, CallbackInfo ci) {
        villagerinterface$customId = view.getOptionalString(VillagerInterfaceMod.CUSTOM_VILLAGER_ID_KEY).orElse(null);
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
