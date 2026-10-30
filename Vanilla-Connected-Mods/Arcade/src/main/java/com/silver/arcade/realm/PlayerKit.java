package com.silver.arcade.realm;

import java.util.List;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * What every player gets in a room, and loses back in the hall: a longer reach (boards and walls stand a few blocks
 * away; vanilla reach is 4.5) and no hunger (the arcade has no food). Ending a room early is the /giveup command.
 */
public final class PlayerKit {
    private static final Identifier REACH = Identifier.fromNamespaceAndPath("arcade", "room_reach");
    /** Extra blocks of reach in rooms (4.5 + 5.5 = 10). */
    private static final double EXTRA_REACH = 5.5;

    private PlayerKit() {
    }

    public static void enterRoom(ServerPlayer player, Room room) {
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null) {
            reach.addOrUpdateTransientModifier(new AttributeModifier(REACH, EXTRA_REACH, AttributeModifier.Operation.ADD_VALUE));
        }
        if (!room.hunger()) {
            fed(player);
        }
    }

    /** Keeps the food bar full (refreshed while in a run and in the hall). */
    public static void fed(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 60 * 60, 0, false, false));
    }

    public static void leaveRoom(ServerPlayer player) {
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(REACH);
        }
    }

    /** A tool adventure-mode players can actually dig with (the client only sends digging for blocks it may break). */
    public static ItemStack digger(ServerPlayer player, ItemStack tool, Block... breaks) {
        var blocks = player.level().registryAccess().lookupOrThrow(Registries.BLOCK);
        tool.set(DataComponents.CAN_BREAK, new AdventureModePredicate(List.of(BlockPredicate.Builder.block().of(blocks, breaks).build())));
        return tool;
    }
}
