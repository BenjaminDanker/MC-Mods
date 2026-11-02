package com.silver.chronicle.fabric.mixin;

import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.fabric.ChronicleMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes successful awards, including repeatable cave advancement awards after datapack revocation. */
@Mixin(PlayerAdvancements.class)
abstract class AdvancementAwardMixin {
    @Shadow private ServerPlayer player;

    private static final Identifier PHARAO_ADVANCEMENT = Identifier.fromNamespaceAndPath("petroglyph-guide", "pharao_boss");
    private static final Identifier CAVE_LEGENDARY_ADVANCEMENT = Identifier.fromNamespaceAndPath("infinity_cave", "kill/legendary");

    @Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
    private void chronicle$recordRelevantAdvancement(AdvancementHolder advancement, String criterion,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Identifier id = advancement.id();
        if (PHARAO_ADVANCEMENT.equals(id)) ChronicleMod.complete(player, ChronicleEvent.FIRST_DEFEAT_DESERT_PHARAO.id());
        else if (CAVE_LEGENDARY_ADVANCEMENT.equals(id)) ChronicleMod.complete(player, ChronicleEvent.FIRST_DEFEAT_CAVE_LEGENDARY.id());
    }
}
