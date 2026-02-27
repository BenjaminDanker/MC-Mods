package com.silver.atlantis.spawn.mob;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.drop.SpawnSpecialConfig;
import com.silver.atlantis.spawn.drop.SpecialDropManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Handles spawning and customizing mobs using direct entity manipulation
 * instead of NBT parsing for maximum reliability.
 */
public final class MobSpawner {

    private MobSpawner() {}

    public static Optional<Entity> createConfiguredEntity(ServerWorld world, MobCustomization customization) {
        // Parse entity type
        Identifier entityId = parseIdentifier(customization.entityId());
        if (entityId == null) {
            AtlantisMod.LOGGER.error("Invalid entity id: {}", customization.entityId());
            return Optional.empty();
        }

        EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);
        if (entityType == null || (entityType == EntityType.PIG && !entityId.getPath().equals("pig"))) {
            // EntityType.PIG is returned as fallback for unknown IDs
            AtlantisMod.LOGGER.error("Unknown entity type: {}", entityId);
            return Optional.empty();
        }

        try {
            // Create entity using the proper spawn method
            Entity entity = entityType.create(world, SpawnReason.COMMAND);
            if (entity == null) {
                AtlantisMod.LOGGER.error("Failed to create entity of type: {}", entityId);
                return Optional.empty();
            }

            // Position and rotation
            entity.refreshPositionAndAngles(
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
            if (entity instanceof MobEntity mob) {
                applyMobCustomizations(world, mob, customization);

                // Ensure structure-spawned mobs never despawn naturally.
                mob.setPersistent();
            }

            entity.addCommandTag(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG);

            if (customization.specialDropAmount() > 0) {
                SpecialDropManager.markSpecialDropAmount(entity, customization.specialDropAmount());
            }

            // Custom name
            if (customization.customName() != null && !customization.customName().isEmpty()) {
                entity.setCustomName(Text.literal(customization.customName()));
                entity.setCustomNameVisible(true);
            }

            // Glowing
            if (customization.glowing()) {
                entity.setGlowing(true);
            }
            return Optional.of(entity);

        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Exception creating configured entity {}: {}", entityId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static void applyLivingCustomizations(ServerWorld world, LivingEntity entity, MobCustomization customization) {
        // Apply max health first (before setting health)
        if (customization.maxHealth() > 0) {
            EntityAttributeInstance maxHealthAttr = entity.getAttributeInstance(EntityAttributes.MAX_HEALTH);
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

    private static void applyMobCustomizations(ServerWorld world, MobEntity mob, MobCustomization customization) {
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
                
                mob.equipStack(slot, stack);
                // Prevent the mob from dropping this equipment naturally
                mob.setEquipmentDropChance(slot, 0.0f);
            }
        }

        // Baby status for zombies and similar
        if (customization.isBaby() && mob instanceof ZombieEntity zombie) {
            zombie.setBaby(true);
        }

        if (mob instanceof CreeperEntity creeper) {
            applyCreeperCustomizations(creeper, customization);
        }
    }

    private static void applyCreeperCustomizations(CreeperEntity creeper, MobCustomization customization) {
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

    private static void setCreeperExplosionRadiusReflective(CreeperEntity creeper, int radius) {
        if (creeper == null) {
            return;
        }

        int clamped = Math.max(3, radius);
        String[] fieldNames = new String[] {"explosionRadius", "field_7225"};
        for (String fieldName : fieldNames) {
            try {
                Field field = CreeperEntity.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setInt(creeper, clamped);
                return;
            } catch (Exception ignored) {
            }
        }

        AtlantisMod.LOGGER.warn("Unable to set creeper explosion radius via reflection (radius={})", clamped);
    }

    private static void setCreeperChargedReflective(CreeperEntity creeper, boolean charged) {
        if (creeper == null) {
            return;
        }

        String[] methodNames = new String[] {"setCharged", "method_7502"};
        for (String methodName : methodNames) {
            try {
                Method method = CreeperEntity.class.getDeclaredMethod(methodName, boolean.class);
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
                Field field = CreeperEntity.class.getDeclaredField(fieldName);
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

    private static boolean trySetTrackedBoolean(CreeperEntity creeper, Object trackedData, boolean value) {
        try {
            Method getMethod = creeper.getDataTracker().getClass().getMethod(
                "get",
                net.minecraft.entity.data.TrackedData.class
            );
            Object current = getMethod.invoke(creeper.getDataTracker(), trackedData);
            if (current != null && !(current instanceof Boolean)) {
                return false;
            }

            Method setMethod = creeper.getDataTracker().getClass().getMethod(
                "set",
                net.minecraft.entity.data.TrackedData.class,
                Object.class
            );
            setMethod.invoke(creeper.getDataTracker(), trackedData, Boolean.valueOf(value));
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
        RegistryEntry<EntityAttribute> attributeEntry = Registries.ATTRIBUTE.getEntry(id).orElse(null);
        if (attributeEntry == null) {
            AtlantisMod.LOGGER.warn("Unknown attribute: {}", attributeId);
            return;
        }

        EntityAttributeInstance instance = entity.getAttributeInstance(attributeEntry);
        if (instance != null) {
            instance.setBaseValue(value);
            AtlantisMod.LOGGER.debug("Set attribute {} to {} on entity", attributeId, value);
        } else {
            AtlantisMod.LOGGER.warn("Entity does not have attribute: {}", attributeId);
        }
    }

    private static void applyStatusEffect(ServerWorld world, LivingEntity entity, MobCustomization.EffectData effectData) {
        Identifier id = parseIdentifier(effectData.effectId());
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid effect id: {}", effectData.effectId());
            return;
        }

        RegistryEntry<StatusEffect> effectEntry = Registries.STATUS_EFFECT.getEntry(id).orElse(null);
        if (effectEntry == null) {
            AtlantisMod.LOGGER.warn("Unknown status effect: {}", effectData.effectId());
            return;
        }

        StatusEffectInstance instance = new StatusEffectInstance(
            effectEntry,
            effectData.duration(),
            effectData.amplifier(),
            effectData.ambient(),
            effectData.showParticles()
        );
        entity.addStatusEffect(instance);
        AtlantisMod.LOGGER.debug("Applied effect {} to entity", effectData.effectId());
    }
    
    private static void applyEnchantment(ServerWorld world, ItemStack stack, String enchantmentId, int level) {
        Identifier id = parseIdentifier(enchantmentId);
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid enchantment id: {}", enchantmentId);
            return;
        }
        
        var enchantmentRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var enchantmentEntry = enchantmentRegistry.getEntry(id);
        
        if (enchantmentEntry.isEmpty()) {
            AtlantisMod.LOGGER.warn("Unknown enchantment: {}", enchantmentId);
            return;
        }
        
        stack.addEnchantment(enchantmentEntry.get(), level);
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
