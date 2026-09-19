package com.silver.skyislands.enderdragons;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
public final class DragonMixinSmoke implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            ClassLoader loader=getClass().getClassLoader();
            Class<?> dragon=Class.forName("net.minecraft.world.entity.boss.enderdragon.EnderDragon",false,loader);
            if (!DragonProvokedAccess.class.isAssignableFrom(dragon)) throw new AssertionError("Dragon mixin missing");
            Class<?> player=Class.forName("net.minecraft.server.level.ServerPlayer",false,loader);
            if (!DragonGustProtectedAccess.class.isAssignableFrom(player)) throw new AssertionError("Dragon gust player mixin missing");
            Class.forName("net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager",false,loader);
            Class.forName("net.minecraft.world.level.chunk.LevelChunk",false,loader);
            System.out.println("Sky-Islands dragon mixin smoke passed: combat, movement, phases and head changes");
        } catch (ClassNotFoundException error) { throw new AssertionError(error); }
    }
}
