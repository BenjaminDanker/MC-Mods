package com.silver.gaterites;

import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * The old engineers' plans, scattered when their city broke: one set in the Overworld's towers, one in the Nether's
 * reactors and labs, one in the End's towers. The Tech story asks for each (custom data {@code techstory_plans}). Tech
 * role only, added to those chests' loot when datapacks load.
 */
final class EngineerPlans {
    private record Set(String id, String title, String line, float chance, List<String> tablePrefixes, List<String> excluded) {
    }

    private static final List<Set> SETS = List.of(
        new Set("overworld", "Engineer's Plans: The Workshop", "Drawn in a careful hand. The workshop was only the beginning.", 0.25F,
            List.of("structory_towers:basic/", "structory_towers:top/", "structory_towers:workshop"),
            List.of("structory_towers:basic/crimson", "structory_towers:basic/warped", "structory_towers:top/crimson", "structory_towers:top/warped")),
        new Set("nether", "Engineer's Plans: The Reactor", "Scorched at the edges. Whoever drew this worked close to the fire.", 0.35F,
            List.of("incendium:reactor/", "incendium:lab/", "structory_towers:basic/crimson", "structory_towers:basic/warped"), List.of()),
        new Set("end", "Engineer's Plans: The Vault", "Cold to the touch. The last page of the old city's design.", 0.5F,
            List.of("structory_towers:end_tower"), List.of()));

    private EngineerPlans() {
    }

    static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            String id = key.identifier().toString();
            for (Set set : SETS) {
                if (set.tablePrefixes().stream().anyMatch(id::startsWith) && set.excluded().stream().noneMatch(id::startsWith)) {
                    table.withPool(pool(set));
                }
            }
        });
    }

    private static LootPool.Builder pool(Set set) {
        CompoundTag data = new CompoundTag();
        data.putString("techstory_plans", set.id());
        return LootPool.lootPool()
            .when(LootItemRandomChanceCondition.randomChance(set.chance()))
            .add(LootItem.lootTableItem(Items.PAPER)
                .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(data)))
                .apply(SetComponentsFunction.setComponent(DataComponents.ITEM_NAME,
                    Component.literal(set.title()).withStyle(ChatFormatting.GOLD)))
                .apply(SetComponentsFunction.setComponent(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal(set.line()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))))));
    }
}
