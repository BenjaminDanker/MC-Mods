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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("arcade");
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
    /** How long a disconnected player's room waits for them (paused) before the others move on. */
    static final int GRACE_TICKS = 90 * 20;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Host host;
    private final Random random = new Random();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, String> lastRoom = new HashMap<>();
    /** Disconnected players inside their grace period (tick they left). Their room is paused meanwhile. */
    private final Map<UUID, Long> away = new HashMap<>();
    /** Players who were dropped after the grace period; they may rejoin the climb while it lasts. */
    private final Map<UUID, Long> dropped = new HashMap<>();
    /** How long a dropped player may rejoin the climb (the same window as resuming a suspended run). */
    static final long REJOIN_TICKS = 15 * 60 * 20L;
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
        long startedAt;
        final ServerBossEvent bar;
        boolean over;
        /** Someone meets this room for the first time: a longer countdown, to read the rules. */
        boolean firstTime;
        /** Ticks of countdown left once the room is ready (-1: not started yet). */
        int countdown = -1;
        /** Cancelled because a teammate left: its players moved on to fresh rooms; no outcome here. */
        boolean voided;
        Set<UUID> cleared = Set.of();

        Party(Room room, Arena arena, List<UUID> members, long limit, long startedAt, int tier) {
            this.room = room;
            this.arena = arena;
            this.members = members;
            this.limit = limit;
            this.startedAt = startedAt;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(room.title() + "  \u00b7  Tier " + tier), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    /** A Grand Game: everyone always plays the same room. */
    private final boolean together;

    /** {@code choiceFirst}: a resumed run starts on the choice platform (cash out what was earned, or climb on). */
    public Run(MinecraftServer server, ServerLevel level, Host host, List<ServerPlayer> group, int startTier, int prizeTier, boolean choiceFirst) {
        this(server, level, host, group, startTier, prizeTier, choiceFirst, false);
    }

    public Run(MinecraftServer server, ServerLevel level, Host host, List<ServerPlayer> group, int startTier, int prizeTier, boolean choiceFirst,
            boolean together) {
        this.together = together;
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

    public boolean isAway(UUID player) {
        return away.containsKey(player);
    }

    /** True when this player was dropped from this climb after their grace period and the climb still goes on. */
    public boolean canRejoin(UUID player) {
        Long at = dropped.get(player);
        return at != null && server.getTickCount() - at <= REJOIN_TICKS && phase != Phase.DONE && !players.isEmpty();
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
        if (Rooms.forcedTier() > 0) {
            tier = Rooms.forcedTier();
        }
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
        if (together) {
            out.add(List.copyOf(order));
        } else if (n == 1 || roll < 0.4) {
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
            Arena passArena = Arena.take(level);
            passArena.label("pass");
            passArena.commit();
            Party party = new Party(new Room.Pass(), passArena, members, 1, server.getTickCount(), tier);
            party.over = true;
            party.cleared = new HashSet<>(members);
            parties.add(party);
            return;
        }
        Room room = type.factory().get();
        // The room plays at its own level for this climb tier (its curve in RoomCatalog).
        int roomLevel = Math.max(1, Difficulty.level(type, tier));
        Arena arena = Arena.take(level);
        LOGGER.info("Arcade room {} (tier {}, level {}) for {}", type.id(), tier, roomLevel, online.stream().map(p -> p.getName().getString()).toList());
        Party party = new Party(room, arena, members, room.timeLimitTicks(roomLevel), server.getTickCount(), tier);
        parties.add(party);
        arena.label(type.id() + " t" + tier);
        room.start(arena, online, roomLevel);
        arena.commit();
        for (ServerPlayer player : online) {
            lastRoom.put(player.getUUID(), type.id());
            NameTags.set(player, room.hideNameTags());
            PlayerKit.enterRoom(player, room);
            ViewDistance.set(player, room.viewDistance());
            party.bar.addPlayer(player);
            if (Gamemaster.introduce(player, room, tier)) {
                party.firstTime = true;
            }
        }
    }

    public void tick() {
        long now = server.getTickCount();
        if (now % 100 == 0) {
            parties.forEach(party -> party.arena.flushLedger());
            if (choiceArena != null) {
                choiceArena.flushLedger();
            }
        }
        // Safety net: anyone who vanished without a disconnect event is treated as disconnected.
        for (UUID id : new ArrayList<>(players)) {
            if (!away.containsKey(id) && server.getPlayerList().getPlayer(id) == null) {
                left(id);
            }
        }
        for (UUID id : new ArrayList<>(away.keySet())) {
            if (now - away.get(id) >= GRACE_TICKS) {
                away.remove(id);
                drop(id);
            }
        }
        if (phase == Phase.DONE) {
            return;
        }
        if (phase == Phase.ROOMS) {
            boolean allOver = true;
            for (Party party : parties) {
                if (!party.voided) {
                    keepSpectatorsIn(party);
                }
                if (party.over) {
                    continue;
                }
                if (!party.arena.decorating() && party.countdown != 0) {
                    // A moment before the room starts: long enough to read the rules the first time.
                    if (party.countdown < 0) {
                        party.countdown = party.firstTime ? 8 * 20 : 3 * 20;
                    }
                    party.countdown--;
                    party.startedAt++;
                    allOver = false;
                    if (party.countdown % 20 == 0 || party.countdown == (party.firstTime ? 8 * 20 : 3 * 20) - 1) {
                        int seconds = (party.countdown + 19) / 20;
                        for (UUID id : party.members) {
                            ServerPlayer player = server.getPlayerList().getPlayer(id);
                            if (player != null) {
                                Gamemaster.bar(player, seconds > 0 ? "Starting in " + seconds + "..." : "Go!");
                            }
                        }
                    }
                    continue;
                }
                if (party.arena.decorating()) {
                    // The stage is still being set (decoration goes up over a second or two): the clock waits.
                    party.startedAt++;
                    allOver = false;
                    if (now % 20 == 0) {
                        for (UUID id : party.members) {
                            ServerPlayer player = server.getPlayerList().getPlayer(id);
                            if (player != null) {
                                Gamemaster.bar(player, "The Gamemaster is setting the stage...");
                            }
                        }
                    }
                    continue;
                }
                UUID missing = party.members.stream().filter(away::containsKey).findFirst().orElse(null);
                if (missing != null) {
                    // Paused: the clock waits with the room for the missing player.
                    party.startedAt++;
                    allOver = false;
                    if (now % 20 == 0) {
                        long left = Math.max(0, (GRACE_TICKS - (now - away.get(missing))) / 20);
                        for (UUID id : party.members) {
                            ServerPlayer player = server.getPlayerList().getPlayer(id);
                            if (player != null) {
                                Gamemaster.bar(player, "Paused: waiting for your partner to reconnect (" + left + "s)");
                            }
                        }
                    }
                    continue;
                }
                long elapsed = now - party.startedAt;
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
            if (party.voided) {
                party.arena.release();
                continue;
            }
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
        a.label("choice");
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
            int cleared = prizeTier;
            int next = tier;
            a.whenReady(() -> {
                ServerPlayer there = server.getPlayerList().getPlayer(id);
                if (there != null) {
                    Gamemaster.title(there, "Tier " + cleared + " cleared", "Gold: take your prize  |  Emerald: tier " + next);
                    Gamemaster.say(there, "Well played. Stand on the gold to leave with your prize, or on the emerald to climb to tier "
                        + next + " and risk it all.");
                }
            });
        }
        a.commit();
    }

    private void choiceTick(long now) {
        if (!choiceArena.ready()) {
            phaseStart = now; // the platform is still being made; the choice (and its clock) starts when the players stand on it
            return;
        }
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
                return party.room.chat(player, message);
            }
        }
        return false;
    }

    /** The player would have died in a room: the room is lost for them and they watch the rest as a spectator. */
    public void died(ServerPlayer player) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID())) {
                party.room.died(player);
                player.setHealth(player.getMaxHealth());
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                return;
            }
        }
        player.setHealth(player.getMaxHealth());
    }

    /** True when both players are in the same running room and that room allows fighting each other. */
    public boolean pvpAllowed(UUID attacker, UUID victim) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(attacker) && party.members.contains(victim)) {
                if (!party.room.pvp()) {
                    return false;
                }
                ServerPlayer a = server.getPlayerList().getPlayer(attacker);
                ServerPlayer v = server.getPlayerList().getPlayer(victim);
                return a == null || v == null || party.room.playerHit(a, v);
            }
        }
        return false;
    }

    /** /explain: the rules of the room this player is in, again. */
    public boolean explain(ServerPlayer player) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID())) {
                Gamemaster.explainAgain(player, party.room);
                return true;
            }
        }
        return false;
    }

    /** /giveup: the room ends now for this player, as a fail. */
    public void giveUp(ServerPlayer player) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(player.getUUID()) && !party.arena.decorating()) {
                party.room.giveUp(player);
                Gamemaster.bar(player, "You gave up this room.");
                return;
            }
        }
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

    /**
     * A player disconnected. Inside a room, the room pauses for up to {@link #GRACE_TICKS} so they can come back to it.
     * Elsewhere (or once the grace runs out) they are dropped from the climb: partners get rooms of their own, and the
     * climb is kept for the leaver (they rejoin it while it lasts, or resume alone within 15 minutes).
     */
    public void left(UUID id) {
        if (!players.contains(id)) {
            return;
        }
        if (phase == Phase.ROOMS && partyOf(id) != null && !together && partyOf(id).arena.ready()) {
            away.put(id, (long) server.getTickCount());
            LOGGER.info("Arcade: {} disconnected mid-room; their room waits", id);
            for (UUID other : partyOf(id).members) {
                ServerPlayer player = server.getPlayerList().getPlayer(other);
                if (player != null && !other.equals(id)) {
                    Gamemaster.say(player, "Your partner has lost their connection. The room waits a minute and a half for them.");
                }
            }
            return;
        }
        drop(id);
    }

    /** The disconnected player is back within the grace period: they pick up the room where they left it. */
    public void returned(ServerPlayer player) {
        UUID id = player.getUUID();
        if (away.remove(id) == null) {
            return;
        }
        Party party = partyOf(id);
        if (party == null) {
            return;
        }
        party.room.rejoined(player);
        LOGGER.info("Arcade: {} came back within the grace period", player.getName().getString());
        party.bar.addPlayer(player);
        Gamemaster.say(player, "There you are. The room waited for you.");
        for (UUID other : party.members) {
            ServerPlayer partner = server.getPlayerList().getPlayer(other);
            if (partner != null && !other.equals(id)) {
                Gamemaster.say(partner, player.getName().getString() + " is back. Carry on.");
            }
        }
    }

    /**
     * A dropped player came back while the climb still goes on: they rejoin at its current tier, in a fresh room of
     * their own this round (never the one they left), and play with the group again from the next round.
     */
    public void rejoinLate(ServerPlayer player) {
        UUID id = player.getUUID();
        if (dropped.remove(id) == null) {
            return;
        }
        players.add(id);
        LOGGER.info("Arcade: {} rejoined their group's climb at tier {}", player.getName().getString(), tier);
        server.getCommands().sendCommands(player);
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        Gamemaster.say(player, "Back in the game. Your group is at tier " + tier + "; you'll join them from the next round.");
        if (phase == Phase.ROOMS) {
            launch(List.of(id), Rooms.pick(1, tier, lastRoom.get(id), random));
        } else if (phase == Phase.CHOICE && choiceArena != null) {
            choiceArena.put(player, 0.5, 1, 0.5, 180F);
            int cleared = prizeTier;
            int next = tier;
            choiceArena.whenReady(() -> Gamemaster.title(player, "Tier " + cleared + " cleared", "Gold: take your prize  |  Emerald: tier " + next));
        }
    }

    private Party partyOf(UUID id) {
        for (Party party : parties) {
            if (!party.over && party.members.contains(id)) {
                return party;
            }
        }
        return null;
    }

    /** Spectators (players who failed mid-room) stay inside the room's box. */
    private void keepSpectatorsIn(Party party) {
        int[] b = party.room.bounds();
        var o = party.arena.origin();
        for (UUID id : party.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isSpectator()) {
                continue;
            }
            double x = net.minecraft.util.Mth.clamp(player.getX(), o.getX() + b[0], o.getX() + b[3]);
            double y = net.minecraft.util.Mth.clamp(player.getY(), o.getY() + b[1], o.getY() + b[4]);
            double z = net.minecraft.util.Mth.clamp(player.getZ(), o.getZ() + b[2], o.getZ() + b[5]);
            if (x != player.getX() || y != player.getY() || z != player.getZ()) {
                player.teleportTo(party.arena.level(), x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
            }
        }
    }

    /** Out of the climb for now: their room counts as failed, partners get fresh rooms, the climb is kept for them. */
    private void drop(UUID id) {
        if (!players.remove(id)) {
            return;
        }
        dropped.put(id, (long) server.getTickCount());
        LOGGER.info("Arcade: {} dropped from the climb (tier {})", id, tier);
        List<UUID> rehome = new ArrayList<>();
        for (Party party : parties) {
            if (party.members.contains(id) && !party.over) {
                party.room.left(id);
                // A Grand Game goes on without them; a small shared room can't go on short-handed.
                if (party.members.size() > 1 && !together) {
                    // A shared room can't go on short-handed: the others get a fresh room of their own, same tier.
                    party.over = true;
                    party.voided = true;
                    party.bar.removeAllPlayers();
                    for (UUID other : party.members) {
                        if (!other.equals(id) && players.contains(other)) {
                            rehome.add(other);
                        }
                    }
                }
            }
        }
        for (UUID other : rehome) {
            ServerPlayer player = server.getPlayerList().getPlayer(other);
            if (player != null) {
                Gamemaster.say(player, "Your partner hasn't come back. No matter: a room of your own, same tier.");
                launch(List.of(other), Rooms.pick(1, tier, lastRoom.get(other), random));
            }
        }
        // A room still being prepared for people who are gone is thrown away at once.
        for (Party party : parties) {
            if (!party.over && !party.arena.ready() && party.members.stream().noneMatch(players::contains)) {
                party.over = true;
                party.voided = true;
                party.bar.removeAllPlayers();
            }
            if (party.voided && !party.arena.ready()) {
                party.arena.release();
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
