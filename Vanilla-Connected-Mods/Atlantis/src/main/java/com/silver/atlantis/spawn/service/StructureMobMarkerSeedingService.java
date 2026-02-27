package com.silver.atlantis.spawn.service;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.config.SpawnDifficultyConfig;
import com.silver.atlantis.spawn.config.SpawnMobConfig;
import com.silver.atlantis.spawn.drop.SpawnSpecialConfig;
import com.silver.atlantis.spawn.drop.SpecialDropManager;
import com.silver.atlantis.spawn.marker.AtlantisMobMarker;
import com.silver.atlantis.spawn.marker.AtlantisMobMarkerState;
import com.silver.atlantis.spawn.mob.MobCustomization;
import com.silver.atlantis.spawn.mob.MobSpawner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

final class StructureMobMarkerSeedingService {

    record SeedResult(boolean seeded, boolean replaced, boolean specialTagged, int specialAmount, boolean skipped) {
    }

    private record BuiltCustomization(MobCustomization customization, int positionDifficulty) {
    }

    SeedResult seedMarker(ServerWorld world,
                          ActiveConstructBounds bounds,
                          Random random,
                          ProximitySpawnService.SpawnMarker marker) {
        if (world == null || bounds == null || random == null || marker == null) {
            return new SeedResult(false, false, false, 0, true);
        }

        Optional<BuiltCustomization> builtOpt = buildCustomization(marker, bounds, random);
        if (builtOpt.isEmpty()) {
            return new SeedResult(false, false, false, 0, true);
        }

        BuiltCustomization built = builtOpt.get();
        Optional<Entity> entityOpt = MobSpawner.createConfiguredEntity(world, built.customization());
        if (entityOpt.isEmpty() || !(entityOpt.get() instanceof MobEntity mob)) {
            return new SeedResult(false, false, false, 0, true);
        }

        int specialAmount = rollSpecialDropAmount(marker.spawnType(), built.positionDifficulty(), random);
        boolean specialTagged = false;
        if (specialAmount > 0) {
            SpecialDropManager.markSpecialDropAmount(mob, specialAmount);
            specialTagged = true;
        }

        AtlantisMobMarker markerData = AtlantisMobMarker.fromEntity(mob);
        if (markerData == null) {
            return new SeedResult(false, false, false, 0, true);
        }

        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);
        boolean replaced = state.getMarker(marker.pos()) != null;
        state.putMarker(marker.pos().toImmutable(), markerData);

        return new SeedResult(true, replaced, specialTagged, specialAmount, false);
    }

    private Optional<BuiltCustomization> buildCustomization(ProximitySpawnService.SpawnMarker marker, ActiveConstructBounds bounds, Random random) {
        double normalizedDifficulty = computeNormalizedDifficulty(marker.pos(), bounds);
        int maxDifficulty = SpawnMobConfig.maxDifficultyFor(marker.spawnType());
        int positionDifficulty = Math.max(1, (int) Math.round(normalizedDifficulty * maxDifficulty));

        SpawnMobConfig.MobLoadout loadout = SpawnMobConfig.buildLoadout(marker.spawnType(), positionDifficulty, random, false);
        if (loadout == null) {
            return Optional.empty();
        }

        MobCustomization.Builder builder = MobCustomization.builder(loadout.entityId());
        builder.position(marker.pos().getX() + 0.5, marker.pos().getY(), marker.pos().getZ() + 0.5);
        builder.rotation(random.nextFloat() * 360f, 0f);

        if (loadout.health() > 0) {
            builder.health(loadout.health());
            builder.maxHealth(loadout.health());
        }

        builder.creeperPowered(loadout.creeperPowered());
        builder.creeperExplosionRadius(loadout.creeperExplosionRadius());

        for (SpawnMobConfig.EquipmentConfig equip : loadout.equipment()) {
            ItemStack stack = createItemStack(equip);
            if (stack.isEmpty()) {
                continue;
            }

            List<MobCustomization.EnchantmentEntry> enchantments = new ArrayList<>();
            for (SpawnMobConfig.EnchantmentConfig ench : equip.enchantments()) {
                enchantments.add(new MobCustomization.EnchantmentEntry(ench.enchantmentId(), ench.level()));
            }
            builder.equipment(equip.slot(), stack, enchantments);
        }

        for (SpawnMobConfig.EffectConfig effect : loadout.effects()) {
            builder.effect(
                effect.effectId(),
                SpawnMobConfig.YEAR_TICKS,
                effect.amplifier(),
                effect.ambient(),
                effect.showParticles()
            );
        }

        return Optional.of(new BuiltCustomization(builder.build(), positionDifficulty));
    }

    private ItemStack createItemStack(SpawnMobConfig.EquipmentConfig equip) {
        Identifier id = Identifier.tryParse(equip.itemId());
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid equipment item id: {}", equip.itemId());
            return ItemStack.EMPTY;
        }

        Item item = Registries.ITEM.get(id);
        if (item == null || item == net.minecraft.item.Items.AIR) {
            AtlantisMod.LOGGER.warn("Unknown equipment item: {}", equip.itemId());
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private int rollSpecialDropAmount(SpawnMobConfig.SpawnType spawnType, int difficulty, Random random) {
        if (spawnType == null) {
            return 0;
        }

        int maxDifficulty = SpawnMobConfig.maxDifficultyFor(spawnType);
        double normalizedDifficulty = Math.max(0.0, Math.min(1.0, difficulty / (double) Math.max(1, maxDifficulty)));

        if (spawnType == SpawnMobConfig.SpawnType.BOSS) {
            int minAmount = Math.max(1, SpawnSpecialConfig.BOSS_SPECIAL_DROP_MIN);
            int maxAmount = Math.max(minAmount, Math.max(1, SpawnSpecialConfig.SPECIAL_DROP_MAX_AMOUNT_BOSS));
            int amount = (int) Math.round(minAmount + (normalizedDifficulty * (maxAmount - minAmount)));
            return Math.max(minAmount, Math.min(maxAmount, amount));
        }

        int maxAmount = Math.max(1, SpawnSpecialConfig.SPECIAL_DROP_MAX_AMOUNT_NON_BOSS);
        double variance = Math.max(0.0, SpawnSpecialConfig.SPECIAL_DROP_RANDOMNESS_PERCENT) / 100.0;

        double chance;
        if (difficulty < 10) {
            double progress = (difficulty - 1) / 9.0;
            chance = 0.15 + (0.65 * Math.max(0.0, Math.min(1.0, progress)));
            if (random.nextDouble() >= chance) {
                return 0;
            }
        }

        double curve = Math.pow(normalizedDifficulty, 1.3);
        double amountF = 1.0 + (curve * (maxAmount - 1));
        if (variance > 0.0) {
            double jitter = (random.nextDouble() * 2.0) - 1.0;
            amountF = amountF * (1.0 + (variance * jitter));
        }

        int amount = (int) Math.round(amountF);
        return Math.max(1, Math.min(maxAmount, amount));
    }

    private double computeNormalizedDifficulty(net.minecraft.util.math.BlockPos spawnPos, ActiveConstructBounds bounds) {
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;

        double rx = Math.max(1.0, (bounds.maxX() - bounds.minX()) / 2.0);
        double rz = Math.max(1.0, (bounds.maxZ() - bounds.minZ()) / 2.0);
        double dx = (spawnPos.getX() + 0.5) - centerX;
        double dz = (spawnPos.getZ() + 0.5) - centerZ;
        double normalized = Math.sqrt((dx * dx) / (rx * rx) + (dz * dz) / (rz * rz));
        double centerFactor = 1.0 - clamp01(normalized);
        centerFactor = clamp01(centerFactor);

        int easyY = resolveEasyY(bounds);
        double heightFactor;
        double depthFactor;

        if (spawnPos.getY() >= easyY) {
            int heightRange = Math.max(1, bounds.maxY() - easyY);
            heightFactor = (spawnPos.getY() - easyY) / (double) heightRange;
            depthFactor = 0.0;
        } else {
            int depthRange = Math.max(1, easyY - bounds.minY());
            depthFactor = (easyY - spawnPos.getY()) / (double) depthRange;
            heightFactor = 0.0;
        }

        heightFactor = clamp01(heightFactor);
        depthFactor = clamp01(depthFactor);

        double weighted = centerFactor * SpawnDifficultyConfig.CENTER_DIFFICULTY_WEIGHT
            + heightFactor * SpawnDifficultyConfig.HEIGHT_DIFFICULTY_WEIGHT
            + depthFactor * SpawnDifficultyConfig.DEEP_DIFFICULTY_WEIGHT;

        double totalWeight = SpawnDifficultyConfig.CENTER_DIFFICULTY_WEIGHT
            + SpawnDifficultyConfig.HEIGHT_DIFFICULTY_WEIGHT
            + SpawnDifficultyConfig.DEEP_DIFFICULTY_WEIGHT;

        if (totalWeight <= 0) {
            return 0.0;
        }

        return clamp01(weighted / totalWeight);
    }

    private int resolveEasyY(ActiveConstructBounds bounds) {
        int relativeEasy = SpawnDifficultyConfig.SCHEMATIC_EASIEST_Y_REL;
        int relativeMin = SpawnDifficultyConfig.SCHEMATIC_MIN_Y_REL;
        int relativeMax = SpawnDifficultyConfig.SCHEMATIC_MAX_Y_REL;

        int relativeClamped = Math.min(relativeMax, Math.max(relativeMin, relativeEasy));
        int offset = relativeClamped - relativeMin;
        int easyY = bounds.minY() + offset;
        if (easyY < bounds.minY()) {
            return bounds.minY();
        }
        if (easyY > bounds.maxY()) {
            return bounds.maxY();
        }
        return easyY;
    }

    private double clamp01(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
