package com.silver.magitech;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A first arrival is told where they are, in their side's words. A finisher's Seal (it travels with them) is turned in
 * at their own dock for the supplies their side left behind: tech arrives with no factory, so its crate is machines;
 * a mage arrives with every ability, so theirs is a few Ley Crystals.
 */
final class Arrival {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final String ARRIVED_TAG = "magitech_arrived";
    /** How close to your dock (horizontally) a Seal can be turned in. */
    private static final double SEAL_RADIUS = 16;
    /** Ticks after joining before the welcome, so Faction-Lock has read the faction. */
    private static final int WELCOME_DELAY = 60;

    private record Supply(String item, int count) {
    }

    private static final Map<String, List<Supply>> KITS = Map.of(
        "tech", List.of(
            new Supply("polyfactory:steam_engine", 1), new Supply("polyfactory:press", 1), new Supply("polyfactory:grinder", 1),
            new Supply("polyfactory:miner", 1), new Supply("polyfactory:iron_drill_head", 2), new Supply("polyfactory:portable_drill", 1),
            new Supply("polyfactory:axle", 16), new Supply("polyfactory:conveyor", 16), new Supply("polyfactory:funnel", 4),
            new Supply("polyfactory:steel_ingot", 16), new Supply("polyfactory:steel_plate", 8), new Supply("polyfactory:steel_gear", 8),
            new Supply("minecraft:coal", 32)),
        "magic", List.of(new Supply("magitech:ley_crystal", 6)));

    private final MinecraftServer server;
    private final Landings landings;
    private final Map<UUID, Integer> waiting = new HashMap<>();

    private Arrival(MinecraftServer server, Landings landings) {
        this.server = server;
        this.landings = landings;
    }

    static void register(MinecraftServer server, Landings landings) {
        if (!Factions.available()) {
            return;
        }
        Arrival arrival = new Arrival(server, landings);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) ->
            arrival.waiting.put(handler.getPlayer().getUUID(), server.getTickCount() + WELCOME_DELAY));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> arrival.waiting.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(ticking -> arrival.tick());
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer user)) {
                return InteractionResult.PASS;
            }
            ItemStack held = user.getItemInHand(hand);
            String sealOf = sealFaction(held);
            return sealOf == null ? InteractionResult.PASS : arrival.turnIn(user, held, sealOf);
        });
    }

    private void tick() {
        if (waiting.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        waiting.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Standing standing = Standing.get();
                if (standing != null) {
                    standing.onJoin(player);
                }
                if (!player.entityTags().contains(ARRIVED_TAG)) {
                    String faction = Factions.of(player.getUUID());
                    if (faction != null) {
                        welcome(player, faction);
                        player.addTag(ARRIVED_TAG);
                    }
                }
            }
            return true;
        });
    }

    private static void welcome(ServerPlayer player, String faction) {
        boolean magic = "magic".equals(faction);
        say(player, "THE OLD CITY", ChatFormatting.GOLD);
        say(player, "Mages and engineers once worked side by side here. The city broke; the Spire fell silent and the Foundry went cold, "
            + "and each side still blames the other.", ChatFormatting.GRAY);
        if (magic) {
            say(player, "The ley wells still remember the currents: call your element inside one and it gives up Ley Crystals. "
                + "A Ley Compass (a compass and a crystal) points to the nearest well.", ChatFormatting.GRAY);
        } else {
            say(player, "Deepsteel lies under the ruins, in ancient debris that only a drill can bring up. "
                + "The mages' wells give Ley Crystals your energy weapons can use, if you can get them.", ChatFormatting.GRAY);
        }
        say(player, "Use your Seal at your dock: your side left supplies behind. Dying without a bed brings you back to the dock.", ChatFormatting.GRAY);
        say(player, "Everything you do for your side counts toward this week's standing, alone or together. "
            + "Your advancements (L) have a new tab: The Old City.", ChatFormatting.GRAY);
    }

    private static void say(ServerPlayer player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }

    /** "magic" or "tech" for a story's Seal, else null. */
    static String sealFaction(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyTag().getStringOr("id", "");
        return "completed_magic".equals(id) ? "magic" : "completed_tech".equals(id) ? "tech" : null;
    }

    private InteractionResult turnIn(ServerPlayer player, ItemStack seal, String sealOf) {
        String faction = Factions.of(player.getUUID());
        if (!sealOf.equals(faction)) {
            player.sendSystemMessage(Component.literal("This Seal belongs to the other side. It will not answer you.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        Vec3 dock = landings.arrival(faction).orElse(null);
        if (dock == null || horizontalDistanceSqr(player.position(), dock) > SEAL_RADIUS * SEAL_RADIUS) {
            player.sendSystemMessage(Component.literal("The Seal stirs. Bring it to your dock.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        seal.shrink(1);
        for (Supply supply : KITS.get(faction)) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(supply.item()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("Supply crate item {} is not on this server", supply.item());
                continue;
            }
            player.getInventory().placeItemBackInInventory(new ItemStack(item, supply.count()));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.sendSystemMessage(Component.literal("magic".equals(faction)
            ? "The Seal dissolves into light. A cache of Ley Crystals was waiting under the dock."
            : "The Seal clicks into a lock you had not noticed. A crate of machines and steel swings open.").withStyle(ChatFormatting.AQUA));
        Grants.grant(player, "magitech:old_city/supplies");
        LOGGER.info("{} turned in their {} Seal for the supply crate", player.getName().getString(), faction);
        return InteractionResult.SUCCESS;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
