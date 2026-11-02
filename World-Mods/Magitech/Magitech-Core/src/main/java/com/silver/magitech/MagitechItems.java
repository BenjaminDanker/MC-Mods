package com.silver.magitech;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/** The resources of the loop. Vanilla clients see the base item, textured by the Polymer resource pack. */
public final class MagitechItems {
    public static final Item LEY_CRYSTAL = register("ley_crystal", Items.AMETHYST_SHARD, Rarity.UNCOMMON, true);
    public static final Item RAW_DEEP_ORE = register("raw_deep_ore", Items.RAW_IRON, Rarity.UNCOMMON, false);
    public static final Item DEEPSTEEL_INGOT = register("deepsteel_ingot", Items.NETHERITE_INGOT, Rarity.RARE, false);

    /** Armor and toughness a Deepsteel Charm gives while held in the off hand. */
    public static final double CHARM_ARMOR = 3.0;
    public static final double CHARM_TOUGHNESS = 2.0;
    /** Neutral gear: anyone may hold it, so a bender with no use for PolyFactory still has a use for Deepsteel. */
    public static final Item DEEPSTEEL_CHARM = registerCharm();

    private MagitechItems() {
    }

    static void init() {
        // Loading this class registers the items.
    }

    private static Item register(String name, Item base, Rarity rarity, boolean glint) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("magitech", name));
        Item.Properties properties = new Item.Properties().setId(key).rarity(rarity);
        if (glint) {
            properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return Registry.register(BuiltInRegistries.ITEM, key, new SimplePolymerItem(properties, base, true));
    }

    private static Item registerCharm() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("magitech", "deepsteel_charm"));
        Item.Properties properties = new Item.Properties().setId(key).rarity(Rarity.RARE).stacksTo(1)
            .attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(Identifier.fromNamespaceAndPath("magitech", "charm_armor"), CHARM_ARMOR,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Identifier.fromNamespaceAndPath("magitech", "charm_toughness"), CHARM_TOUGHNESS,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
                .build())
            .component(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal("Hold it in your off hand.").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC),
                Component.literal("+" + (int) CHARM_ARMOR + " armor, +" + (int) CHARM_TOUGHNESS + " armor toughness").withStyle(net.minecraft.ChatFormatting.BLUE))));
        return Registry.register(BuiltInRegistries.ITEM, key, new SimplePolymerItem(properties, Items.HEART_OF_THE_SEA, true));
    }
}
