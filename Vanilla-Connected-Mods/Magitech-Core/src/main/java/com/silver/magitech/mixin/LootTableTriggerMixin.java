package com.silver.magitech.mixin;

import com.silver.magitech.CityEvents;
import net.minecraft.advancements.triggers.LootTableTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla fires this exactly when a player opens a container whose loot is generated now, once per container. */
@Mixin(LootTableTrigger.class)
public class LootTableTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void magitech$creditRuinChest(ServerPlayer player, ResourceKey<LootTable> table, CallbackInfo ci) {
        CityEvents.onContainerLoot(player, table.identifier().toString());
    }
}
