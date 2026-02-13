package com.silver.atlantis.spawn;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data class holding customization options for spawning a mob.
 */
public record MobCustomization(
    String entityId,
    double x, double y, double z,
    float yaw, float pitch,
    double health,
    double maxHealth,
    Map<String, Double> attributes,
    Map<String, EquipmentEntry> equipment,
    List<EffectData> effects,
    int specialDropAmount,
    boolean creeperPowered,
    int creeperExplosionRadius,
    boolean isBaby,
    String customName,
    boolean glowing
) {
    public static Builder builder(String entityId) {
        return new Builder(entityId);
    }

    public record EffectData(String effectId, int duration, int amplifier, boolean ambient, boolean showParticles) {}
    
    public record EnchantmentEntry(String enchantmentId, int level) {}
    
    public record EquipmentEntry(ItemStack stack, List<EnchantmentEntry> enchantments) {
        public static EquipmentEntry of(ItemStack stack) {
            return new EquipmentEntry(stack, new ArrayList<>());
        }
        
        public static EquipmentEntry of(ItemStack stack, List<EnchantmentEntry> enchantments) {
            return new EquipmentEntry(stack, enchantments);
        }
    }

    public static class Builder {
        private final String entityId;
        private double x, y, z;
        private float yaw, pitch;
        private double health = -1;
        private double maxHealth = -1;
        private final Map<String, Double> attributes = new HashMap<>();
        private final Map<String, EquipmentEntry> equipment = new HashMap<>();
        private final List<EffectData> effects = new ArrayList<>();
        private int specialDropAmount = 0;
        private boolean creeperPowered = false;
        private int creeperExplosionRadius = 0;
        private boolean isBaby = false;
        private String customName = null;
        private boolean glowing = false;

        public Builder(String entityId) {
            this.entityId = entityId;
        }

        public Builder position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        public Builder health(double health) {
            this.health = health;
            return this;
        }

        public Builder maxHealth(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        public Builder attribute(String id, double value) {
            this.attributes.put(id, value);
            return this;
        }

        public Builder equipment(String slot, ItemStack stack) {
            this.equipment.put(slot, EquipmentEntry.of(stack));
            return this;
        }
        
        public Builder equipment(String slot, ItemStack stack, List<EnchantmentEntry> enchantments) {
            this.equipment.put(slot, EquipmentEntry.of(stack, enchantments));
            return this;
        }

        public Builder effect(String effectId, int duration, int amplifier, boolean ambient, boolean showParticles) {
            this.effects.add(new EffectData(effectId, duration, amplifier, ambient, showParticles));
            return this;
        }

        public Builder specialDropAmount(int specialDropAmount) {
            this.specialDropAmount = specialDropAmount;
            return this;
        }

        public Builder creeperPowered(boolean creeperPowered) {
            this.creeperPowered = creeperPowered;
            return this;
        }

        public Builder creeperExplosionRadius(int creeperExplosionRadius) {
            this.creeperExplosionRadius = Math.max(0, creeperExplosionRadius);
            return this;
        }

        public Builder baby(boolean isBaby) {
            this.isBaby = isBaby;
            return this;
        }

        public Builder customName(String name) {
            this.customName = name;
            return this;
        }

        public Builder glowing(boolean glowing) {
            this.glowing = glowing;
            return this;
        }

        public MobCustomization build() {
            return new MobCustomization(
                entityId, x, y, z, yaw, pitch,
                health, maxHealth, attributes, equipment, effects,
                specialDropAmount,
                creeperPowered,
                creeperExplosionRadius,
                isBaby, customName, glowing
            );
        }
    }
}
