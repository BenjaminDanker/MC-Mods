package com.silver.arsenal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers every weapon, gadget and round of ammunition from the specs. */
final class Arms {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");

    private Arms() {
    }

    static List<Item> register(List<Spec> specs) {
        List<Item> items = new ArrayList<>();
        for (Spec spec : specs) {
            items.add(register(spec, false));
            if (spec.temper) {
                items.add(register(spec, true));
            }
        }
        return items;
    }

    private static Item register(Spec spec, boolean tempered) {
        String id = tempered ? spec.id + "_tempered" : spec.id;
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("arsenal", id));
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(spec.stack).rarity(rarity(spec, tempered));
        properties.component(DataComponents.LORE, new ItemLore(Lore.lines(spec, tempered)));
        if (tempered) {
            properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (spec.melee != null) {
            Params melee = new Params(spec.melee);
            double damage = melee.num("damage", 1) * (tempered ? Tuning.temperedDamage : 1.0);
            properties.attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, melee.num("speed", 1.6) - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        }
        Item base = BuiltInRegistries.ITEM.getValue(Identifier.parse(spec.base));
        Action action = spec.use == null ? null : Actions.create(new Params(spec.use).kind());
        if (spec.use != null && action == null) {
            LOGGER.error("ALERT: {} has an unknown use kind '{}'; it will do nothing when used", id, new Params(spec.use).kind());
        }
        HitProc hit = spec.hit == null ? null : HitProcs.create(new Params(spec.hit));
        if (spec.hit != null && hit == null) {
            LOGGER.error("ALERT: {} has an unknown hit kind '{}'; its swings will be plain", id, new Params(spec.hit).kind());
        }
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new ArsenalItem(properties, base, spec, tempered, action, hit));
        if (spec.cell != null && !tempered) {
            Ammo.registerCell(item, new Params(spec.cell).integer("charges", 12));
        }
        return item;
    }

    private static Rarity rarity(Spec spec, boolean tempered) {
        if (tempered) {
            return Rarity.EPIC;
        }
        return switch (spec.rarity) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
