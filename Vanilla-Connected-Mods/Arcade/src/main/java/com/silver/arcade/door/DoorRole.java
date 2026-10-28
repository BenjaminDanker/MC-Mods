package com.silver.arcade.door;

import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.ArcadeDb;
import com.silver.arcade.Transfers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World servers: now and then the Gamemaster's door appears near a player for a little while. It is made of display
 * entities only (no block changes anywhere). Stepping through it takes the player to the arcade; coming back, they land
 * where they left (their saved position), and any prize waiting for them in this world is handed over.
 */
public final class DoorRole {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private static final String DOOR_TAG = "arcade_door";

    private final MinecraftServer server;
    private final ArcadeConfig config;
    private final ArcadeDb db;
    private final Rewards rewards;
    private final List<Door> doors = new ArrayList<>();
    private final Map<UUID, Long> lastDoorAt = new HashMap<>();
    private final Map<UUID, Long> nextCheckAt = new HashMap<>();

    private static final class Door {
        final ServerLevel level;
        final Vec3 centre;
        final AABB inside;
        final List<Entity> parts;
        final long closesAt;

        Door(ServerLevel level, Vec3 centre, AABB inside, List<Entity> parts, long closesAt) {
            this.level = level;
            this.centre = centre;
            this.inside = inside;
            this.parts = parts;
            this.closesAt = closesAt;
        }
    }

    public DoorRole(MinecraftServer server, ArcadeConfig config, ArcadeDb db) {
        this.server = server;
        this.config = config;
        this.db = db;
        this.rewards = new Rewards(config);
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(ticking -> tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> deliverPrizes(handler.getPlayer()));
        // Doors are never saved: any door entity found when a chunk loads is a leftover.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(DOOR_TAG) && doors.stream().noneMatch(d -> d.parts.contains(entity))) {
                entity.discard();
            }
        });
        LOGGER.info("Arcade door role: about one door per {} minutes of play per player, {} s each",
            (int) (config.door.checkEveryMinutes / Math.max(0.0001, config.door.chance)), config.door.lifetimeSeconds);
    }

    private void tick() {
        long now = server.getTickCount();
        for (Iterator<Door> it = doors.iterator(); it.hasNext();) {
            Door door = it.next();
            if (now >= door.closesAt) {
                close(door, true);
                it.remove();
                continue;
            }
            if (now % 4 == 0) {
                door.level.sendParticles(ParticleTypes.PORTAL, door.centre.x, door.centre.y + 1.5, door.centre.z, 6, 0.6, 1.0, 0.6, 0.2);
            }
            for (ServerPlayer player : door.level.players()) {
                if (player.getBoundingBox().intersects(door.inside) && !player.isSpectator()) {
                    enter(player);
                    close(door, false);
                    it.remove();
                    break;
                }
            }
        }
        if (now % 20 != 0) {
            return;
        }
        long periodTicks = Math.max(1, config.door.checkEveryMinutes) * 1200L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            long due = nextCheckAt.computeIfAbsent(id, ignored -> now + periodTicks);
            if (now < due) {
                continue;
            }
            nextCheckAt.put(id, now + periodTicks);
            Long last = lastDoorAt.get(id);
            boolean cooling = last != null && now - last < config.door.cooldownMinutes * 1200L;
            if (!cooling && !player.isSpectator() && !player.isCreative() && player.getRandom().nextDouble() < config.door.chance) {
                if (open(player)) {
                    lastDoorAt.put(id, now);
                }
            }
        }
    }

    /** Opens a door near the player; false when no fitting spot was found. */
    public boolean open(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = config.door.minDistance + random.nextDouble() * Math.max(0, config.door.maxDistance - config.door.minDistance);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - player.getY()) > 6 || !clear(level, x, y, z)) {
                continue;
            }
            // Facing the player: the door's plane is perpendicular to the line between them.
            float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - (x + 0.5)), player.getZ() - (z + 0.5)));
            spawn(level, new Vec3(x + 0.5, y, z + 0.5), yaw);
            player.sendSystemMessage(Component.literal("Somewhere close, dice are rolling.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            level.playSound(null, x, y, z, SoundEvents.ENDER_EYE_DEATH, SoundSource.AMBIENT, 1.0F, 0.6F);
            LOGGER.info("Opened an arcade door near {} at {} {} {}", player.getName().getString(), x, y, z);
            return true;
        }
        return false;
    }

    private static boolean clear(ServerLevel level, int x, int y, int z) {
        if (!level.getBlockState(new BlockPos(x, y - 1, z)).isSolid() || !level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 4; dy++) {
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void spawn(ServerLevel level, Vec3 at, float yaw) {
        List<Entity> parts = new ArrayList<>();
        // A dark arch with a crying-obsidian keystone, a deep-purple veil, and a die floating above.
        String[][] blocks = {
            {"minecraft:polished_blackstone_bricks", "-1.5 0 0", "1 3.5 0.5"}, {"minecraft:polished_blackstone_bricks", "0.5 0 0", "1 3.5 0.5"},
            {"minecraft:polished_blackstone_bricks", "-1.5 3.5 0", "3 0.5 0.5"}, {"minecraft:crying_obsidian", "-0.25 3.4 -0.05", "0.5 0.7 0.6"},
            {"minecraft:purple_stained_glass", "-0.5 0 0.1", "1 3.5 0.3"}};
        for (String[] b : blocks) {
            parts.add(display(level, at, yaw, "minecraft:block_display", "block_state:{Name:\"" + b[0] + "\"}", b[1], b[2]));
        }
        parts.add(display(level, at, yaw, "minecraft:text_display",
            "text:{text:\"⚄\",color:\"light_purple\"},background:0,billboard:\"center\",brightness:{block:15,sky:15}", "0 4.6 0", "2 2 2"));
        parts.removeIf(java.util.Objects::isNull);
        double rad = Math.toRadians(yaw);
        Vec3 normal = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        AABB inside = new AABB(at.x - 0.6, at.y, at.z - 0.6, at.x + 0.6, at.y + 3, at.z + 0.6).inflate(Math.abs(normal.z) * 0.2, 0, Math.abs(normal.x) * 0.2);
        doors.add(new Door(level, at, inside, parts, server.getTickCount() + config.door.lifetimeSeconds * 20L));
    }

    /** One display entity of the door, positioned in the door's own frame (x across, z through) and turned by yaw. */
    private Entity display(ServerLevel level, Vec3 at, float yaw, String type, String extra, String offset, String scale) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully("{" + extra + ",transformation:{left_rotation:[0f,0f,0f,1f],"
                + "right_rotation:[0f,0f,0f,1f],translation:[" + offset.replace(' ', ',').replaceAll("([0-9.\\-]+)", "$1f") + "],scale:["
                + scale.replace(' ', ',').replaceAll("([0-9.\\-]+)", "$1f") + "]}}");
            EntityType<?> entityType = type.endsWith("text_display") ? EntityTypes.TEXT_DISPLAY : EntityTypes.BLOCK_DISPLAY;
            Entity entity = EntityType.loadEntityRecursive(entityType, tag, level, EntitySpawnReason.COMMAND, e -> e);
            if (entity == null) {
                return null;
            }
            entity.snapTo(at.x, at.y, at.z, yaw, 0F);
            entity.addTag(DOOR_TAG);
            level.addFreshEntity(entity);
            return entity;
        } catch (Exception failure) {
            LOGGER.error("Could not build part of the arcade door", failure);
            return null;
        }
    }

    private void close(Door door, boolean fade) {
        door.parts.forEach(Entity::discard);
        if (fade) {
            door.level.sendParticles(ParticleTypes.REVERSE_PORTAL, door.centre.x, door.centre.y + 1.5, door.centre.z, 40, 0.5, 1.2, 0.5, 0.05);
        }
    }

    private void enter(ServerPlayer player) {
        String name = player.getName().getString();
        UUID id = player.getUUID();
        db.recordEntry(id, name, config.serverName).whenComplete((ok, failure) -> server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online == null) {
                return;
            }
            if (failure != null) {
                LOGGER.error("ALERT: could not record {}'s arcade entry; not sending them", name, failure);
                online.sendSystemMessage(Component.literal("The door shudders and stays shut.").withStyle(ChatFormatting.GRAY));
                return;
            }
            online.sendSystemMessage(Component.literal("You step through. Something laughs.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            if (!Transfers.send(online, config.arcadeServer, config.hallPortal, "arcade door")) {
                LOGGER.error("ALERT: the arcade transfer for {} was not queued", name);
            }
        }));
    }

    private void deliverPrizes(ServerPlayer player) {
        UUID id = player.getUUID();
        db.takePrizes(id, config.serverName).whenComplete((prizes, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("ALERT: could not read arcade prizes for {}", player.getName().getString(), failure);
                return;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online == null || prizes.isEmpty()) {
                return; // (a prize taken for a player who left again is lost only if they left within this instant)
            }
            for (ArcadeDb.Prize prize : prizes) {
                rewards.give(online, prize.tier());
            }
        }));
    }
}
