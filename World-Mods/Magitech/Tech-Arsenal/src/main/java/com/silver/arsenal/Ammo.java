package com.silver.arsenal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * What a use costs. Some weapons take an item per shot (rivets, shells); energy weapons keep a charge in their own stack and
 * refill it from a power cell when it runs out.
 */
final class Ammo {
    private static final String CHARGE = "charge";
    /** Every kind of cell and how much charge it gives, filled as the items are registered. */
    private static final Map<Item, Integer> CELLS = new LinkedHashMap<>();

    private Ammo() {
    }

    static void registerCell(Item item, int charges) {
        CELLS.put(item, charges);
    }

    /** The cell kinds a player holds, richest first, so a stronger cell is used before a plain one. */
    private static List<Item> heldCells(ServerPlayer player) {
        List<Item> held = new ArrayList<>();
        for (Item cell : CELLS.keySet()) {
            if (count(player, cell) > 0) {
                held.add(cell);
            }
        }
        held.sort((a, b) -> Integer.compare(CELLS.get(b), CELLS.get(a)));
        return held;
    }

    /** What the player's best cell kind would give, for tooltips. */
    static Map<Item, Integer> cells() {
        return CELLS;
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("arsenal", id));
    }

    private static boolean free(ServerPlayer player) {
        return player.getAbilities().instabuild;
    }

    static int charge(ItemStack weapon) {
        return weapon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(CHARGE, 0);
    }

    private static void setCharge(ItemStack weapon, int charge) {
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putInt(CHARGE, charge));
    }

    static int count(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int used = Math.min(amount, stack.getCount());
                stack.shrink(used);
                amount -= used;
            }
        }
    }

    /** Whether the player can pay for one use; tells them what is missing when not. */
    static boolean canAfford(ServerPlayer player, Spec spec, ItemStack weapon) {
        if (spec.ammo == null || free(player)) {
            return true;
        }
        Params ammo = new Params(spec.ammo);
        if (ammo.has("charge")) {
            int need = ammo.integer("charge", 1);
            if (charge(weapon) >= need) {
                return true;
            }
            if (!heldCells(player).isEmpty()) {
                return true;
            }
            player.sendSystemMessage(Component.literal("No charge left. It needs a power cell."), true);
            return false;
        }
        Item item = item(ammo.str("item", ""));
        int per = ammo.integer("per", 1);
        if (count(player, item) >= per) {
            return true;
        }
        player.sendSystemMessage(Component.translatable(item.getDescriptionId()).append(Component.literal(" needed.")), true);
        return false;
    }

    /** Pays for one use. Call only after {@link #canAfford} and a successful use. */
    static void spend(ServerPlayer player, Spec spec, ItemStack weapon) {
        if (spec.ammo == null || free(player)) {
            return;
        }
        Params ammo = new Params(spec.ammo);
        if (ammo.has("charge")) {
            int need = ammo.integer("charge", 1);
            int charge = charge(weapon);
            if (charge < need) {
                List<Item> cells = heldCells(player);
                Item used = cells.getFirst();
                take(player, used, 1);
                charge += CELLS.get(used);
            }
            setCharge(weapon, charge - need);
            return;
        }
        take(player, item(ammo.str("item", "")), ammo.integer("per", 1));
    }
}
