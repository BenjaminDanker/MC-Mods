package com.silver.arcade.realm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;

/**
 * One group's climb. Each round, the group is split at random (everyone alone, in pairs, or all together) and every
 * party gets a room of the current tier. Who clears goes to the choice platform: the gold side cashes out (the prize
 * for the highest tier cleared), the emerald side goes on to the next tier. Who fails loses the run.
 */
public final class Run {
    private enum Phase { ROOMS, CHOICE, DONE }

    /** What the realm needs from a run when players leave it. */
    public interface Host {
        /** The player cashed out at {@code prizeTier} (record the prize, send them back to the hall). */
        void cashOut(ServerPlayer player, int prizeTier);

        /** The player failed: the run is lost (send them back to the hall). */
        void lost(ServerPlayer player, int tier);

        /** The player disconnected; keep their run for a while. */
        void suspended(UUID player, int tier, int prizeTier);
    }

    private static final int CHOICE_TICKS = 20 * 20;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Host host;
    private final Random random = new Random();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, String> lastRoom = new HashMap<>();
    private int tier;
    private int prizeTier;
    private Phase phase = Phase.ROOMS;

    private final List<Party> parties = new ArrayList<>();
    private Arena choiceArena;
    private long phaseStart;

    private static final class Party {
        final Room room;
        final Arena arena;
        final List<UUID> members;
        final long limit;
        final ServerBossEvent bar;
        boolean over;
        Set<UUID> cleared = Set.of();

        Party(Room room, Arena arena, List<UUID> members, long limit) {
            this.room = room;
            this.arena = arena;
            this.members = members;
            this.limit = limit;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(room.title()), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    /** {@code choiceFirst}: a resumed run starts on the choice platform (cash out what was earned, or climb on). */
    public Run(MinecraftServer server, ServerLevel level, Host host, List<ServerPlayer> group, int startTier, int prizeTier, boolean choiceFirst) {
        this.server = server;
        this.level = level;
        this.host = host;
        this.tier = Math.max(1, startTier);
        this.prizeTier = prizeTier;
        group.forEach(p -> players.add(p.getUUID()));
        if (choiceFirst) {
            openChoice(new HashSet<>(players));
        } else {
            startRound();
        }
    }

    public boolean has(UUID player) {
        return players.contains(player);
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    public int tier() {
        return tier;
    }

    /** Splits the players into parties for this round and starts a room for each. */
    private void startRound() {
        phase = Phase.ROOMS;
        phaseStart = server.getTickCount();
        List<UUID> order = new ArrayList<>(players);
        Collections.shuffle(order, random);
        for (List<UUID> party : split(order)) {
            RoomType type = Rooms.pick(party.size(), tier, lastRoom.get(party.get(0)), random);
            if (type == null && party.size() > 1) {
                // Nothing fits this party size: everyone in it plays alone.
                for (UUID solo : party) {
                    launch(List.of(solo), Rooms.pick(1, tier, lastRoom.get(solo), random));
                }
                continue;
            }
            launch(party, type);
        }
    }

    /** Random grouping: alone (most often), in pairs, or all together. Groups of 3 split 2+1, 4 split 2+2. */
    List<List<UUID>> split(List<UUID> order) {
        List<List<UUID>> out = new ArrayList<>();
        int n = order.size();
        double roll = random.nextDouble();
        if (n == 1 || roll < 0.4) {
            order.forEach(id -> out.add(List.of(id)));
        } else if (roll < 0.75 || n == 2 && roll < 0.85) {
            for (int i = 0; i < n; i += 2) {
                out.add(order.subList(i, Math.min(n, i + 2)));
            }
        } else {
            out.add(List.copyOf(order));
        }
        return out;
    }

    private void launch(List<UUID> members, RoomType type) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        if (online.isEmpty()) {
            return;
        }
        if (type == null) {
            for (ServerPlayer player : online) {
                player.sendSystemMessage(Component.literal("(No room fits; the Gamemaster waves you through.)"));
            }
            Party party = new Party(new Room.Pass(), Arena.take(level), members, 1);
            party.over = true;
            party.cleared = new HashSet<>(members);
            parties.add(party);
            return;
        }
        Room room = type.factory().get();
        Arena arena = Arena.take(level);
        Party party = new Party(room, arena, members, room.timeLimitTicks(tier));
        parties.add(party);
        room.start(arena, online, tier);
        for (ServerPlayer player : online) {
            lastRoom.put(player.getUUID(), type.id());
            party.bar.addPlayer(player);
            Gamemaster.introduce(player, room, tier);
        }
    }

    public void tick() {
        long now = server.getTickCount();
        if (phase == Phase.ROOMS) {
            boolean allOver = true;
            for (Party party : parties) {
                if (party.over) {
                    continue;
                }
                long elapsed = now - phaseStart;
                party.room.tick(elapsed);
                party.bar.setProgress(Math.max(0F, 1F - (float) elapsed / Math.max(1, party.limit)));
                for (UUID id : party.members) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null && player.getY() < Arena.BASE_Y - 40) {
                        party.room.fell(player);
                    }
                }
                if (party.room.finished() || elapsed >= party.limit) {
                    party.over = true;
                    party.cleared = party.room.cleared();
                    party.bar.removeAllPlayers();
                } else {
                    allOver = false;
                }
            }
            if (allOver) {
                endRound();
            }
        } else if (phase == Phase.CHOICE) {
            choiceTick(now);
        }
    }

    private void endRound() {
        Set<UUID> survivors = new HashSet<>();
        for (Party party : parties) {
            for (UUID id : party.members) {
                if (!players.contains(id)) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (party.cleared.contains(id)) {
                    survivors.add(id);
                } else if (player != null) {
                    players.remove(id);
                    host.lost(player, tier);
                }
            }
            party.arena.release();
        }
        parties.clear();
        if (survivors.isEmpty()) {
            phase = Phase.DONE;
            return;
        }
        prizeTier = tier;
        tier++;
        openChoice(survivors);
    }

    private void openChoice(Set<UUID> survivors) {
        phase = Phase.CHOICE;
        phaseStart = server.getTickCount();
        choiceArena = Arena.take(level);
        Arena a = choiceArena;
        a.fill(-6, 0, -3, 6, 0, 3, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        a.fill(-6, 0, -3, -3, 0, 3, Blocks.GOLD_BLOCK.defaultBlockState());
        a.fill(3, 0, -3, 6, 0, 3, Blocks.EMERALD_BLOCK.defaultBlockState());
        a.fill(-6, 1, -4, 6, 1, -4, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
        a.fill(-6, 1, 4, 6, 1, 4, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
        a.set(-7, 1, 0, Blocks.LANTERN.defaultBlockState());
        a.set(7, 1, 0, Blocks.LANTERN.defaultBlockState());
        for (UUID id : survivors) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            a.put(player, 0.5, 1, 0.5, 180F);
            Gamemaster.title(player, "Tier " + prizeTier + " cleared", "Gold: take your prize  |  Emerald: tier " + tier);
            Gamemaster.say(player, "Well played. Stand on the gold to leave with your prize, or on the emerald to climb to tier "
                + tier + " and risk it all.");
        }
    }

    private void choiceTick(long now) {
        long elapsed = now - phaseStart;
        List<UUID> onward = new ArrayList<>();
        for (UUID id : new ArrayList<>(players)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            var below = choiceArena.level().getBlockState(player.blockPosition().below());
            boolean gold = below.is(Blocks.GOLD_BLOCK);
            boolean emerald = below.is(Blocks.EMERALD_BLOCK);
            if (elapsed % 20 == 0) {
                Gamemaster.bar(player, "Choose: gold to cash out, emerald to climb (" + Math.max(0, (CHOICE_TICKS - elapsed) / 20) + "s)");
            }
            if (gold || elapsed >= CHOICE_TICKS && !emerald) {
                players.remove(id);
                host.cashOut(player, prizeTier);
            } else if (emerald && elapsed >= 60) {
                onward.add(id);
            }
        }
        boolean everyoneDecided = !players.isEmpty() && onward.size() == players.size();
        if (players.isEmpty() || everyoneDecided || elapsed >= CHOICE_TICKS) {
            choiceArena.release();
            choiceArena = null;
            if (players.isEmpty()) {
                phase = Phase.DONE;
            } else {
                startRound();
            }
        }
    }

    public boolean chat(ServerPlayer player, String message) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID())) {
                if (message.trim().equalsIgnoreCase("!explain")) {
                    Gamemaster.explainAgain(player, party.room);
                    return true;
                }
                return party.room.chat(player, message);
            }
        }
        return false;
    }

    public boolean use(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID())) {
                return party.room.use(player, pos);
            }
        }
        return false;
    }

    public boolean hit(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID())) {
                return party.room.hit(player, pos);
            }
        }
        return false;
    }

    /** A player disconnected: their room counts as failed for them, but the run is kept for them for a while. */
    public void left(UUID id) {
        if (!players.remove(id)) {
            return;
        }
        for (Party party : parties) {
            if (party.members.contains(id) && !party.over) {
                party.room.left(id);
            }
        }
        host.suspended(id, tier, prizeTier);
        if (players.isEmpty()) {
            for (Party party : parties) {
                party.bar.removeAllPlayers();
                party.arena.release();
            }
            parties.clear();
            if (choiceArena != null) {
                choiceArena.release();
                choiceArena = null;
            }
            phase = Phase.DONE;
        }
    }

    /** The server is stopping: everyone keeps their run as suspended. */
    public void abandon() {
        for (UUID id : new ArrayList<>(players)) {
            host.suspended(id, tier, prizeTier);
        }
        players.clear();
        for (Party party : parties) {
            party.bar.removeAllPlayers();
            party.arena.release();
        }
        parties.clear();
        if (choiceArena != null) {
            choiceArena.release();
        }
        phase = Phase.DONE;
    }
}
