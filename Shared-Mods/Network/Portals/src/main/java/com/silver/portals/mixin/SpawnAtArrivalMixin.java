package com.silver.portals.mixin;

import com.silver.portals.PortalsHooks;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player arriving through a portal is created at the arrival point: their saved position, world and facing are
 * replaced as the server reads them, so it spawns them there instead of moving them after they joined.
 */
@Mixin(PlayerList.class)
public abstract class SpawnAtArrivalMixin {
    @Inject(method = "loadPlayerData", at = @At("RETURN"), cancellable = true)
    private void portals$placeAtArrival(NameAndId player, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        Optional<CompoundTag> saved = cir.getReturnValue();
        if (saved == null || saved.isEmpty()) {
            return; // first visit: the move after joining puts them there
        }
        CompoundTag placed = PortalsHooks.placeAtArrival(((PlayerList) (Object) this).getServer(), player.id(), saved.get());
        if (placed != null) {
            cir.setReturnValue(Optional.of(placed));
        }
    }
}
