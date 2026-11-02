package com.silver.chronicle.fabric.mixin;

import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.fabric.ChronicleEvents;
import com.silver.chronicle.fabric.ChronicleMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the actual projectile creation reached from ProjectileWeaponItem#shoot. */
@Mixin(Projectile.class)
abstract class ProjectileSpawnMixin {
    @Inject(method = "spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/world/entity/projectile/Projectile;", at = @At("RETURN"))
    private static void chronicle$recordSpecialArrowFire(Projectile projectile, ServerLevel level, ItemStack ammunition,
                                                         Consumer<Projectile> onSpawn,
                                                         CallbackInfoReturnable<Projectile> cir) {
        if (!net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "arrow")
                .equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()))) return;
        if (!ChronicleEvents.hasSpecialArrow(ammunition.get(DataComponents.CUSTOM_DATA))) return;
        if (level.getEntity(projectile.getId()) == projectile && projectile.getOwner() instanceof ServerPlayer player)
            ChronicleMod.complete(player, ChronicleEvent.FIRST_FIRE_SPECIAL_ARROW.id());
    }
}
