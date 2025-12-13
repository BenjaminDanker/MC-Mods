package com.silver.atlantis.spawn;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses command block NBT data and converts it into MobCustomization objects.
 * 
 * Expected format in command block:
 * {Structure_Spawning:{id:"zombie",Health:100,equipment:{mainhand:{id:"diamond_sword",enchantments:{sharpness:5}}}}}
 * 
 * Or simplified format:
 * {id:"zombie",Health:100}
 */
public final class CommandBlockParser {

    private CommandBlockParser() {}

    /**
     * Parses NBT data into a MobCustomization.
     * 
     * @param nbt The NBT compound (either root or Structure_Spawning inner compound)
     * @param fallbackPos Fallback position if none specified
     * @return The parsed customization, or empty if parsing fails
     */
    public static Optional<MobCustomization> parse(NbtCompound nbt, BlockPos fallbackPos) {
        // Check for Structure_Spawning wrapper
        NbtCompound spec = nbt;
        if (nbt.contains("Structure_Spawning")) {
            NbtElement element = nbt.get("Structure_Spawning");
            if (element instanceof NbtCompound compound) {
                spec = compound;
            }
        }

        // Must have an entity id
        String entityId = readString(spec, "id").orElse(null);
        if (entityId == null || entityId.isBlank()) {
            AtlantisMod.LOGGER.warn("Missing 'id' in spawn specification");
            return Optional.empty();
        }

        MobCustomization.Builder builder = MobCustomization.builder(entityId);

        // Position
        Vec3d pos = readPosition(spec, fallbackPos);
        builder.position(pos.x, pos.y, pos.z);

        // Rotation
        float yaw = (float) readDouble(spec, "Yaw", 0);
        float pitch = (float) readDouble(spec, "Pitch", 0);
        if (spec.contains("Rotation") && spec.get("Rotation") instanceof NbtList rotList && rotList.size() >= 2) {
            yaw = toFloat(rotList.get(0));
            pitch = toFloat(rotList.get(1));
        }
        builder.rotation(yaw, pitch);

        // Health
        double health = readDouble(spec, "Health", -1);
        if (health > 0) {
            builder.health(health);
            builder.maxHealth(health); // Also set max health to match
        }

        // Explicit max health
        double maxHealth = readDouble(spec, "MaxHealth", -1);
        if (maxHealth > 0) {
            builder.maxHealth(maxHealth);
        }

        // Attributes
        if (spec.contains("Attributes") && spec.get("Attributes") instanceof NbtList attrList) {
            parseAttributes(builder, attrList);
        }

        // Equipment
        if (spec.contains("equipment") && spec.get("equipment") instanceof NbtCompound equipNbt) {
            parseEquipment(builder, equipNbt);
        }
        // Also support HandItems format
        if (spec.contains("HandItems") && spec.get("HandItems") instanceof NbtList handList) {
            parseHandItems(builder, handList);
        }
        // ArmorItems format
        if (spec.contains("ArmorItems") && spec.get("ArmorItems") instanceof NbtList armorList) {
            parseArmorItems(builder, armorList);
        }

        // Status effects
        if (spec.contains("effects") && spec.get("effects") instanceof NbtList effectList) {
            parseEffects(builder, effectList);
        }
        // Also support ActiveEffects format
        if (spec.contains("ActiveEffects") && spec.get("ActiveEffects") instanceof NbtList effectList) {
            parseActiveEffects(builder, effectList);
        }

        // Baby
        if (spec.contains("IsBaby")) {
            builder.baby(spec.getBoolean("IsBaby", false));
        }

        // Custom name
        readString(spec, "CustomName").ifPresent(builder::customName);

        // Glowing
        if (spec.contains("Glowing")) {
            builder.glowing(spec.getBoolean("Glowing", false));
        }

        return Optional.of(builder.build());
    }

    private static Vec3d readPosition(NbtCompound spec, BlockPos fallback) {
        // Check for Pos array
        if (spec.contains("Pos") && spec.get("Pos") instanceof NbtList posList && posList.size() == 3) {
            double x = toDouble(posList.get(0));
            double y = toDouble(posList.get(1));
            double z = toDouble(posList.get(2));
            return new Vec3d(x, y, z);
        }

        // Check for SpawnPos array
        if (spec.contains("SpawnPos") && spec.get("SpawnPos") instanceof NbtList posList && posList.size() == 3) {
            double x = toDouble(posList.get(0));
            double y = toDouble(posList.get(1));
            double z = toDouble(posList.get(2));
            return new Vec3d(x, y, z);
        }

        // Check for individual x, y, z
        if (spec.contains("x") && spec.contains("y") && spec.contains("z")) {
            return new Vec3d(
                readDouble(spec, "x", fallback.getX() + 0.5),
                readDouble(spec, "y", fallback.getY() + 1),
                readDouble(spec, "z", fallback.getZ() + 0.5)
            );
        }

        // Use fallback (center of block above)
        return Vec3d.ofCenter(fallback).add(0, 0.5, 0);
    }

    private static void parseAttributes(MobCustomization.Builder builder, NbtList attrList) {
        for (int i = 0; i < attrList.size(); i++) {
            if (attrList.get(i) instanceof NbtCompound attrEntry) {
                String name = readString(attrEntry, "Name").orElse(null);
                double base = readDouble(attrEntry, "Base", -1);
                if (name != null && base >= 0) {
                    builder.attribute(name, base);
                }
            }
        }
    }

    private static void parseEquipment(MobCustomization.Builder builder, NbtCompound equipNbt) {
        for (String slot : equipNbt.getKeys()) {
            NbtElement element = equipNbt.get(slot);
            if (element instanceof NbtCompound itemNbt) {
                ParsedItem parsed = parseItemStack(itemNbt);
                if (!parsed.stack.isEmpty()) {
                    builder.equipment(slot, parsed.stack, parsed.enchantments);
                }
            }
        }
    }

    private static void parseHandItems(MobCustomization.Builder builder, NbtList handList) {
        // HandItems: [{...mainhand...}, {...offhand...}]
        if (handList.size() > 0 && handList.get(0) instanceof NbtCompound mainHand) {
            ParsedItem parsed = parseItemStack(mainHand);
            if (!parsed.stack.isEmpty()) {
                builder.equipment("mainhand", parsed.stack, parsed.enchantments);
            }
        }
        if (handList.size() > 1 && handList.get(1) instanceof NbtCompound offHand) {
            ParsedItem parsed = parseItemStack(offHand);
            if (!parsed.stack.isEmpty()) {
                builder.equipment("offhand", parsed.stack, parsed.enchantments);
            }
        }
    }

    private static void parseArmorItems(MobCustomization.Builder builder, NbtList armorList) {
        // ArmorItems: [{...feet...}, {...legs...}, {...chest...}, {...head...}]
        String[] slots = {"feet", "legs", "chest", "head"};
        for (int i = 0; i < Math.min(armorList.size(), slots.length); i++) {
            if (armorList.get(i) instanceof NbtCompound armorNbt) {
                ParsedItem parsed = parseItemStack(armorNbt);
                if (!parsed.stack.isEmpty()) {
                    builder.equipment(slots[i], parsed.stack, parsed.enchantments);
                }
            }
        }
    }

    private record ParsedItem(ItemStack stack, List<MobCustomization.EnchantmentEntry> enchantments) {}

    private static ParsedItem parseItemStack(NbtCompound itemNbt) {
        String itemId = readString(itemNbt, "id").orElse(null);
        if (itemId == null || itemId.isBlank()) {
            return new ParsedItem(ItemStack.EMPTY, List.of());
        }

        Identifier id = parseIdentifier(itemId);
        if (id == null) {
            return new ParsedItem(ItemStack.EMPTY, List.of());
        }

        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            AtlantisMod.LOGGER.warn("Unknown item: {}", itemId);
            return new ParsedItem(ItemStack.EMPTY, List.of());
        }

        int count = (int) readDouble(itemNbt, "Count", 1);
        ItemStack stack = new ItemStack(item, count);
        List<MobCustomization.EnchantmentEntry> enchantments = new ArrayList<>();

        // Parse enchantments - simple format: {sharpness: 5, fire_aspect: 2}
        if (itemNbt.contains("enchantments") && itemNbt.get("enchantments") instanceof NbtCompound enchNbt) {
            for (String enchName : enchNbt.getKeys()) {
                int level = (int) readDouble(enchNbt, enchName, 1);
                enchantments.add(new MobCustomization.EnchantmentEntry(enchName, level));
            }
        }

        // Vanilla Enchantments format: [{id:"sharpness",lvl:5}]
        if (itemNbt.contains("Enchantments") && itemNbt.get("Enchantments") instanceof NbtList enchList) {
            for (int i = 0; i < enchList.size(); i++) {
                if (enchList.get(i) instanceof NbtCompound enchEntry) {
                    String enchId = readString(enchEntry, "id").orElse(null);
                    int level = (int) readDouble(enchEntry, "lvl", 1);
                    if (enchId != null) {
                        enchantments.add(new MobCustomization.EnchantmentEntry(enchId, level));
                    }
                }
            }
        }

        return new ParsedItem(stack, enchantments);
    }

    private static void parseEffects(MobCustomization.Builder builder, NbtList effectList) {
        for (int i = 0; i < effectList.size(); i++) {
            if (effectList.get(i) instanceof NbtCompound effectNbt) {
                String effectId = readString(effectNbt, "id").orElse(null);
                if (effectId != null) {
                    int duration = (int) readDouble(effectNbt, "duration", 600);
                    int amplifier = (int) readDouble(effectNbt, "amplifier", 0);
                    boolean ambient = effectNbt.getBoolean("ambient", false);
                    boolean showParticles = effectNbt.getBoolean("show_particles", true);
                    builder.effect(effectId, duration, amplifier, ambient, showParticles);
                }
            }
        }
    }

    private static void parseActiveEffects(MobCustomization.Builder builder, NbtList effectList) {
        // Vanilla format: ActiveEffects:[{id:"speed",Amplifier:0b,Duration:100,...}]
        for (int i = 0; i < effectList.size(); i++) {
            if (effectList.get(i) instanceof NbtCompound effectNbt) {
                String effectId = readString(effectNbt, "id").orElse(null);
                if (effectId != null) {
                    int duration = (int) readDouble(effectNbt, "Duration", 600);
                    int amplifier = (int) readDouble(effectNbt, "Amplifier", 0);
                    boolean ambient = effectNbt.getBoolean("Ambient", false);
                    boolean showParticles = effectNbt.getBoolean("ShowParticles", true);
                    builder.effect(effectId, duration, amplifier, ambient, showParticles);
                }
            }
        }
    }

    private static Optional<String> readString(NbtCompound source, String key) {
        // In 1.21.10, getString returns Optional<String>
        return source.getString(key);
    }

    private static double readDouble(NbtCompound source, String key, double defaultValue) {
        if (!source.contains(key)) {
            return defaultValue;
        }
        NbtElement element = source.get(key);
        if (element instanceof AbstractNbtNumber num) {
            return num.doubleValue();
        }
        return defaultValue;
    }

    private static double toDouble(NbtElement element) {
        if (element instanceof AbstractNbtNumber num) {
            return num.doubleValue();
        }
        return 0;
    }

    private static float toFloat(NbtElement element) {
        if (element instanceof AbstractNbtNumber num) {
            return num.floatValue();
        }
        return 0;
    }

    private static Identifier parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.contains(":") ? raw : "minecraft:" + raw;
        return Identifier.tryParse(normalized);
    }
}
