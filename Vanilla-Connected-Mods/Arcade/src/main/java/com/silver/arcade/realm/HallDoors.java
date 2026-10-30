package com.silver.arcade.realm;

import com.silver.arcade.ArcadeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hall's doors. Walking through any door takes you to the small waiting room behind it; whoever walks through the
 * same door within the next few seconds joins you, then a random climb begins. Every door does the same thing.
 *
 * <p>Grand Games are the Gamemaster's call, never a player's choice. When the arcade is busy (enough players free in
 * the hall or waiting behind doors) he calls one, at most every so often: for a short window every door leads to it.
 * If enough come through, it starts; if not, they get ordinary climbs. Enough players waiting behind doors at once are
 * also merged into a Grand Game.
 */
final class HallDoors {
    interface Starter {
        void start(List<ServerPlayer> group, boolean together);
    }

    private static final int WAIT_TICKS = 5 * 20;
    private static final int MAX_GROUP = 4;

    private final MinecraftServer server;
    private final ArcadeConfig config;
    private final Starter starter;
    /** Door index -> players waiting behind it, and when the first came through. */
    private final Map<Integer, Set<UUID>> waiting = new HashMap<>();
    private final Map<Integer, Long> firstAt = new HashMap<>();
    private final Set<UUID> grandWaiting = new LinkedHashSet<>();
    private long grandCallEnds = -1;
    private long lastGrandAt = -1;

    HallDoors(MinecraftServer server, ArcadeConfig config, Starter starter) {
        this.server = server;
        this.config = config;
        this.starter = starter;
    }

    private boolean callOpen(long now) {
        return grandCallEnds > now;
    }

    private static final String SIGN_TAG = "arcade_hall_sign";
    private final List<net.minecraft.world.entity.Entity> signs = new ArrayList<>();

    /** Gold "PLAY" signs over every door and "HOME" over the exit gate (rebuilt each start; stale copies are discarded). */
    void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.entityTags().contains(SIGN_TAG) && !signs.contains(entity)) {
                entity.discard();
            }
        });
    }

    /** Every sign has its own spot; a missing one (not yet loaded, or gone) is put up once its spot is loaded. */
    private void ensureSigns(long now) {
        if (now % 100 != 0 || config.hall.doors.isEmpty()) {
            return;
        }
        if (signSpots.isEmpty()) {
            for (ArcadeConfig.HallDoor door : config.hall.doors) {
                boolean alongX = "x".equals(door.axis);
                signSpots.add(new SignSpot(door.x + (alongX ? door.width / 2.0 : 0.5), door.y + door.height + 0.4,
                    door.z + (alongX ? 0.5 : door.width / 2.0), "\u2684 PLAY \u2684"));
            }
            signSpots.add(new SignSpot(0.5, 110.0, -7.5, "HOME"));
        }
        for (SignSpot spot : signSpots) {
            if (spot.entity != null && !spot.entity.isRemoved()) {
                continue;
            }
            if (level().isPositionEntityTicking(net.minecraft.core.BlockPos.containing(spot.x, spot.y, spot.z))) {
                signs.remove(spot.entity);
                spot.entity = sign(spot.x, spot.y, spot.z, spot.text);
            }
        }
    }

    private static final class SignSpot {
        final double x;
        final double y;
        final double z;
        final String text;
        net.minecraft.world.entity.Entity entity;

        SignSpot(double x, double y, double z, String text) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
        }
    }

    private final List<SignSpot> signSpots = new ArrayList<>();

    private net.minecraft.world.entity.Entity sign(double x, double y, double z, String text) {
        try {
            var tag = net.minecraft.nbt.TagParser.parseCompoundFully("{billboard:\"center\",shadow:1b,background:0,"
                + "transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[2.5f,2.5f,2.5f],right_rotation:[0f,0f,0f,1f]},"
                + "brightness:{sky:15,block:15},text:{text:\"" + text + "\",color:\"gold\",bold:1b}}");
            var entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, tag, level(),
                net.minecraft.world.entity.EntitySpawnReason.COMMAND, e -> e);
            if (entity != null) {
                entity.addTag(SIGN_TAG);
                entity.snapTo(x, y, z, 0F, 0F);
                signs.add(entity);
                level().addFreshEntity(entity);
                return entity;
            }
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger("arcade").error("ALERT: a hall sign could not be put up", failure);
        }
        return null;
    }

    void tick(long now, List<ServerPlayer> free) {
        ensureSigns(now);
        List<ArcadeConfig.HallDoor> doors = config.hall.doors;
        // Who walks through a door now?
        for (ServerPlayer player : free) {
            UUID id = player.getUUID();
            if (grandWaiting.contains(id) || waiting.values().stream().anyMatch(set -> set.contains(id))) {
                continue;
            }
            for (int i = 0; i < doors.size(); i++) {
                if (inside(doors.get(i), player)) {
                    enter(i, player, now);
                    break;
                }
            }
        }
        forgetGone();
        if (callOpen(now)) {
            if ((grandCallEnds - now) % 20 == 0) {
                long seconds = (grandCallEnds - now + 19) / 20;
                for (ServerPlayer player : level().players()) {
                    Gamemaster.bar(player, grandWaiting.contains(player.getUUID())
                        ? "The Grand Game gathers: " + grandWaiting.size() + " in, " + seconds + "s left"
                        : "Grand Game! Every door leads to it for " + seconds + "s");
                }
                for (ArcadeConfig.HallDoor door : doors) {
                    level().sendParticles(ParticleTypes.END_ROD, door.x + 0.5 + ("x".equals(door.axis) ? door.width / 2.0 : 0),
                        door.y + door.height / 2.0, door.z + 0.5 + ("z".equals(door.axis) ? door.width / 2.0 : 0), 12, 0.8, 1.2, 0.8, 0.02);
                }
            }
        } else if (grandCallEnds > 0) {
            grandCallEnds = -1;
            closeCall();
        }
        // Doors whose wait is over start their climbs (merged into a Grand Game when enough wait at once).
        int waitingTotal = waiting.values().stream().mapToInt(Set::size).sum();
        boolean anyDue = firstAt.values().stream().anyMatch(t -> now - t >= WAIT_TICKS);
        if (anyDue && waitingTotal >= config.event.minPlayers && cooledDown(now)) {
            List<ServerPlayer> all = new ArrayList<>();
            waiting.values().forEach(set -> all.addAll(online(set)));
            waiting.clear();
            firstAt.clear();
            all.forEach(p -> Gamemaster.title(p, "A Grand Game!", "So many of you at once. Everyone plays together."));
            startGrand(all, now);
            return;
        }
        for (var entry : new ArrayList<>(firstAt.entrySet())) {
            int door = entry.getKey();
            long left = WAIT_TICKS - (now - entry.getValue());
            List<ServerPlayer> group = online(waiting.getOrDefault(door, Set.of()));
            if (left > 0) {
                if (left % 20 == 0 || left == WAIT_TICKS - 1) {
                    group.forEach(p -> Gamemaster.bar(p, "The game begins in " + ((left + 19) / 20) + "... (" + group.size() + " here)"));
                }
                continue;
            }
            waiting.remove(door);
            firstAt.remove(door);
            for (int i = 0; i < group.size(); i += MAX_GROUP) {
                starter.start(group.subList(i, Math.min(group.size(), i + MAX_GROUP)), false);
            }
        }
        maybeCall(now, free);
    }

    private void enter(int index, ServerPlayer player, long now) {
        ArcadeConfig.HallDoor door = config.hall.doors.get(index);
        player.teleportTo(level(), door.waitX, door.waitY, door.waitZ, Set.of(), door.waitYaw, 0F, false);
        if (callOpen(now)) {
            grandWaiting.add(player.getUUID());
            Gamemaster.say(player, "To the Grand Game with you.");
            return;
        }
        waiting.computeIfAbsent(index, i -> new LinkedHashSet<>()).add(player.getUUID());
        firstAt.putIfAbsent(index, now);
    }

    /** The Gamemaster calls a Grand Game when the arcade is busy enough, no more than once per cooldown. */
    private void maybeCall(long now, List<ServerPlayer> free) {
        if (callOpen(now) || now % 100 != 0 || !cooledDown(now)) {
            return;
        }
        int around = free.size(); // everyone in the hall, those waiting behind doors included
        if (around < config.event.minPlayers) {
            return;
        }
        grandCallEnds = now + config.event.windowSeconds * 20L;
        lastGrandAt = now;
        for (ServerPlayer player : level().players()) {
            Gamemaster.title(player, "A Grand Game!", "Every door leads to it for " + config.event.windowSeconds + " seconds");
            Gamemaster.say(player, "So many of you! A Grand Game: everyone in the same rooms. Walk through any door in the next "
                + config.event.windowSeconds + " seconds to play.");
        }
        // Whoever is already waiting behind a door is in.
        waiting.values().forEach(grandWaiting::addAll);
        waiting.clear();
        firstAt.clear();
    }

    private void closeCall() {
        List<ServerPlayer> in = online(grandWaiting);
        grandWaiting.clear();
        if (in.size() >= config.event.minPlayers) {
            startGrand(in, server.getTickCount());
            return;
        }
        for (ServerPlayer player : in) {
            Gamemaster.say(player, "Not enough for a Grand Game this time. An ordinary climb, then.");
        }
        for (int i = 0; i < in.size(); i += MAX_GROUP) {
            starter.start(in.subList(i, Math.min(in.size(), i + MAX_GROUP)), false);
        }
    }

    private void startGrand(List<ServerPlayer> players, long now) {
        lastGrandAt = now;
        List<ServerPlayer> cap = players.subList(0, Math.min(players.size(), config.event.maxPlayers));
        starter.start(new ArrayList<>(cap), true);
        if (players.size() > cap.size()) {
            List<ServerPlayer> rest = players.subList(cap.size(), players.size());
            for (int i = 0; i < rest.size(); i += MAX_GROUP) {
                starter.start(new ArrayList<>(rest.subList(i, Math.min(rest.size(), i + MAX_GROUP))), false);
            }
        }
    }

    private boolean cooledDown(long now) {
        return lastGrandAt < 0 || now - lastGrandAt >= config.event.cooldownMinutes * 1200L;
    }

    private void forgetGone() {
        waiting.values().forEach(set -> set.removeIf(id -> server.getPlayerList().getPlayer(id) == null));
        waiting.entrySet().removeIf(entry -> {
            if (entry.getValue().isEmpty()) {
                firstAt.remove(entry.getKey());
                return true;
            }
            return false;
        });
        grandWaiting.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private List<ServerPlayer> online(Set<UUID> ids) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    static boolean inside(ArcadeConfig.HallDoor door, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean alongX = "x".equals(door.axis);
        double x0 = door.x;
        double x1 = door.x + (alongX ? door.width : 1);
        double z0 = door.z;
        double z1 = door.z + (alongX ? 1 : door.width);
        return x >= x0 && x < x1 && z >= z0 && z < z1 && y >= door.y - 0.5 && y < door.y + door.height;
    }

    private ServerLevel level() {
        return server.overworld();
    }
}
