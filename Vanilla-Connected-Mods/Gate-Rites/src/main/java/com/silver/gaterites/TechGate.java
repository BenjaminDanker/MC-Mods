package com.silver.gaterites;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * The Tech side: a Gate Core, crafted from the best of every chapter, lights a gate framed in steel blocks. Vanilla clients
 * see a nether star textured by the Polymer resource pack.
 */
final class TechGate {
    private TechGate() {
    }

    static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("gaterites", "gate_core"));
        Item.Properties properties = new Item.Properties().setId(key).rarity(Rarity.EPIC)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).stacksTo(16);
        Registry.register(BuiltInRegistries.ITEM, key, new SimplePolymerItem(properties, Items.NETHER_STAR, true));
        PolymerResourcePackUtils.addModAssets("gate-rites");
    }
}
