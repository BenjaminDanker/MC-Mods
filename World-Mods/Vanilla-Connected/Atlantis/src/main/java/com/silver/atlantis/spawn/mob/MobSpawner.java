package com.silver.atlantis.spawn.mob;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.drop.SpawnSpecialConfig;
import com.silver.atlantis.spawn.drop.SpecialDropManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * Handles spawning and customizing mobs using direct entity manipulation
 * instead of NBT parsing for maximum reliability.
 */
public final class MobSpawner {

    private MobSpawner() {}

    public static Optional<Entity> createConfiguredEntity(ServerLevel world, MobCustomization customization) {
        // Parse entity type
        Identifier entityId = parseIdentifier(customization.entityId());
        if (entityId == null) {
            AtlantisMod.LOGGER.error("Invalid entity id: {}", customization.entityId());
            return Optional.empty();
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
        if (entityType == null) {
            AtlantisMod.LOGGER.error("Unknown entity type: {}", entityId);
            return Optional.empty();
        }

        try {
            // Create entity using the proper spawn method
            Entity entity = entityType.create(world, EntitySpawnReason.COMMAND);
            if (entity == null) {
                AtlantisMod.LOGGER.error("Failed to create entity of type: {}", entityId);
                return Optional.empty();
            }

            // Position and rotation
            entity.snapTo(
                customization.x(), 
                customization.y(), 
                customization.z(),
                customization.yaw(),
                customization.pitch()
            );

            // Apply customizations if it's a living entity
            if (entity instanceof LivingEntity living) {
                applyLivingCustomizations(world, living, customization);
            }

            // Apply mob-specific customizations
            if (entity instanceof Mob mob) {
                applyMobCustomizations(world, mob, customization);

                // Ensure structure-spawned mobs never despawn naturally.
                mob.setPersistenceRequired();
            }

            entity.addTag(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG);

            if (customization.specialDropAmount() > 0) {
                SpecialDropManager.markSpecialDropAmount(entity, customization.specialDropAmount());
            }

            // Custom name
            if (customization.customName() != null && !customization.customName().isEmpty()) {
                entity.setCustomName(Component.literal(customization.customName()));
                entity.setCustomNameVisible(true);
            }

            // Glowing
            if (customization.glowing()) {
                entity.setGlowingTag(true);
            }
            return Optional.of(entity);

        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Exception creating configured entity {}: {}", entityId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static void applyLivingCustomizations(ServerLevel world, LivingEntity entity, MobCustomization customization) {
        // Apply max health first (before setting health)
        if (customization.maxHealth() > 0) {
            AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(customization.maxHealth());
            }
        }

        // Apply other attributes
        for (var entry : customization.attributes().entrySet()) {
            applyAttribute(entity, entry.getKey(), entry.getValue());
        }

        // Apply health after max health is set
        if (customization.health() > 0) {
            entity.setHealth((float) customization.health());
        } else if (customization.maxHealth() > 0) {
            // If max health was set but not current health, heal to full
            entity.setHealth((float) customization.maxHealth());
        }

        // Apply status effects
        for (MobCustomization.EffectData effectData : customization.effects()) {
            applyStatusEffect(world, entity, effectData);
        }
    }

    private static void applyMobCustomizations(ServerLevel world, Mob mob, MobCustomization customization) {
        // Apply equipment
        for (var entry : customization.equipment().entrySet()) {
            EquipmentSlot slot = parseEquipmentSlot(entry.getKey());
            if (slot != null) {
                MobCustomization.EquipmentEntry equipEntry = entry.getValue();
                ItemStack stack = equipEntry.stack().copy();
                
                // Apply enchantments
                for (MobCustomization.EnchantmentEntry enchEntry : equipEntry.enchantments()) {
                    applyEnchantment(world, stack, enchEntry.enchantmentId(), enchEntry.level());
                }
                
                mob.setItemSlot(slot, stack);
                // Prevent the mob from dropping this equipment naturally
                mob.setDropChance(slot, 0.0f);
            }
        }

        // Baby status for zombies and similar
        if (customization.isBaby() && mob instanceof Zombie zombie) {
            zombie.setBaby(true);
        }

        if (mob instanceof Creeper creeper) {
            applyCreeperCustomizations(creeper, customization);
        }
    }

    private static void applyCreeperCustomizations(Creeper creeper, MobCustomization customization) {
        if (creeper == null || customization == null) {
            return;
        }

        if (customization.creeperPowered()) {
            setCreeperChargedReflective(creeper, true);
        }

        int explosionRadius = customization.creeperExplosionRadius();
        if (explosionRadius >= 3) {
            setCreeperExplosionRadiusReflective(creeper, explosionRadius);
        }
    }

    private static void setCreeperExplosionRadiusReflective(Creeper creeper, int radius) {
        if (creeper == null) {
            return;
        }

        int clamped = Math.max(3, radius);
        String[] fieldNames = new String[] {"explosionRadius", "field_7225"};
        for (String fieldName : fieldNames) {
            try {
                Field field = Creeper.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setInt(creeper, clamped);
                return;
            } catch (Exception ignored) {
            }
        }

        AtlantisMod.LOGGER.warn("Unable to set creeper explosion radius via reflection (radius={})", clamped);
    }

    private static void setCreeperChargedReflective(Creeper creeper, boolean charged) {
        if (creeper == null) {
            return;
        }

        String[] methodNames = new String[] {"setCharged", "method_7502"};
        for (String methodName : methodNames) {
            try {
                Method method = Creeper.class.getDeclaredMethod(methodName, boolean.class);
                method.setAccessible(true);
                method.invoke(creeper, charged);
                return;
            } catch (Exception ignored) {
            }
        }

        // Final fallback: write tracked charged field directly if available.
        String[] fieldNames = new String[] {"CHARGED", "field_7224"};
        for (String fieldName : fieldNames) {
            try {
                Field field = Creeper.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object trackedData = field.get(null);
                if (trackedData != null && trySetTrackedBoolean(creeper, trackedData, charged)) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        AtlantisMod.LOGGER.warn("Unable to set creeper charged state via reflection (charged={})", charged);
    }

    private static boolean trySetTrackedBoolean(Creeper creeper, Object trackedData, boolean value) {
        try {
            Method getMethod = creeper.getEntityData().getClass().getMethod(
                "get",
                net.minecraft.network.syncher.EntityDataAccessor.class
            );
            Object current = getMethod.invoke(creeper.getEntityData(), trackedData);
            if (current != null && !(current instanceof Boolean)) {
                return false;
            }

            Method setMethod = creeper.getEntityData().getClass().getMethod(
                "set",
                net.minecraft.network.syncher.EntityDataAccessor.class,
                Object.class
            );
            setMethod.invoke(creeper.getEntityData(), trackedData, Boolean.valueOf(value));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void applyAttribute(LivingEntity entity, String attributeId, double value) {
        Identifier id = parseIdentifier(attributeId);
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid attribute id: {}", attributeId);
            return;
        }

        // Find the attribute in registry
        Holder<Attribute> attributeEntry = BuiltInRegistries.ATTRIBUTE.get(id).orElse(null);
        if (attributeEntry == null) {
            AtlantisMod.LOGGER.warn("Unknown attribute: {}", attributeId);
            return;
        }

        AttributeInstance instance = entity.getAttribute(attributeEntry);
        if (instance != null) {
            instance.setBaseValue(value);
            AtlantisMod.LOGGER.debug("Set attribute {} to {} on entity", attributeId, value);
        } else {
            AtlantisMod.LOGGER.warn("Entity does not have attribute: {}", attributeId);
        }
    }

    private static void applyStatusEffect(ServerLevel world, LivingEntity entity, MobCustomization.EffectData effectData) {
        Identifier id = parseIdentifier(effectData.effectId());
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid effect id: {}", effectData.effectId());
            return;
        }

        Holder<MobEffect> effectEntry = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
        if (effectEntry == null) {
            AtlantisMod.LOGGER.warn("Unknown status effect: {}", effectData.effectId());
            return;
        }

        MobEffectInstance instance = new MobEffectInstance(
            effectEntry,
            effectData.duration(),
            effectData.amplifier(),
            effectData.ambient(),
            effectData.showParticles()
        );
        entity.addEffect(instance);
        AtlantisMod.LOGGER.debug("Applied effect {} to entity", effectData.effectId());
    }
    
    private static void applyEnchantment(ServerLevel world, ItemStack stack, String enchantmentId, int level) {
        Identifier id = parseIdentifier(enchantmentId);
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid enchantment id: {}", enchantmentId);
            return;
        }
        
        var enchantmentRegistry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var enchantmentEntry = enchantmentRegistry.get(id);
        
        if (enchantmentEntry.isEmpty()) {
            AtlantisMod.LOGGER.warn("Unknown enchantment: {}", enchantmentId);
            return;
        }
        
        stack.enchant(enchantmentEntry.get(), level);
        AtlantisMod.LOGGER.debug("Applied enchantment {} level {} to item", enchantmentId, level);
    }

    private static EquipmentSlot parseEquipmentSlot(String slotName) {
        return switch (slotName.toLowerCase()) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "mainhand", "weapon" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> {
                AtlantisMod.LOGGER.warn("Unknown equipment slot: {}", slotName);
                yield null;
            }
        };
    }

    private static Identifier parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.contains(":") ? raw : "minecraft:" + raw;
        return Identifier.tryParse(normalized);
    }
}
