package com.silver.factionlock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mpds.mpds.sql;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the two factions out of each other's equipment on Magitech. A player's faction is the story they finished
 * (recorded in MPDS). Tech machines and tools, and anything tagged with a faction, do nothing in the wrong hands;
 * Bending is handled by Element Choice, which gives elements only to the Magic faction. Equipment can still be broken,
 * carried and stolen, so raiding the other side stays worthwhile.
 */
public final class FactionLockMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("faction-lock");
    private static final Map<UUID, Faction> FACTIONS = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "faction-lock-io");
        thread.setDaemon(true);
        return thread;
    });

    /** The faction of a player, NONE until MPDS has answered. */
    public static Faction factionOf(UUID player) {
        return FACTIONS.getOrDefault(player, Faction.NONE);
    }

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> load(connection.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> FACTIONS.remove(connection.getPlayer().getUUID()));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            return denied(player, ownerOfItem(stack)) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockState state = world.getBlockState(hit.getBlockPos());
            boolean machine = denied(player, ownerOfBlock(state));
            boolean tool = denied(player, ownerOfItem(player.getItemInHand(hand)));
            return machine || tool ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
            denied(player, ownerOfItem(player.getItemInHand(hand))) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
            !denied(player, ownerOfItem(player.getMainHandItem())));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("faction").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player != null) {
                    Faction faction = factionOf(player.getUUID());
                    player.sendSystemMessage(Component.literal(faction == Faction.NONE
                        ? "You have no faction." : "You belong to the " + faction.id() + " faction.").withStyle(ChatFormatting.AQUA));
                }
                return 1;
            })));
    }

    private void load(ServerPlayer player) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        var server = player.level().getServer();
        io.execute(() -> {
            try {
                var done = sql.getCompletions(id.toString());
                Faction faction = Faction.of(done.magic(), done.tech());
                LOGGER.info("{} belongs to faction {}", name, faction.id());
                // Applied on the server thread, and only while the player is still online, so a reply that
                // arrives after DISCONNECT can't leave a stale entry behind.
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(id) == null) {
                        return;
                    }
                    FACTIONS.put(id, faction);
                    Teams.assign(server, player, faction);
                });
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not read the faction of {}; they stay without one", name, failure);
            }
        });
    }

    private static Faction ownerOfItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        String tagged = custom == null ? null : custom.copyTag().getStringOr("faction", null);
        return Rules.owner(namespace, tagged);
    }

    private static Faction ownerOfBlock(BlockState state) {
        return Rules.owner(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace(), null);
    }

    /** True (and tells the player) when this player may not use equipment of that owner. Creative players are staff and may. */
    private static boolean denied(Player player, Faction owner) {
        if (owner == null || player.isCreative() || !(player instanceof ServerPlayer who)) {
            return false;
        }
        Faction mine = factionOf(who.getUUID());
        if (!Rules.blocked(mine, owner)) {
            return false;
        }
        who.sendSystemMessage(Component.literal("That is " + owner.id() + " equipment. "
            + (mine == Faction.NONE ? "You have no faction yet." : "The " + mine.id() + " faction cannot use it.")).withStyle(ChatFormatting.RED), true);
        return true;
    }
}
