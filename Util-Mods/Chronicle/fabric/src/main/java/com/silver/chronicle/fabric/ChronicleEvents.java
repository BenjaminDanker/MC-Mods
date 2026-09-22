package com.silver.chronicle.fabric;

import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.common.PrivacyMode;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;

public final class ChronicleEvents {
    private static final String GIANT_TAG = "sky_islands_managed_giant";
    private static final String LEVIATHAN_TAG = "atlantis_managed_leviathan";
    private static final String DRAGON_TAG = "sky_islands_managed_dragon";
    private static final String SPECIAL_ARROW_ID = "special_arrow";

    private ChronicleEvents() { }

    static void register(ChronicleRuntime runtime) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "giant")
                    .equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                    && entity.entityTags().contains(GIANT_TAG)) reportDeath(entity, source, ChronicleEvent.FIRST_DEFEAT_SKY_GIANT);
            if (entity.entityTags().contains(LEVIATHAN_TAG)) reportDeath(entity, source, ChronicleEvent.FIRST_DEFEAT_OCEAN_LEVIATHAN);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(runtime::stop);
    }

    static void reportDeath(LivingEntity victim, DamageSource source, ChronicleEvent event) {
        ServerPlayer player = resolvePlayer(victim, source);
        if (player != null) ChronicleMod.complete(player, event.id());
    }

    public static ServerPlayer resolvePlayer(LivingEntity victim, DamageSource source) {
        Entity attacker = source == null ? null : source.getEntity();
        if (attacker instanceof ServerPlayer player) return player;
        if (attacker instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        LivingEntity credit = victim.getKillCredit();
        return credit instanceof ServerPlayer player ? player : null;
    }

    public static boolean hasSpecialArrow(CustomData data) {
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        return SPECIAL_ARROW_ID.equals(tag.getStringOr("id", ""));
    }
}
