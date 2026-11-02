package com.silver.arsenal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** What a weapon does when it is used (right-click). */
interface Action {
    /** Returns true when the weapon actually fired, so ammo is spent and the cooldown starts. */
    boolean use(Ctx ctx);

    /** Everything an action may need about this use. */
    record Ctx(ServerLevel level, ServerPlayer player, ItemStack stack, Spec spec, Params p, boolean tempered) {
        /** A damage number from the params, with the tempering bonus applied. */
        double dmg(String key, double fallback) {
            return p.num(key, fallback) * (tempered ? Tuning.temperedDamage : 1.0);
        }
    }
}
