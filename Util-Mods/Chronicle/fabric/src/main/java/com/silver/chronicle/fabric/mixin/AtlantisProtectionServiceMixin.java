package com.silver.chronicle.fabric.mixin;

import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.fabric.ChronicleMod;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Taps Atlantis' existing inside-transition set insertion; Chronicle adds no movement polling. */
@Pseudo
@Mixin(targets = "com.silver.atlantis.protect.ProtectionService", remap = false)
abstract class AtlantisProtectionServiceMixin {
    @Redirect(method = "checkInnerAirEntry", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", ordinal = 1))
    private boolean chronicle$observeInteriorEntry(Set<UUID> insideSet, Object entry,
                                                   MinecraftServer server) {
        boolean wasAdded = insideSet.add((UUID) entry);
        if (wasAdded) {
            ServerPlayer player = server.getPlayerList().getPlayer((UUID) entry);
            if (player != null) ChronicleMod.complete(player, ChronicleEvent.FIRST_DISCOVER_ATLANTIS.id());
        }
        return wasAdded;
    }
}
