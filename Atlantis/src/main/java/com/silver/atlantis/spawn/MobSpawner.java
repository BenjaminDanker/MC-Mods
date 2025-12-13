package com.silver.atlantis.spawn;

import com.silver.atlantis.AtlantisMod;
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
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Handles spawning and customizing mobs using direct entity manipulation
 * instead of NBT parsing for maximum reliability.
 */
public final class MobSpawner {

    private MobSpawner() {}

    /**
     * Spawns a mob with the given customization options.
     * 
     * @param world The server world to spawn in
     * @param customization The mob customization data
     * @return The spawned entity, or empty if spawning failed
     */
    public static Optional<Entity> spawn(ServerWorld world, MobCustomization customization) {
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

            // Spawn the entity
            boolean spawned = world.spawnEntity(entity);
            if (!spawned) {
                AtlantisMod.LOGGER.error("World rejected spawn of entity: {}", entityId);
                return Optional.empty();
            }

            AtlantisMod.LOGGER.info("Successfully spawned {} at ({}, {}, {})", 
                entityId, customization.x(), customization.y(), customization.z());
            return Optional.of(entity);

        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Exception spawning entity {}: {}", entityId, e.getMessage(), e);
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
