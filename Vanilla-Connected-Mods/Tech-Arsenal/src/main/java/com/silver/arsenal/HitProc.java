package com.silver.arsenal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** What a melee weapon adds when its swing connects. */
interface HitProc {
    void onHit(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered);
}
