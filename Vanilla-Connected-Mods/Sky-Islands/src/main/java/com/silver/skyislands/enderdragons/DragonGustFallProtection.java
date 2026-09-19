package com.silver.skyislands.enderdragons;

import net.minecraft.server.level.ServerPlayer;

public final class DragonGustFallProtection {
    public static final long DURATION_TICKS=40;
    public static final float FALL_DAMAGE_CAP=6;
    private DragonGustFallProtection() {}

    public static void grant(ServerPlayer player,long now) {
        ((DragonGustProtectedAccess)player).skyIslands$protectFromGustFallDamage(now+DURATION_TICKS);
    }

    public static float cap(long now,long protectedUntil,boolean fallDamage,float amount) {
        return fallDamage && now<protectedUntil ? Math.min(amount,FALL_DAMAGE_CAP) : amount;
    }
}
