package com.silver.magitech;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** What counts toward the weekly {@link Standing}, read from kills and from loot as it is rolled. */
public final class CityEvents {
    /** Containers of the ruins: these packs' structures, and vanilla's structure chests. */
    private static final Set<String> RUIN_NAMESPACES = Set.of("incendium", "structory_towers", "nullscape", "nova_structures");
    /** When Tech is favoured, a drilled Ancient Debris gives one more Raw Deep Ore this often. */
    static final float FAVOURED_ORE_CHANCE = 0.25F;

    private CityEvents() {
    }

    static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Standing standing = Standing.get();
            if (standing == null || !(source.getEntity() instanceof ServerPlayer killer) || entity == killer) {
                return;
            }
            if (entity instanceof ServerPlayer victim) {
                if (Standing.isRival(killer.getUUID(), victim.getUUID())) {
                    standing.award(killer, StandingRules.RIVAL_KILL, "rival");
                    Grants.grant(killer, "magitech:old_city/feud");
                }
            } else if (entity instanceof Enemy) {
                standing.award(killer, StandingRules.HOSTILE_KILL, "kill");
            }
        });
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            Standing standing = Standing.get();
            if (standing == null) {
                return;
            }
            String id = table.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            if (id.equals("minecraft:blocks/ancient_debris")) {
                onDebris(standing, context, drops);
            }
        });
    }

    private static void onDebris(Standing standing, LootContext context, List<ItemStack> drops) {
        int ore = 0;
        ItemStack stack = null;
        for (ItemStack drop : drops) {
            if (drop.is(MagitechItems.RAW_DEEP_ORE)) {
                ore += drop.getCount();
                stack = drop;
            }
        }
        if (stack == null) {
            return;
        }
        if ("tech".equals(standing.favoured()) && context.getRandom().nextFloat() < FAVOURED_ORE_CHANCE) {
            stack.grow(1);
            ore++;
        }
        if (context.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer miner) {
            standing.award(miner, StandingRules.DEEP_ORE * ore, "ore");
        }
    }

    /** A player opened a container whose loot was generated just now (see LootTableTriggerMixin): a ruin's chest counts. */
    public static void onContainerLoot(ServerPlayer player, String table) {
        Standing standing = Standing.get();
        if (standing == null || !isRuinChest(table)) {
            return;
        }
        standing.award(player, StandingRules.CHEST, "chest");
    }

    static boolean isRuinChest(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return RUIN_NAMESPACES.contains(namespace) || "minecraft".equals(namespace) && path.startsWith("chests/");
    }
}
