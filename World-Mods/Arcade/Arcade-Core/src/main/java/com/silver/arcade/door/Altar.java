package com.silver.arcade.door;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gamemaster's altar near the world spawn: a small pedestal with his die turning above it. Touching it gives you a
 * die if you carry none, so anyone who put theirs away (or lost it) can come back to the arcade whenever they like.
 * Display entities only (no blocks, nothing to break or claim); rebuilt on every start.
 */
final class Altar {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    static final String TAG = "arcade_altar";
    private static final int GIVE_COOLDOWN_TICKS = 20 * 60;

    private final MinecraftServer server;
    private final BlockPos configured;
    private final List<Entity> parts = new ArrayList<>();
    private final Map<UUID, Long> gaveAt = new HashMap<>();
    private ServerLevel level;
    private Vec3 at;
    private Display die;
    private boolean searched;

    Altar(MinecraftServer server, BlockPos configured) {
        this.server = server;
        this.configured = configured;
    }

    void tick(long now) {
        if (at == null) {
            if (!searched && now % 100 == 0) {
                place();
            }
            return;
        }
        if (parts.isEmpty() || parts.get(0).isRemoved()) {
            if (now % 100 == 0 && level.isPositionEntityTicking(BlockPos.containing(at))) {
                build();
            }
            return;
        }
        if (now % 2 == 0 && die != null) {
            die.setYRot((die.getYRot() + 4F) % 360F);
        }
        if (now % 10 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.6, at.z, 2, 0.3, 0.3, 0.3, 0.2);
        }
        if (now % 5 != 0) {
            return;
        }
        AABB touch = new AABB(at.x - 0.9, at.y, at.z - 0.9, at.x + 0.9, at.y + 2.2, at.z + 0.9);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.getBoundingBox().intersects(touch)) {
                continue;
            }
            Long last = gaveAt.get(player.getUUID());
            if (last != null && now - last < GIVE_COOLDOWN_TICKS) {
                continue;
            }
            gaveAt.put(player.getUUID(), now);
            if (Die.carries(player)) {
                player.sendSystemMessage(Component.literal("You already carry his die.").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            player.getInventory().placeItemBackInInventory(Die.create());
            player.sendSystemMessage(Component.literal("The die settles into your hand, warm, as if it had been waiting. Roll it when you want to play.")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        }
    }

    /** The configured spot, or open ground a few blocks from the world spawn. */
    private void place() {
        searched = true;
        var spawn = server.getRespawnData();
        level = server.getLevel(spawn.dimension());
        if (level == null) {
            level = server.overworld();
        }
        BlockPos found = configured != null ? configured : search(level, spawn.pos());
        if (found == null) {
            LOGGER.warn("No open ground for the Gamemaster's altar near {}; set door.altar in config/arcade.json", spawn.pos());
            return;
        }
        at = Vec3.atBottomCenterOf(found);
        LOGGER.info("The Gamemaster's altar stands at {}", found);
    }

    /** Nearest spot (3 to 10 blocks out) with a sturdy floor, 3 free blocks above, and no fluid. */
    static BlockPos search(ServerLevel level, BlockPos spawn) {
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double d = dx * dx + dz * dz;
                if (d < 9 || d > 100 || d >= bestDistance) {
                    continue;
                }
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos pos = spawn.offset(dx, dy, dz);
                    if (fits(level, pos)) {
                        best = pos;
                        bestDistance = d;
                        break;
                    }
                }
            }
        }
        return best;
    }

    private static boolean fits(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        for (int y = 0; y < 3; y++) {
            var state = level.getBlockState(pos.above(y));
            if (!state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void build() {
        AABB around = new AABB(at.x - 2, at.y - 1, at.z - 2, at.x + 2, at.y + 4, at.z + 2);
        level.getEntities((Entity) null, around, e -> e.entityTags().contains(TAG)).forEach(Entity::discard);
        parts.clear();
        // Pedestal: a slim purpur pillar with a chiseled cap; the die (an echo shard) turns above it.
        spawn(EntityTypes.BLOCK_DISPLAY, "{block_state:{Name:\"minecraft:purpur_pillar\"},transformation:{translation:[-0.3f,0f,-0.3f],"
            + "left_rotation:[0f,0f,0f,1f],scale:[0.6f,0.9f,0.6f],right_rotation:[0f,0f,0f,1f]}}");
        spawn(EntityTypes.BLOCK_DISPLAY, "{block_state:{Name:\"minecraft:chiseled_quartz_block\"},transformation:{translation:[-0.4f,0.9f,-0.4f],"
            + "left_rotation:[0f,0f,0f,1f],scale:[0.8f,0.15f,0.8f],right_rotation:[0f,0f,0f,1f]}}");
        Entity shard = spawn(EntityTypes.ITEM_DISPLAY, "{item:{id:\"minecraft:echo_shard\",count:1,components:{\"minecraft:enchantment_glint_override\":true}},"
            + "billboard:\"fixed\",brightness:{sky:15,block:15},teleport_duration:2,transformation:{translation:[0f,1.55f,0f],"
            + "left_rotation:[0f,0f,0f,1f],scale:[0.8f,0.8f,0.8f],right_rotation:[0f,0f,0f,1f]}}");
        die = shard instanceof Display display ? display : null;
    }

    private Entity spawn(EntityType<?> type, String nbt) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(nbt);
            Entity entity = EntityType.loadEntityRecursive(type, tag, level, EntitySpawnReason.COMMAND, e -> e);
            if (entity == null) {
                return null;
            }
            entity.addTag(TAG);
            entity.snapTo(at.x, at.y, at.z, 0F, 0F);
            level.addFreshEntity(entity);
            parts.add(entity);
            return entity;
        } catch (Exception failure) {
            LOGGER.error("ALERT: could not build the Gamemaster's altar", failure);
            return null;
        }
    }
}
