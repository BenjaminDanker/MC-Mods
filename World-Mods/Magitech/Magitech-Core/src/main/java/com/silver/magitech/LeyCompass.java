package com.silver.magitech;

import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A compass with a Ley Crystal in it (crafted, custom data {@code magitech: "ley_compass"}). Using it turns the needle
 * toward the nearest ley well and says how far, so a lone player can find one without a map.
 */
final class LeyCompass {
    private static final Identifier WELL = Identifier.fromNamespaceAndPath("magitech", "ley_well");
    /** How far a search reaches, in chunks (wells are about 18 chunks apart). */
    private static final int SEARCH_CHUNKS = 64;
    private static final int COOLDOWN_TICKS = 100;

    private LeyCompass() {
    }

    static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer user) || !(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            ItemStack held = user.getItemInHand(hand);
            if (!isLeyCompass(held)) {
                return InteractionResult.PASS;
            }
            if (user.getCooldowns().isOnCooldown(held)) {
                return InteractionResult.FAIL;
            }
            user.getCooldowns().addCooldown(held, COOLDOWN_TICKS);
            point(user, level, held);
            return InteractionResult.SUCCESS;
        });
    }

    static boolean isLeyCompass(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && "ley_compass".equals(data.copyTag().getStringOr("magitech", ""));
    }

    private static void point(ServerPlayer player, ServerLevel level, ItemStack compass) {
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.literal("The needle spins. The ley does not run here.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Optional<Holder.Reference<Structure>> well = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
            .get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, WELL));
        if (well.isEmpty()) {
            return;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(well.get()), player.blockPosition(), SEARCH_CHUNKS, false);
        if (found == null) {
            player.sendSystemMessage(Component.literal("The needle trembles but settles nowhere.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        BlockPos at = found.getFirst();
        compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), at)), false));
        double dx = at.getX() + 0.5 - player.getX();
        double dz = at.getZ() + 0.5 - player.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        player.sendSystemMessage(Component.literal("The needle settles: a ley well about " + distance + " blocks " + compass(dx, dz) + ".")
            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The eight-point direction of (dx, dz); north is -z, east is +x. */
    static String compass(double dx, double dz) {
        if (dx * dx + dz * dz < 1.0E-6) {
            return "right here";
        }
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8);
        return "away, " + names[sector];
    }
}
